package com.balance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RiotApiService {

    @Value("${RIOT_apiKey}")
    private String apiKey;

    public static final String KR   = "https://kr.api.riotgames.com";
    public static final String ASIA = "https://asia.api.riotgames.com";

    private static final Map<String, Integer> TIER_BASE = Map.of(
        "IRON", 0, "BRONZE", 400, "SILVER", 800, "GOLD", 1200,
        "PLATINUM", 1600, "EMERALD", 2000, "DIAMOND", 2400,
        "MASTER", 2800, "GRANDMASTER", 2800, "CHALLENGER", 2800
    );
    private static final Map<String, Integer> RANK_BONUS = Map.of(
        "IV", 0, "III", 100, "II", 200, "I", 300
    );

    public static final Map<String, String> LANE_KO = new LinkedHashMap<>() {{
        put("TOP", "탑"); put("JUNGLE", "정글"); put("MIDDLE", "미드");
        put("BOTTOM", "원딜"); put("UTILITY", "서포터"); put("UNKNOWN", "미정");
    }};

    private static final Map<String, String> MONSTER_KO = new LinkedHashMap<>() {{
        put("DRAGON", "드래곤");         put("FIRE_DRAGON", "화염 드래곤");
        put("WATER_DRAGON", "바다 드래곤"); put("AIR_DRAGON", "구름 드래곤");
        put("EARTH_DRAGON", "산 드래곤");  put("HEXTECH_DRAGON", "헥스텍 드래곤");
        put("CHEMTECH_DRAGON", "화학공학 드래곤"); put("ELDER_DRAGON", "장로 드래곤");
        put("BARON_NASHOR", "바론 나샤르"); put("RIFTHERALD", "협곡의 전령");
        put("HORDE", "공허 유충");
    }};

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String ddVersion = "15.4.1";
    private String ddBase;
    private Map<Integer, Map<String, String>> champMap = new HashMap<>();
    private Map<Integer, String> itemMap = new HashMap<>();

    public RiotApiService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        try { ddVersion = fetchDdVersion(); } catch (Exception ignored) {}
        ddBase = "https://ddragon.leagueoflegends.com/cdn/" + ddVersion;
        try { champMap = fetchChampionMap(); } catch (Exception ignored) {}
        try { itemMap  = fetchItemMap();     } catch (Exception ignored) {}
    }

    private String fetchDdVersion() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://ddragon.leagueoflegends.com/api/versions.json"))
            .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(resp.body()).get(0).asText();
    }

    private Map<Integer, Map<String, String>> fetchChampionMap() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(ddBase + "/data/ko_KR/champion.json"))
            .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode data = objectMapper.readTree(resp.body()).get("data");
        Map<Integer, Map<String, String>> result = new HashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = data.fields(); it.hasNext(); ) {
            JsonNode champ = it.next().getValue();
            int key = Integer.parseInt(champ.get("key").asText());
            Map<String, String> info = new HashMap<>();
            info.put("name", champ.get("name").asText());
            info.put("img",  champ.get("id").asText());
            result.put(key, info);
        }
        return result;
    }

    private Map<Integer, String> fetchItemMap() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(ddBase + "/data/ko_KR/item.json"))
            .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode data = objectMapper.readTree(resp.body()).get("data");
        Map<Integer, String> result = new HashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = data.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            result.put(Integer.parseInt(e.getKey()), e.getValue().get("name").asText());
        }
        return result;
    }

    public record RiotResponse(int status, JsonNode body) {}

    public RiotResponse rget(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("X-Riot-Token", apiKey)
            .timeout(Duration.ofSeconds(10))
            .GET().build();

        HttpResponse<String> response = null;
        for (int i = 0; i < 3; i++) {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse("1");
                Thread.sleep(Long.parseLong(retryAfter) * 1000L);
            } else {
                break;
            }
        }

        int status = response.statusCode();
        JsonNode body = null;
        String bodyStr = response.body();
        if (bodyStr != null && !bodyStr.isBlank()) {
            try { body = objectMapper.readTree(bodyStr); } catch (Exception ignored) {}
        }
        return new RiotResponse(status, body);
    }

    public int calcScore(JsonNode entry) {
        if (entry == null || entry.isNull()) return 0;
        String t  = entry.path("tier").asText("IRON");
        int base  = TIER_BASE.getOrDefault(t, 0);
        int lp    = entry.path("leaguePoints").asInt(0);
        if (Set.of("MASTER", "GRANDMASTER", "CHALLENGER").contains(t)) return base + lp;
        String rank = entry.path("rank").asText("IV");
        return base + RANK_BONUS.getOrDefault(rank, 0) + lp;
    }

    public String encodePath(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public Map<String, Object> getPlayerData(String gameName, String tagLine) throws Exception {
        String accountUrl = ASIA + "/riot/account/v1/accounts/by-riot-id/"
            + encodePath(gameName) + "/" + encodePath(tagLine);
        RiotResponse accountResp = rget(accountUrl);

        if (accountResp.status() == 404)
            throw new NotFoundException("소환사를 찾을 수 없습니다: " + gameName + "#" + tagLine);
        if (accountResp.status() != 200)
            throw new RuntimeException("Riot API 오류 " + accountResp.status());

        String puuid = accountResp.body().path("puuid").asText("");
        if (puuid.isEmpty()) throw new RuntimeException("PUUID를 찾을 수 없습니다");

        RiotResponse summResp = rget(KR + "/lol/summoner/v4/summoners/by-puuid/" + puuid);
        if (summResp.status() != 200) throw new RuntimeException("소환사 정보 조회 실패");
        JsonNode summ = summResp.body();

        RiotResponse rankedResp = rget(KR + "/lol/league/v4/entries/by-puuid/" + puuid);
        JsonNode rankedArr = (rankedResp.status() == 200 && rankedResp.body() != null)
            ? rankedResp.body() : objectMapper.createArrayNode();

        JsonNode solo = null, flex = null;
        for (JsonNode entry : rankedArr) {
            String queueType = entry.path("queueType").asText();
            if ("RANKED_SOLO_5x5".equals(queueType)) solo = entry;
            if ("RANKED_FLEX_SR".equals(queueType))  flex = entry;
        }

        int MATCH_COUNT = 30;
        RiotResponse matchIdsResp = rget(
            ASIA + "/lol/match/v5/matches/by-puuid/" + puuid + "/ids?count=" + MATCH_COUNT);
        List<String> matchIds = new ArrayList<>();
        if (matchIdsResp.status() == 200 && matchIdsResp.body() != null && matchIdsResp.body().isArray()) {
            matchIdsResp.body().forEach(id -> matchIds.add(id.asText()));
        }

        Map<String, Integer> laneCounts = new HashMap<>();
        Map<String, Map<String, Integer>> laneStats  = new HashMap<>();
        Map<String, Map<String, Object>> champStats  = new HashMap<>();
        List<Map<String, Object>> recentMatches = new ArrayList<>();

        for (String mid : matchIds) {
            RiotResponse matchResp = rget(ASIA + "/lol/match/v5/matches/" + mid);
            if (matchResp.status() != 200 || matchResp.body() == null) continue;

            JsonNode minfo = matchResp.body().get("info");
            if (minfo == null) continue;

            for (JsonNode p : minfo.path("participants")) {
                if (!puuid.equals(p.path("puuid").asText())) continue;

                String cname  = p.path("championName").asText("Unknown");
                String pos    = p.path("teamPosition").asText("");
                boolean isWin = p.path("win").asBoolean(false);
                int k = p.path("kills").asInt(0);
                int d = p.path("deaths").asInt(0);
                int a = p.path("assists").asInt(0);

                if (!pos.isEmpty()) {
                    laneCounts.merge(pos, 1, Integer::sum);
                    laneStats.computeIfAbsent(pos, x -> {
                        Map<String, Integer> m = new HashMap<>();
                        m.put("w", 0); m.put("l", 0); m.put("n", 0); return m;
                    });
                    laneStats.get(pos).merge("n", 1, Integer::sum);
                    laneStats.get(pos).merge(isWin ? "w" : "l", 1, Integer::sum);
                }

                champStats.computeIfAbsent(cname, x -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("w", 0); m.put("l", 0); m.put("k", 0);
                    m.put("d", 0); m.put("a", 0); m.put("n", 0); return m;
                });
                Map<String, Object> cs = champStats.get(cname);
                cs.put("n", (Integer)cs.get("n") + 1);
                cs.put("k", (Integer)cs.get("k") + k);
                cs.put("d", (Integer)cs.get("d") + d);
                cs.put("a", (Integer)cs.get("a") + a);
                cs.put(isWin ? "w" : "l", (Integer)cs.get(isWin ? "w" : "l") + 1);

                if (recentMatches.size() < 20) {
                    int dur = minfo.path("gameDuration").asInt(0);
                    List<Integer> items = new ArrayList<>();
                    for (int i = 0; i < 7; i++) items.add(p.path("item" + i).asInt(0));

                    Map<String, Object> matchData = new LinkedHashMap<>();
                    matchData.put("matchId",       mid);
                    matchData.put("participantId", p.path("participantId").asInt(0));
                    matchData.put("champion",      cname);
                    matchData.put("win",           isWin);
                    matchData.put("kills",         k);
                    matchData.put("deaths",        d);
                    matchData.put("assists",       a);
                    matchData.put("kda",  Math.round((k + a) / (double)Math.max(d, 1) * 100.0) / 100.0);
                    matchData.put("lane", LANE_KO.getOrDefault(pos, pos.isEmpty() ? "?" : pos));
                    matchData.put("duration", dur / 60 + ":" + String.format("%02d", dur % 60));
                    matchData.put("durationSec", dur);
                    matchData.put("items", items);
                    recentMatches.add(matchData);
                }
                break;
            }
        }

        List<Map<String, Object>> seasonMost = champStats.entrySet().stream()
            .sorted((a2, b2) -> (Integer)b2.getValue().get("n") - (Integer)a2.getValue().get("n"))
            .limit(10)
            .map(e -> {
                String cn = e.getKey();
                Map<String, Object> s = e.getValue();
                int n  = (Integer) s.get("n");
                int w  = (Integer) s.get("w");
                int kk = (Integer) s.get("k");
                int dd = (Integer) s.get("d");
                int aa = (Integer) s.get("a");
                int wr = n > 0 ? (int)Math.round(w * 100.0 / n) : 0;
                double kda = Math.round((kk + aa) / (double)Math.max(dd, 1) * 100.0) / 100.0;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("name", cn); r.put("games", n); r.put("winRate", wr); r.put("kda", kda);
                r.put("imgUrl", ddBase + "/img/champion/" + cn + ".png");
                return r;
            }).collect(Collectors.toList());

        List<Map.Entry<String, Integer>> sortedLanes = laneCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());
        String primary   = sortedLanes.size() > 0 ? sortedLanes.get(0).getKey() : "UNKNOWN";
        String secondary = sortedLanes.size() > 1 ? sortedLanes.get(1).getKey() : "UNKNOWN";

        RiotResponse masteryResp = rget(
            KR + "/lol/champion-mastery/v4/champion-masteries/by-puuid/" + puuid + "/top?count=10");
        List<Map<String, Object>> topChamps = new ArrayList<>();
        if (masteryResp.status() == 200 && masteryResp.body() != null && masteryResp.body().isArray()) {
            for (JsonNode m : masteryResp.body()) {
                int cid = m.path("championId").asInt(-1);
                Map<String, String> info = champMap.getOrDefault(cid, new HashMap<>());
                Map<String, Object> cd = new LinkedHashMap<>();
                cd.put("name",    info.getOrDefault("name", String.valueOf(cid)));
                String img = info.get("img");
                cd.put("imgUrl", img != null ? ddBase + "/img/champion/" + img + ".png" : "");
                cd.put("level",  m.path("championLevel").asInt(0));
                cd.put("points", m.path("championPoints").asInt(0));
                topChamps.add(cd);
            }
        }

        int soloSc = calcScore(solo);
        int flexSc = calcScore(flex);
        Map<String, Object> fmtSolo = fmtRank(solo, soloSc, "솔로랭크");
        Map<String, Object> fmtFlex = fmtRank(flex, flexSc, "자유랭크");
        Map<String, Object> highest;
        int score;
        if (soloSc >= flexSc) { highest = fmtSolo; score = soloSc; }
        else                   { highest = fmtFlex; score = flexSc; }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("displayName",    gameName + "#" + tagLine);
        resp.put("gameName",       gameName);
        resp.put("tagLine",        tagLine);
        resp.put("level",          summ.path("summonerLevel").asInt(0));
        resp.put("iconUrl", ddBase + "/img/profileicon/" + summ.path("profileIconId").asInt(29) + ".png");
        resp.put("highestRank",    highest);
        resp.put("primaryLane",    primary);
        resp.put("secondaryLane",  secondary);
        resp.put("primaryLaneKo",  LANE_KO.getOrDefault(primary, "미정"));
        resp.put("laneStats",      laneStats);
        resp.put("score",          score);
        resp.put("topChamps",      topChamps);
        resp.put("seasonMost",     seasonMost);
        resp.put("recentMatches",  recentMatches);
        resp.put("totalMatches",   matchIds.size());
        return resp;
    }

    private Map<String, Object> fmtRank(JsonNode entry, int score, String queueType) {
        if (entry == null || entry.isNull()) return null;
        int w = entry.path("wins").asInt(0);
        int l = entry.path("losses").asInt(0);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tier",      entry.path("tier").asText("IRON"));
        r.put("rank",      entry.path("rank").asText("IV"));
        r.put("lp",        entry.path("leaguePoints").asInt(0));
        r.put("wins",      w);
        r.put("losses",    l);
        r.put("winRate",   (w + l) > 0 ? Math.round(w * 100.0 / (w + l) * 10.0) / 10.0 : 0.0);
        r.put("score",     score);
        r.put("queueType", queueType);
        return r;
    }

    public List<Map<String, Object>> getTimeline(String matchId, int pid) throws Exception {
        RiotResponse resp = rget(ASIA + "/lol/match/v5/timelines/" + matchId);
        if (resp.status() != 200 || resp.body() == null)
            throw new RuntimeException("타임라인 로드 실패");

        JsonNode frames = resp.body().path("info").path("frames");
        Set<Integer> undoSet = new HashSet<>();

        for (JsonNode f : frames) {
            for (JsonNode ev : f.path("events")) {
                if ("ITEM_UNDO".equals(ev.path("type").asText())
                    && ev.path("participantId").asInt() == pid) {
                    undoSet.add(ev.path("beforeActionItemId").asInt());
                }
            }
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (JsonNode f : frames) {
            for (JsonNode ev : f.path("events")) {
                int minV = ev.path("timestamp").asInt(0) / 60000;
                String et = ev.path("type").asText();

                if ("ITEM_PURCHASED".equals(et) && ev.path("participantId").asInt() == pid) {
                    int iid = ev.path("itemId").asInt();
                    if (!undoSet.contains(iid)) {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("type",   "item");
                        e.put("minute", minV);
                        e.put("name",   itemMap.getOrDefault(iid, "ID " + iid));
                        e.put("imgUrl", ddBase + "/img/item/" + iid + ".png");
                        events.add(e);
                    }
                } else if ("CHAMPION_KILL".equals(et)) {
                    if (ev.path("killerId").asInt() == pid) {
                        events.add(Map.of("type", "kill",   "minute", minV));
                    } else if (ev.path("victimId").asInt() == pid) {
                        events.add(Map.of("type", "death",  "minute", minV));
                    } else {
                        for (JsonNode aid : ev.path("assistingParticipantIds")) {
                            if (aid.asInt() == pid) {
                                events.add(Map.of("type", "assist", "minute", minV));
                                break;
                            }
                        }
                    }
                } else if ("ELITE_MONSTER_KILL".equals(et)) {
                    String sub = ev.path("monsterSubType").asText("");
                    if (sub.isEmpty()) sub = ev.path("monsterType").asText("");
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("type",   "objective");
                    e.put("minute", minV);
                    e.put("name",   MONSTER_KO.getOrDefault(sub, sub));
                    events.add(e);
                }
            }
        }
        events.sort(Comparator.comparingInt(e -> (Integer) e.get("minute")));
        return events;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    public String getDdBase()  { return ddBase; }
    public String getDdVersion() { return ddVersion; }
    public Map<Integer, Map<String, String>> getChampMap() { return champMap; }
    public Map<Integer, String> getItemMap() { return itemMap; }
}
