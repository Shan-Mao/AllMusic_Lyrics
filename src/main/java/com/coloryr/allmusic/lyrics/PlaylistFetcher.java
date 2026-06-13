package com.coloryr.allmusic.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网易云歌单获取器。
 *
 * 支持：
 *   https://music.163.com/playlist?id=123456
 *   https://music.163.com/#/playlist?id=123456
 */
public final class PlaylistFetcher {

    private static final Logger LOG = LoggerFactory.getLogger("AllMusicLyrics");
    private static final ExecutorService POOL =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "PlaylistFetcher");
                t.setDaemon(true);
                return t;
            });

    private static final Pattern PLAYLIST_URL = Pattern.compile(
            "music\\.163\\.com.*playlist\\?id=(\\d+)");

    private static volatile CloseableHttpClient http;

    private PlaylistFetcher() {}

    public static void init() {
        http = HttpClients.custom()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
    }

    /** 缓存的歌单歌曲链接 */
    private static volatile List<String> cachedSongUrls = new ArrayList<>();
    /** 缓存的歌单名 */
    private static volatile String cachedPlaylistName = "";

    public static List<String> getCachedSongUrls() { return cachedSongUrls; }
    public static String getCachedPlaylistName() { return cachedPlaylistName; }

    /**
     * 从消息中检测歌单URL并异步获取歌曲列表。
     * @return CompletableFuture，完成时返回歌曲URL列表
     */
    public static CompletableFuture<List<String>> fetchFromMessage(String message) {
        if (message == null || message.isBlank())
            return CompletableFuture.completedFuture(null);

        Matcher m = PLAYLIST_URL.matcher(message);
        if (!m.find())
            return CompletableFuture.completedFuture(null);

        long playlistId = Long.parseLong(m.group(1));
        return fetchPlaylist(playlistId);
    }

    /**
     * 分页获取歌单全部歌曲链接。
     * 先用 topTrackIds（全量ID），回退到分页 tracks。
     */
    private static CompletableFuture<List<String>> fetchPlaylist(long playlistId) {
        return CompletableFuture.supplyAsync(() -> {
            LOG.info("获取歌单 ID={}", playlistId);
            try {
            List<String> allUrls = new ArrayList<>();
            cachedPlaylistName = "";

            String json = httpGet("https://music.163.com/api/playlist/detail?id=" + playlistId);
            if (json == null) return null;

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.get("code").getAsInt() != 200) {
                LOG.warn("API code={}", root.get("code").getAsInt());
                return null;
            }

            JsonObject result = root.getAsJsonObject("result");
            if (result == null) return null;
            cachedPlaylistName = result.has("name") ? result.get("name").getAsString() : "歌单";

            // 优先 topTrackIds（通常包含全量ID），注意值可能是 null
            JsonArray ids = getJsonArrayOrNull(result, "topTrackIds");
            if (ids == null) ids = getJsonArrayOrNull(result, "trackIds");

            LOG.info("topTrackIds={}, trackIds={}, tracks={}",
                    getJsonArrayOrNull(result, "topTrackIds") != null,
                    getJsonArrayOrNull(result, "trackIds") != null,
                    getJsonArrayOrNull(result, "tracks") != null ?
                            getJsonArrayOrNull(result, "tracks").size() : 0);

            int trackCount = result.has("trackCount")
                    ? result.get("trackCount").getAsInt() : 0;
            LOG.info("trackCount={}", trackCount);

            if (ids != null && ids.size() > 0) {
                for (int i = 0; i < ids.size(); i++)
                    allUrls.add("https://music.163.com/song?id=" +
                            ids.get(i).getAsJsonObject().get("id").getAsLong());
            } else {
                // tracks 回退 + 翻页（trackCount=0 则只取首页）
                int offset = 0;
                int target = trackCount > 0 ? trackCount : Integer.MAX_VALUE;
                while (allUrls.size() < target) {
                    String pageJson = offset == 0 ? json
                            : httpGet("https://music.163.com/api/playlist/detail?id=" +
                                    playlistId + "&offset=" + offset + "&limit=100");
                    if (pageJson == null) break;

                    JsonObject pageRoot = JsonParser.parseString(pageJson).getAsJsonObject();
                    if (pageRoot.get("code").getAsInt() != 200) break;

                    JsonObject pageResult = pageRoot.getAsJsonObject("result");
                    JsonArray tracks = getJsonArrayOrNull(pageResult, "tracks");
                    if (tracks == null || tracks.isEmpty()) break;

                    for (int i = 0; i < tracks.size(); i++)
                        allUrls.add("https://music.163.com/song?id=" +
                                tracks.get(i).getAsJsonObject().get("id").getAsLong());
                    offset += tracks.size();
                    if (tracks.size() < 100) break; // 最后一页
                }
            }

            LOG.info("歌单「{}」共 {} 首", cachedPlaylistName, allUrls.size());
            cachedSongUrls = allUrls;
            return allUrls;
            } catch (Exception e) {
                LOG.error("解析歌单失败: {}", e.toString());
                return null;
            }
        }, POOL);
    }

    private static JsonArray getJsonArrayOrNull(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        var el = obj.get(key);
        return el.isJsonNull() ? null : el.getAsJsonArray();
    }

    private static String httpGet(String url) {
        try {
            LOG.info("HTTP GET: {}", url.substring(0, Math.min(60, url.length())));
            HttpGet req = new HttpGet(url);
            req.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            req.setHeader("Referer", "https://music.163.com/");
            req.setHeader("Cookie", "os=pc; osver=Microsoft-Windows-10; appver=2.9.7; channel=netease; WEVNSM=1.0.0; WNMCID=zlpxumx.bf5n.4h22.bo3f.92agr.5da27");
            try (CloseableHttpResponse resp = http.execute(req)) {
                int code = resp.getCode();
                LOG.info("HTTP response: {}", code);
                if (code == 429) { Thread.sleep(3000); return httpGet(url); }
                if (code != 200) { LOG.warn("HTTP {} {}", code, url.substring(0, 50)); return null; }
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                LOG.info("HTTP body length: {}", body.length());
                return body;
            }
        } catch (Exception e) { LOG.error("HTTP error: {}", e.toString()); return null; }
    }

    private static final java.util.concurrent.ScheduledExecutorService SENDER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PlaylistSender");
                t.setDaemon(true);
                return t;
            });

    /**
     * 从缓存中取出指定数量的歌曲链接并逐条发送（间隔 600ms，避免指令被吞）。
     */
    public static void sendSongs(int count) {
        if (cachedSongUrls.isEmpty()) return;

        int send = Math.min(count, cachedSongUrls.size());
        List<String> toSend = new java.util.ArrayList<>(cachedSongUrls.subList(0, send));
        cachedSongUrls = new java.util.ArrayList<>(cachedSongUrls.subList(send, cachedSongUrls.size()));

        for (int i = 0; i < toSend.size(); i++) {
            final String url = toSend.get(i);
            SENDER.schedule(() -> sendMusicCommand(url), i * 600L, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        LOG.info("已排队发送 {} 首，剩余 {} 首", send, cachedSongUrls.size());
    }

    private static void sendMusicCommand(String songUrl) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            String cmd = "music " + songUrl;
            mc.player.connection.sendCommand(cmd);
            LOG.info("发送: {}", cmd);
        }
    }

    /** 剩余歌曲数 */
    public static int remaining() {
        return cachedSongUrls.size();
    }
}
