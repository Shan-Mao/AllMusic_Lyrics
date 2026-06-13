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
     * 通过歌单ID获取所有歌曲的网易云链接。
     */
    private static CompletableFuture<List<String>> fetchPlaylist(long playlistId) {
        return CompletableFuture.supplyAsync(() -> {
            LOG.info("获取歌单 ID={}", playlistId);
            try {
                String url = "https://music.163.com/api/playlist/detail?id=" + playlistId;
                HttpGet req = new HttpGet(url);
                req.setHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                req.setHeader("Referer", "https://music.163.com/");

                try (CloseableHttpResponse resp = http.execute(req)) {
                    if (resp.getCode() != 200) {
                        LOG.warn("歌单API返回 {}", resp.getCode());
                        return null;
                    }
                    String json = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                    if (root.get("code").getAsInt() != 200) {
                        LOG.warn("歌单API code != 200");
                        return null;
                    }

                    JsonObject result = root.getAsJsonObject("result");
                    cachedPlaylistName = result.get("name").getAsString();
                    JsonArray tracks = result.getAsJsonArray("tracks");

                    List<String> urls = new ArrayList<>();
                    for (int i = 0; i < tracks.size(); i++) {
                        JsonObject track = tracks.get(i).getAsJsonObject();
                        long songId = track.get("id").getAsLong();
                        urls.add("https://music.163.com/song?id=" + songId);
                    }

                    LOG.info("歌单「{}」共 {} 首", cachedPlaylistName, urls.size());
                    cachedSongUrls = urls;
                    return urls;
                }
            } catch (Exception e) {
                LOG.error("获取歌单失败: {}", e.toString());
                return null;
            }
        }, POOL);
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
