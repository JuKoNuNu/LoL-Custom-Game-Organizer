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
                                       List<List<Integer>> separateGroups) {
        int n    = players.size();
        int half = n / 2;

        if (fixedGroups == null)    fixedGroups    = List.of();
        if (separateGroups == null) separateGroups = List.of();

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

                Object[] t1Assign = getBestAssignment(t1Players, allPerms);
                Object[] t2Assign = getBestAssignment(t2Players, allPerms);

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
                assignRandomLanes(t1Data);
                assignRandomLanes(t2Data);
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

    private Object[] getBestAssignment(List<Map<String, Object>> team, List<int[]> allPerms) {
        int    bestM     = -1;
        double bestScore = 0;
        List<Object[]> bestAssignment = null;

        for (int[] perm : allPerms) {
            int    m     = 0;
            double score = 0;
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
            }
            if (m > bestM) {
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

    private void assignRandomLanes(List<Map<String, Object>> team) {
        List<String> roles = new ArrayList<>(LANES_LIST);
        Collections.shuffle(roles);
        for (int i = 0; i < team.size() && i < roles.size(); i++) {
            String lane = roles.get(i);
            team.get(i).put("assignedLane",   lane);
            team.get(i).put("assignedLaneKo", LANE_KO.getOrDefault(lane, lane));
        }
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
