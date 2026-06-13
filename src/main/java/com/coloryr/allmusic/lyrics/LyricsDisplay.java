package com.coloryr.allmusic.lyrics;

import com.coloryr.allmusic.lyrics.config.LyricsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 歌词显示引擎。
 *
 * 生命周期:
 *   1. "正在解析歌曲[ID]" → 后台获取歌词，缓存起来（不显示）
 *   2. "正在播放：歌名"   → 激活显示已缓存的歌词
 *   3. PLAY / STOP / CLEAR → 控制播放状态和清理
 */
public final class LyricsDisplay {

    private static final Logger LOG = LoggerFactory.getLogger("AllMusicLyrics");

    // 歌词数据
    private static volatile List<LrcLine> lines;
    private static volatile String songDisplayName = "";

    // 播放状态
    private static volatile long currentMs;
    private static volatile boolean active;
    private static volatile boolean playing;
    private static volatile long lastTickNano;

    // 预缓存
    private static volatile List<LrcLine> pendingLines;
    private static volatile String pendingSongName;
    private static volatile long pendingTimestamp;     // 缓存时间戳，超时清除

    /** 缓存过期时间（毫秒），默认 20 分钟 */
    private static final long CACHE_TTL_MS = 20 * 60 * 1000;

    // 去重
    private static volatile String lastParseMsg;
    private static volatile String lastPlayMsg;

    // 渲染常量（可通过 Mod Menu 配置）

    private static final Pattern PARSING_ID  = Pattern.compile("正在解析歌曲[：:]?\\s*(\\d+)");
    private static final Pattern NOW_PLAYING = Pattern.compile("正在播放[：:]\\s*(.+?)(?:\\s+by:.*)?$");
    private static final Pattern NETEASE_URL = Pattern.compile("music\\.163\\.com.*[/#]song\\?id=(\\d+)");

    private LyricsDisplay() {}

    // ==================================================================
    //  消息入口
    // ==================================================================

    public static void onSongParsing(String message) {
        if (message == null || message.isBlank()) return;
        if (message.equals(lastParseMsg)) return;
        lastParseMsg = message;

        Matcher m = PARSING_ID.matcher(message);
        if (!m.find()) {
            m = NETEASE_URL.matcher(message);
            if (!m.find()) return;
        }
        long songId = Long.parseLong(m.group(1));
        LOG.info("后台获取歌词 ID={}", songId);

        LyricsFetcher.fetchById(songId).thenAccept(lrcText -> {
            if (lrcText != null && !lrcText.isBlank()) {
                List<LrcLine> parsed = LyricsParser.parse(lrcText);
                LOG.info("歌词已缓存: {} 行 (等待播放)", parsed.size());
                pendingLines = parsed;
                pendingTimestamp = System.currentTimeMillis();
            }
        }).exceptionally(ex -> {
            LOG.error("歌词获取异常: {}", ex.toString());
            return null;
        });
    }

    public static void onNowPlaying(String message) {
        if (message == null || message.isBlank()) return;
        if (message.equals(lastPlayMsg)) return;
        lastPlayMsg = message;

        Matcher m = NOW_PLAYING.matcher(message);
        if (!m.find()) return;

        String songName = m.group(1).trim();
        LOG.info("正在播放: {}", songName);

        if (pendingLines != null && !pendingLines.isEmpty()) {
            lines = pendingLines;
            pendingLines = null;
        } else {
            LOG.info("无缓存，用歌名搜索...");
            LyricsFetcher.fetch(message).thenAccept(lrcText -> {
                if (lrcText != null && !lrcText.isBlank()) {
                    List<LrcLine> parsed = LyricsParser.parse(lrcText);
                    if (!parsed.isEmpty()) {
                        lines = parsed;
                        songDisplayName = cleanName(songName);
                        active = true;
                        currentMs = 0;
                        lastTickNano = System.nanoTime();
                        LOG.info("歌词显示已激活: {}", songDisplayName);
                    }
                }
            }).exceptionally(ex -> null);
            return;
        }

        songDisplayName = cleanName(songName);
        active = true;
        currentMs = 0;
        lastTickNano = System.nanoTime();
        LOG.info("歌词显示已激活: {}", songDisplayName);
    }

    // ==================================================================
    //  播放控制
    // ==================================================================

    public static void onPlay() {
        playing = true;
        if (lines != null) {
            active = true;
            currentMs = 0;
            lastTickNano = System.nanoTime();
        }
    }

    public static void onStop() { playing = false; }

    public static void onClear() {
        active = false;
        playing = false;
        lines = null;
        pendingLines = null;
        pendingSongName = null;
        pendingTimestamp = 0;
        songDisplayName = "";
        lastParseMsg = null;
        lastPlayMsg = null;
        currentMs = 0;
    }

    // ==================================================================
    //  时间驱动
    // ==================================================================

    public static void tick() {
        // 定期清除过期预缓存（5 分钟未播放）
        if (pendingLines != null && pendingTimestamp > 0 &&
                System.currentTimeMillis() - pendingTimestamp > CACHE_TTL_MS) {
            LOG.info("预缓存超时，清除");
            pendingLines = null;
            pendingTimestamp = 0;
        }

        if (!active || lines == null) return;
        if (playing) {
            long now = System.nanoTime();
            long deltaNs = now - lastTickNano;
            if (deltaNs > 0 && deltaNs < 500_000_000L)
                currentMs += deltaNs / 1_000_000L;
            lastTickNano = now;

            if (!lines.isEmpty() && currentMs > lines.get(lines.size() - 1).timeMs() + 5000)
                active = false;
        }
    }

    // ==================================================================
    //  渲染（只显示一排歌词）
    // ==================================================================

    public static void render(GuiGraphicsExtractor ctx) {
        LyricsConfig cfg = LyricsConfig.get();
        if (!cfg.showLyrics) return;
        if (!active || lines == null || lines.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        if (font == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        long nowMs = System.currentTimeMillis();

        int cur = LyricsParser.currentIndex(lines, currentMs);
        String curr = cur >= 0 ? lines.get(cur).text() : "";

        int boxH = font.lineHeight + 16;
        int boxW = Math.min(sw - 40, cfg.maxWidth);
        int boxX = (sw - boxW) / 2;
        int boxY = sh - cfg.bottomOffset - boxH;

        ctx.fill(boxX - 4, boxY - 4, boxX + boxW + 4, boxY + boxH + 4, cfg.bgColor);

        if (!curr.isEmpty()) {
            int color = cfg.rgbMode
                    ? LyricsConfig.rgbColor(nowMs, cfg.rgbSpeed)
                    : cfg.textColor;
            drawCentered(ctx, font, curr, sw / 2, boxY + 8, color, boxW - 20);
        }

        if (!songDisplayName.isEmpty()) {
            int titleClr = cfg.titleRgbMode
                    ? LyricsConfig.rgbColor(nowMs, cfg.titleRgbSpeed)
                    : cfg.titleColor;
            String sn = font.plainSubstrByWidth(songDisplayName, boxW - 20);
            ctx.text(font, sn, sw / 2 - font.width(sn) / 2,
                    boxY - font.lineHeight - 4, titleClr, false);
        }
    }

    // ==================================================================
    //  内部
    // ==================================================================

    private static void drawCentered(GuiGraphicsExtractor ctx, Font font,
                                      String text, int cx, int y, int color, int maxW) {
        if (text.isEmpty()) return;
        if (font.width(text) <= maxW) {
            ctx.text(font, text, cx - font.width(text) / 2, y, color, false);
        } else {
            StringBuilder line = new StringBuilder();
            int ly = y;
            for (String word : text.split(" ")) {
                String trial = line.length() > 0 ? line + " " + word : word;
                if (font.width(trial) > maxW) {
                    ctx.text(font, line.toString(),
                            cx - font.width(line.toString()) / 2, ly, color, false);
                    ly += font.lineHeight + 1;
                    line = new StringBuilder(word);
                } else {
                    if (line.length() > 0) line.append(' ');
                    line.append(word);
                }
            }
            if (line.length() > 0)
                ctx.text(font, line.toString(),
                        cx - font.width(line.toString()) / 2, ly, color, false);
        }
    }

    private static String cleanName(String s) {
        if (s == null) return "";
        return s.replaceAll("\\.(mp3|flac|wav|ogg|m4a|aac|wma|ape)\\b", "")
                .replaceAll("[(（](320|128|192|HQ|SQ|无损|高音质|标准|极高|臻品).*?[)）]", "")
                .replaceAll("\\[(320|128|192|HQ|SQ|无损|高音质|标准|极高|臻品).*?\\]", "")
                .trim();
    }
}
