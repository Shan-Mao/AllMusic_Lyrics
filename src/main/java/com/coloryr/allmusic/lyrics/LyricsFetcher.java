package com.coloryr.allmusic.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 歌词获取器。
 *
 * 数据源:
 * - 网易云音乐 API（直接歌曲ID → 精准）
 * - 网易云搜索 API（歌名搜索 → 后备）
 * - LRCLIB API（国际后备）
 *
 * HTTP 层使用 JDK 内置 {@link java.net.http.HttpClient}，
 * 不再依赖 Apache HttpClient5（26.2 起前置模组已将其重定位内嵌）。
 */
public final class LyricsFetcher {

    private static final Logger LOG = LoggerFactory.getLogger("AllMusicLyrics");
    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "LyricsFetcher");
                t.setDaemon(true);
                return t;
            });

    private static volatile HttpClient http;
    private static volatile boolean shutdown;

    /** 网易云URL正则：https://music.163.com/song?id=xxxxx 或 /#/song?id=xxxxx */
    private static final Pattern NETEASE_URL = Pattern.compile(
            "music\\.163\\.com.*[/#]song\\?id=(\\d+)");
    /** 正在解析歌曲[ID] 消息正则 */
    private static final Pattern PARSING_MSG = Pattern.compile(
            "正在解析歌曲[：:]?\\s*(\\d+)");
    /** 正在播放 消息正则（后备提取歌名） */
    private static final Pattern NOW_PLAYING = Pattern.compile(
            "正在播放[：:]\\s*(.+)");

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private LyricsFetcher() {}

    public static void init() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(POOL)
                .build();
    }

    public static void shutdown() {
        shutdown = true;
        POOL.shutdownNow();
        http = null;
    }

    // ==================================================================
    //  公开 API
    // ==================================================================

    /**
     * 从聊天消息中提取信息并尝试获取歌词。
     * 支持:
     *   - [AllMusic3]正在解析歌曲[123456]   → 直接ID获取
     *   - /music https://music.163.com/song?id=123456 → URL提取
     *   - 歌曲名搜索（后备）
     */
    public static CompletableFuture<String> fetch(String message) {
        if (shutdown || message == null || message.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        // 1) 尝试从"正在解析歌曲[ID]"中提取
        Matcher m = PARSING_MSG.matcher(message);
        if (m.find()) {
            long songId = Long.parseLong(m.group(1));
            LOG.info("检测到网易云歌曲ID: {}", songId);
            return fetchById(songId);
        }

        // 2) 尝试从网易云URL中提取
        m = NETEASE_URL.matcher(message);
        if (m.find()) {
            long songId = Long.parseLong(m.group(1));
            LOG.info("从URL提取歌曲ID: {}", songId);
            return fetchById(songId);
        }

        // 3) 尝试从"正在播放：歌名"中提取歌名搜索
        m = NOW_PLAYING.matcher(message);
        if (m.find()) {
            String name = m.group(1).trim();
            // 去掉 "by: xxx" 部分
            int byIdx = name.indexOf(" by:");
            if (byIdx > 0) name = name.substring(0, byIdx).trim();
            LOG.info("检测到正在播放: {}", name);
            return fetchByName(name);
        }

        // 4) 直接当作歌名搜索
        return fetchByName(message);
    }

    /**
     * 通过网易云歌曲ID直接获取歌词（最精准的方式）。
     */
    public static CompletableFuture<String> fetchById(long songId) {
        return CompletableFuture.supplyAsync(() -> {
            LOG.info("直接获取歌词 ID={}", songId);
            String lrc = neteaseLyric(songId);
            if (lrc != null) {
                LOG.info("✔ 直接获取歌词成功 ID={}", songId);
                return lrc;
            }
            LOG.warn("✘ 直接获取歌词失败 ID={}", songId);
            return null;
        }, POOL);
    }

    /**
     * 通过歌名搜索获取歌词（后备方案）。
     */
    private static CompletableFuture<String> fetchByName(String songInfo) {
        return CompletableFuture.supplyAsync(() -> {
            String name = clean(songInfo);
            String artist = "";
            if (name.contains(" - ")) {
                int idx = name.indexOf(" - ");
                artist = name.substring(idx + 3).trim();
                name = name.substring(0, idx).trim();
            }

            LOG.info("搜索歌词: {} - {}", name, artist);

            // 1) 网易云搜索
            String lrc = neteaseSearch(name, artist);
            if (lrc != null) { LOG.info("✔ 网易云搜索成功: {}", name); return lrc; }

            // 2) 网易云搜索（仅歌名）
            if (!artist.isEmpty()) {
                lrc = neteaseSearch(name, "");
                if (lrc != null) { LOG.info("✔ 网易云搜索成功(仅歌名): {}", name); return lrc; }
            }

            // 3) LRCLIB
            lrc = lrclib(name, artist);
            if (lrc != null) { LOG.info("✔ LRCLIB成功: {}", name); return lrc; }

            LOG.warn("✘ 未找到歌词: {}", name);
            return null;
        }, POOL);
    }

    // ==================================================================
    //  网易云 API
    // ==================================================================

    /** 通过歌曲ID直接获取歌词 */
    private static String neteaseLyric(long songId) {
        try {
            String url = "https://music.163.com/api/song/lyric?id=" +
                    songId + "&lv=1&kv=1&tv=-1";
            String json = get(url);
            if (json == null) return null;

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("code").getAsInt() != 200) return null;

            JsonObject lrcObj = root.getAsJsonObject("lrc");
            if (lrcObj != null) {
                String lyric = lrcObj.get("lyric").getAsString();
                if (lyric != null && !lyric.isBlank()) return lyric;
            }

            JsonObject tlrcObj = root.getAsJsonObject("tlyric");
            if (tlrcObj != null) {
                String tLyric = tlrcObj.get("lyric").getAsString();
                if (tLyric != null && !tLyric.isBlank()) return tLyric;
            }
        } catch (Exception e) {
            LOG.debug("直接获取歌词异常: {}", e.toString());
        }
        return null;
    }

    /** 网易云搜索 */
    private static String neteaseSearch(String name, String artist) {
        try {
            String keyword = artist.isEmpty() ? name : name + " " + artist;
            String enc = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String json = get("https://music.163.com/api/search/get/?s=" +
                    enc + "&type=1&limit=5&offset=0");
            if (json == null) return null;

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("code").getAsInt() != 200) return null;

            var songs = root.getAsJsonObject("result").getAsJsonArray("songs");
            if (songs == null || songs.isEmpty()) return null;

            long songId = songs.get(0).getAsJsonObject().get("id").getAsLong();
            String normName = normalize(name);
            for (int i = 0; i < songs.size(); i++) {
                String found = songs.get(i).getAsJsonObject().get("name").getAsString();
                if (normalize(found).contains(normName) || normName.contains(normalize(found))) {
                    songId = songs.get(i).getAsJsonObject().get("id").getAsLong();
                    break;
                }
            }

            return neteaseLyric(songId);
        } catch (Exception e) {
            LOG.debug("网易云搜索异常: {}", e.toString());
        }
        return null;
    }

    // ==================================================================
    //  LRCLIB
    // ==================================================================

    private static String lrclib(String name, String artist) {
        try {
            String a = URLEncoder.encode(artist.isEmpty() ? "unknown" : artist,
                    StandardCharsets.UTF_8);
            String t = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String json = get("https://lrclib.net/api/get?artist_name=" + a +
                    "&track_name=" + t);
            if (json == null) return null;

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("syncedLyrics") && !root.get("syncedLyrics").isJsonNull()) {
                String sl = root.get("syncedLyrics").getAsString();
                if (sl != null && !sl.isBlank()) return sl;
            }
            if (root.has("plainLyrics") && !root.get("plainLyrics").isJsonNull()) {
                String pl = root.get("plainLyrics").getAsString();
                if (pl != null && !pl.isBlank()) return plainToLrc(pl);
            }
        } catch (Exception e) {
            LOG.debug("LRCLIB异常: {}", e.toString());
        }
        return null;
    }

    // ==================================================================
    //  HTTP 工具（JDK HttpClient）
    // ==================================================================

    private static String get(String url) {
        HttpClient client = http;
        if (shutdown || client == null) return null;
        try {
            HttpRequest req = newRequest(url);
            HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resp.statusCode() == 429) {
                LOG.debug("被限速，等待2秒重试...");
                Thread.sleep(2000);
                resp = client.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }

            if (resp.statusCode() == 200) return resp.body();
            if (resp.statusCode() != 404)
                LOG.debug("HTTP {} → {}", resp.statusCode(), url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.debug("HTTP请求失败: {}", e.toString());
        }
        return null;
    }

    private static HttpRequest newRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Referer", "https://music.163.com/")
                .header("Cookie", "os=pc; osver=Microsoft-Windows-10; appver=2.9.7")
                .GET()
                .build();
    }

    // ==================================================================
    //  文本工具
    // ==================================================================

    static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("\\.(mp3|flac|wav|ogg|m4a|aac|wma|ape)\\b", "")
                .replaceAll("[(（](320|128|192|HQ|SQ|无损|高音质|标准|极高|臻品).*?[)）]", "")
                .replaceAll("\\[(320|128|192|HQ|SQ|无损|高音质|标准|极高|臻品).*?\\]", "")
                .trim();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[\\s\\p{Punct}（（）)\\(\\[\\]【】《》]", "").trim();
    }

    private static String plainToLrc(String text) {
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\\r?\\n");
        int i = 0;
        for (String ln : lines) {
            String t = ln.trim();
            if (t.isEmpty()) continue;
            long sec = i * 5L;
            sb.append(String.format("[%02d:%02d.00]%s\n", sec / 60, sec % 60, t));
            i++;
        }
        return sb.toString();
    }
}
