package com.balance.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BalanceService {

    private static final List<String> LANES_LIST = List.of("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY");
    private static final Map<String, String> LANE_KO = Map.of(
        "TOP", "탑", "JUNGLE", "정글", "MIDDLE", "미드",
        "BOTTOM", "원딜", "UTILITY", "서포터", "UNKNOWN", "미정"
    );

    public Map<String, Object> balance(List<Map<String, Object>> players, String mode,
                                       List<List<Integer>> fixedGroups,
                                       List<List<Integer>> separateGroups,
                                       Map<Integer, String> laneLocks,
                                       Map<String, List<String>> laneHistory) {
        int n    = players.size();
        int half = n / 2;

        if (fixedGroups == null)    fixedGroups    = List.of();
        if (separateGroups == null) separateGroups = List.of();
        if (laneLocks == null)      laneLocks      = Map.of();
        if (laneHistory == null)    laneHistory    = Map.of();

        List<Map<String, Object>> t1Data = new ArrayList<>();
        List<Map<String, Object>> t2Data = new ArrayList<>();
        double s1 = 0, s2 = 0;
        boolean laneBalanced = false;

        if ("balance".equals(mode) && n == 10) {
            List<int[]> allPerms   = generatePermutations(5);
            List<List<Integer>> allCombos = generateCombinations(n, 5);

            int    maxPrimaryMatches = -1;
            double minScoreDiff     = Double.MAX_VALUE;
            List<Object[]> bestT1   = null, bestT2 = null;
            double bestS1 = 0, bestS2 = 0;

            for (List<Integer> combo : allCombos) {
                Set<Integer> comboSet = new HashSet<>(combo);
                if (!isValidSplit(comboSet, n, fixedGroups, separateGroups)) continue;

                List<Map<String, Object>> t1Players = combo.stream()
                    .map(players::get).collect(Collectors.toList());
                List<Map<String, Object>> t2Players = new ArrayList<>();
                for (int i = 0; i < n; i++)
                    if (!comboSet.contains(i)) t2Players.add(players.get(i));

                // Build per-team lane locks (remap global indices to team-local indices)
                Map<Integer, String> t1Locks = new HashMap<>();
                Map<Integer, String> t2Locks = new HashMap<>();
                for (Map.Entry<Integer, String> le : laneLocks.entrySet()) {
                    int gi = le.getKey();
                    if (comboSet.contains(gi)) {
                        t1Locks.put(combo.indexOf(gi), le.getValue());
                    } else {
                        // find local index in t2
                        int li = 0;
                        for (int i = 0; i < n; i++) {
                            if (!comboSet.contains(i)) {
                                if (i == gi) { t2Locks.put(li, le.getValue()); break; }
                                li++;
                            }
                        }
                    }
                }

                Object[] t1Assign = getBestAssignment(t1Players, allPerms, t1Locks, laneHistory);
                Object[] t2Assign = getBestAssignment(t2Players, allPerms, t2Locks, laneHistory);

                int    totalM = (int)t1Assign[1] + (int)t2Assign[1];
                double diff   = Math.abs((double)t1Assign[2] - (double)t2Assign[2]);

                if (totalM > maxPrimaryMatches || (totalM == maxPrimaryMatches && diff < minScoreDiff)) {
                    maxPrimaryMatches = totalM;
                    minScoreDiff      = diff;
                    bestT1 = (List<Object[]>) t1Assign[0];
                    bestT2 = (List<Object[]>) t2Assign[0];
                    bestS1 = (double) t1Assign[2];
                    bestS2 = (double) t2Assign[2];
                }
            }

            s1 = bestS1; s2 = bestS2;
            for (Object[] pa : bestT1) {
                Map<String, Object> p = new LinkedHashMap<>((Map<String, Object>) pa[0]);
                String lane = (String) pa[1];
                p.put("assignedLane",   lane);
                p.put("assignedLaneKo", LANE_KO.getOrDefault(lane, lane));
                t1Data.add(p);
            }
            for (Object[] pa : bestT2) {
                Map<String, Object> p = new LinkedHashMap<>((Map<String, Object>) pa[0]);
                String lane = (String) pa[1];
                p.put("assignedLane",   lane);
                p.put("assignedLaneKo", LANE_KO.getOrDefault(lane, lane));
                t2Data.add(p);
            }
            laneBalanced = true;

        } else if ("balance".equals(mode)) {

            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) order.add(i);
            order.sort((a, b) -> Double.compare(getScore(players.get(b)), getScore(players.get(a))));

            List<Integer> t1I = new ArrayList<>(), t2I = new ArrayList<>();
            Set<Integer> t1Set = new HashSet<>(), t2Set = new HashSet<>();

            // Pre-assign fixed group members together
            for (int i : order) {
                // Check if this player is in a fixed group with someone already assigned
                int forcedTeam = getForcedTeam(i, t1Set, t2Set, fixedGroups);
                // Check separation constraints
                int separateForce = getSeparateForce(i, t1Set, t2Set, separateGroups);

                int team = 0; // 0=undecided, 1=team1, 2=team2
                if (forcedTeam != 0) team = forcedTeam;
                if (separateForce != 0) {
                    if (team != 0 && team != separateForce) {
                        // Conflict — fixed says one team, separate says other. Fixed wins.
                    } else if (team == 0) {
                        team = separateForce;
                    }
                }

                if (team == 1 && t1I.size() < half) {
                    t1I.add(i); t1Set.add(i); s1 += getScore(players.get(i));
                } else if (team == 2 && t2I.size() < (n - half)) {
                    t2I.add(i); t2Set.add(i); s2 += getScore(players.get(i));
                } else if (t1I.size() < half && (t2I.size() >= (n - half) || s1 <= s2)) {
                    t1I.add(i); t1Set.add(i); s1 += getScore(players.get(i));
                } else {
                    t2I.add(i); t2Set.add(i); s2 += getScore(players.get(i));
                }
            }
            for (int i : t1I) t1Data.add(new LinkedHashMap<>(players.get(i)));
            for (int i : t2I) t2Data.add(new LinkedHashMap<>(players.get(i)));

        } else {
            // Random mode with constraints
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < n; i++) indices.add(i);

            boolean valid = false;
            for (int attempt = 0; attempt < 1000; attempt++) {
                Collections.shuffle(indices);
                Set<Integer> team1Set = new HashSet<>(indices.subList(0, half));
                if (isValidSplit(team1Set, n, fixedGroups, separateGroups)) {
                    valid = true;
                    break;
                }
            }

            for (int i : indices.subList(0, half)) t1Data.add(new LinkedHashMap<>(players.get(i)));
            for (int i : indices.subList(half, n))  t2Data.add(new LinkedHashMap<>(players.get(i)));
            s1 = t1Data.stream().mapToDouble(this::getScore).sum();
            s2 = t2Data.stream().mapToDouble(this::getScore).sum();

            if (n == 10) {
                // Remap laneLocks: global indices -> team-local indices
                Map<Integer, String> t1Locks = new HashMap<>();
                Map<Integer, String> t2Locks = new HashMap<>();
                List<Integer> t1Indices = new ArrayList<>(indices.subList(0, half));
                List<Integer> t2Indices = new ArrayList<>(indices.subList(half, n));
                for (Map.Entry<Integer, String> le : laneLocks.entrySet()) {
                    int li1 = t1Indices.indexOf(le.getKey());
                    if (li1 >= 0) t1Locks.put(li1, le.getValue());
                    int li2 = t2Indices.indexOf(le.getKey());
                    if (li2 >= 0) t2Locks.put(li2, le.getValue());
                }
                assignRandomLanes(t1Data, t1Locks, laneHistory);
                assignRandomLanes(t2Data, t2Locks, laneHistory);
            }
        }

        Map<String, Integer> laneOrder = new HashMap<>();
        for (int i = 0; i < LANES_LIST.size(); i++) laneOrder.put(LANES_LIST.get(i), i);

        Comparator<Map<String, Object>> laneComp = (a, b) -> {
            String la = (String) a.getOrDefault("assignedLane", a.getOrDefault("primaryLane", ""));
            String lb = (String) b.getOrDefault("assignedLane", b.getOrDefault("primaryLane", ""));
            return laneOrder.getOrDefault(la, 99) - laneOrder.getOrDefault(lb, 99);
        };
        t1Data.sort(laneComp);
        t2Data.sort(laneComp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("team1",       t1Data);
        result.put("team2",       t2Data);
        result.put("team1Score",  s1);
        result.put("team2Score",  s2);
        result.put("scoreDiff",   Math.abs(s1 - s2));
        result.put("laneBalanced", laneBalanced);
        result.put("mode",        mode);
        return result;
    }

    private double getScore(Map<String, Object> player) {
        Object score = player.get("score");
        return score instanceof Number ? ((Number) score).doubleValue() : 0.0;
    }

    private String getPrimaryLane(Map<String, Object> player) {
        Object v = player.get("primaryLane");
        return v instanceof String ? (String) v : "";
    }

    private String getSecondaryLane(Map<String, Object> player) {
        Object v = player.get("secondaryLane");
        return v instanceof String ? (String) v : "";
    }

    private Object[] getBestAssignment(List<Map<String, Object>> team, List<int[]> allPerms,
                                       Map<Integer, String> laneLocks,
                                       Map<String, List<String>> laneHistory) {
        int    bestM     = -1;
        double bestScore = 0;
        int    bestHistoryConflicts = Integer.MAX_VALUE;
        List<Object[]> bestAssignment = null;

        for (int[] perm : allPerms) {
            // Check lane lock constraints: locked player must be at the locked lane position
            boolean valid = true;
            for (Map.Entry<Integer, String> le : laneLocks.entrySet()) {
                int playerIdx = le.getKey();
                String requiredLane = le.getValue();
                int requiredPos = LANES_LIST.indexOf(requiredLane);
                if (requiredPos < 0) continue;
                // perm[requiredPos] should be playerIdx
                if (perm[requiredPos] != playerIdx) { valid = false; break; }
            }
            if (!valid) continue;

            int    m     = 0;
            double score = 0;
            int    historyConflicts = 0;
            for (int i = 0; i < 5; i++) {
                Map<String, Object> p  = team.get(perm[i]);
                String lane            = LANES_LIST.get(i);
                double base            = getScore(p);
                if (lane.equals(getPrimaryLane(p))) {
                    m++; score += base;
                } else if (lane.equals(getSecondaryLane(p))) {
                    score += base * 0.90;
                } else {
                    score += base * 0.80;
                }
                // Check lane history conflicts
                String playerName = getPlayerDisplayName(p);
                List<String> history = laneHistory.getOrDefault(playerName, List.of());
                if (history.contains(lane)) historyConflicts++;
            }
            // Prefer: fewer history conflicts > more primary matches > lower score diff
            if (historyConflicts < bestHistoryConflicts
                || (historyConflicts == bestHistoryConflicts && m > bestM)
                || (historyConflicts == bestHistoryConflicts && m == bestM && score > bestScore)) {
                bestHistoryConflicts = historyConflicts;
                bestM     = m;
                bestScore = score;
                List<Object[]> assign = new ArrayList<>();
                for (int i = 0; i < 5; i++)
                    assign.add(new Object[]{ team.get(perm[i]), LANES_LIST.get(i) });
                bestAssignment = assign;
            }
        }
        return new Object[]{ bestAssignment, bestM, bestScore };
    }

    private void assignRandomLanes(List<Map<String, Object>> team, Map<Integer, String> laneLocks,
                                    Map<String, List<String>> laneHistory) {
        // Identify which lanes are already locked
        Set<String> usedLanes = new HashSet<>();
        Set<Integer> lockedPlayers = new HashSet<>();
        for (Map.Entry<Integer, String> le : laneLocks.entrySet()) {
            int idx = le.getKey();
            if (idx >= 0 && idx < team.size()) {
                team.get(idx).put("assignedLane", le.getValue());
                team.get(idx).put("assignedLaneKo", LANE_KO.getOrDefault(le.getValue(), le.getValue()));
                usedLanes.add(le.getValue());
                lockedPlayers.add(idx);
            }
        }

        // Remaining lanes and unlocked players
        List<String> remainingLanes = new ArrayList<>();
        for (String lane : LANES_LIST) {
            if (!usedLanes.contains(lane)) remainingLanes.add(lane);
        }
        List<Integer> unlockedPlayers = new ArrayList<>();
        for (int i = 0; i < team.size(); i++) {
            if (!lockedPlayers.contains(i)) unlockedPlayers.add(i);
        }

        // Try to find assignment avoiding recent lanes (up to 1000 attempts)
        List<String> bestAssignment = null;
        int bestConflicts = Integer.MAX_VALUE;

        for (int attempt = 0; attempt < 1000; attempt++) {
            Collections.shuffle(remainingLanes);
            int conflicts = 0;
            for (int j = 0; j < unlockedPlayers.size() && j < remainingLanes.size(); j++) {
                int pi = unlockedPlayers.get(j);
                String lane = remainingLanes.get(j);
                String playerName = getPlayerDisplayName(team.get(pi));
                List<String> history = laneHistory.getOrDefault(playerName, List.of());
                if (history.contains(lane)) conflicts++;
            }
            if (conflicts == 0) {
                bestAssignment = new ArrayList<>(remainingLanes);
                break;
            }
            if (conflicts < bestConflicts) {
                bestConflicts = conflicts;
                bestAssignment = new ArrayList<>(remainingLanes);
            }
        }

        // Apply best assignment
        int ri = 0;
        for (int i = 0; i < unlockedPlayers.size() && ri < bestAssignment.size(); i++) {
            int pi = unlockedPlayers.get(i);
            String lane = bestAssignment.get(ri++);
            team.get(pi).put("assignedLane", lane);
            team.get(pi).put("assignedLaneKo", LANE_KO.getOrDefault(lane, lane));
        }
    }

    private String getPlayerDisplayName(Map<String, Object> player) {
        Object dn = player.get("displayName");
        return dn instanceof String ? (String) dn : "";
    }


    /**
     * Check if a team1 split satisfies all fixed and separate constraints.
     */
    private boolean isValidSplit(Set<Integer> team1Set, int n,
                                  List<List<Integer>> fixedGroups,
                                  List<List<Integer>> separateGroups) {
        // Fixed groups: all members must be on same team
        for (List<Integer> group : fixedGroups) {
            if (group.size() < 2) continue;
            boolean first = team1Set.contains(group.get(0));
            for (int i = 1; i < group.size(); i++) {
                if (team1Set.contains(group.get(i)) != first) return false;
            }
        }
        // Separate groups: for each pair, must be on different teams
        for (List<Integer> group : separateGroups) {
            if (group.size() < 2) continue;
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    boolean iInT1 = team1Set.contains(group.get(i));
                    boolean jInT1 = team1Set.contains(group.get(j));
                    if (iInT1 == jInT1) return false;
                }
            }
        }
        return true;
    }

    /**
     * For greedy balance: check if player must go to a specific team due to fixed group constraints.
     * Returns 0=undecided, 1=team1, 2=team2
     */
    private int getForcedTeam(int playerIdx, Set<Integer> team1Set, Set<Integer> team2Set,
                               List<List<Integer>> fixedGroups) {
        for (List<Integer> group : fixedGroups) {
            if (!group.contains(playerIdx)) continue;
            for (int member : group) {
                if (member == playerIdx) continue;
                if (team1Set.contains(member)) return 1;
                if (team2Set.contains(member)) return 2;
            }
        }
        return 0;
    }

    /**
     * For greedy balance: check if player must go to a specific team due to separate constraints.
     * Returns 0=undecided, 1=team1, 2=team2
     */
    private int getSeparateForce(int playerIdx, Set<Integer> team1Set, Set<Integer> team2Set,
                                  List<List<Integer>> separateGroups) {
        for (List<Integer> group : separateGroups) {
            if (!group.contains(playerIdx)) continue;
            for (int member : group) {
                if (member == playerIdx) continue;
                if (team1Set.contains(member)) return 2; // must go to opposite team
                if (team2Set.contains(member)) return 1;
            }
        }
        return 0;
    }

    private List<int[]> generatePermutations(int n) {
        List<int[]> result = new ArrayList<>();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) arr[i] = i;
        permHelper(arr, 0, result);
        return result;
    }

    private void permHelper(int[] arr, int start, List<int[]> result) {
        if (start == arr.length) { result.add(arr.clone()); return; }
        for (int i = start; i < arr.length; i++) {
            int tmp = arr[start]; arr[start] = arr[i]; arr[i] = tmp;
            permHelper(arr, start + 1, result);
            tmp = arr[start]; arr[start] = arr[i]; arr[i] = tmp;
        }
    }

    private List<List<Integer>> generateCombinations(int n, int r) {
        List<List<Integer>> result = new ArrayList<>();
        combHelper(0, n, r, new ArrayList<>(), result);
        return result;
    }

    private void combHelper(int start, int n, int r, List<Integer> cur, List<List<Integer>> result) {
        if (cur.size() == r) { result.add(new ArrayList<>(cur)); return; }
        for (int i = start; i < n; i++) {
            cur.add(i);
            combHelper(i + 1, n, r, cur, result);
            cur.remove(cur.size() - 1);
        }
    }
}
