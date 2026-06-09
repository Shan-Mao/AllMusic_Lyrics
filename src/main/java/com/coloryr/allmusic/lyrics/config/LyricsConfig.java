package com.coloryr.allmusic.lyrics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 歌词 HUD 配置。
 * 保存为 config/allmusic_lyrics.json。
 */
public class LyricsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("allmusic_lyrics.json").toFile();

    // ---- 歌词显示 ----

    public boolean showLyrics = true;
    public int maxWidth = 380;
    public int bottomOffset = 72;
    public int bgColor = 0x80_000000;

    // ---- 歌词文字颜色 ----

    /** 是否启用 RGB 模式（覆盖 textColor） */
    public boolean rgbMode = true;

    /** RGB 颜色切换间隔（秒） */
    public int rgbSpeed = 2;

    /** 歌词文字颜色 (ARGB)，rgbMode=false 时使用 */
    public int textColor = 0xFF_FFFFFF;

    // ---- 歌名颜色 ----

    /** 是否启用歌名 RGB 模式 */
    public boolean titleRgbMode = false;

    /** 歌名 RGB 切换间隔（秒） */
    public int titleRgbSpeed = 3;

    /** 歌名颜色 (ARGB)，titleRgbMode=false 时使用 */
    public int titleColor = 0xFF_CCCCCC;

    // ---- RGB 预设色板 ----

    public static final int[] RGB_PRESETS = {
            0xFF_FF5555, // 红
            0xFF_FFAA00, // 橙
            0xFF_FFFF55, // 黄
            0xFF_55FF55, // 绿
            0xFF_55FFFF, // 青
            0xFF_5555FF, // 蓝
            0xFF_FF55FF, // 紫
    };

    // ---- 单例 ----

    private static LyricsConfig instance;

    public static LyricsConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        if (FILE.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
                instance = GSON.fromJson(r, LyricsConfig.class);
            } catch (Exception e) {
                instance = new LyricsConfig();
            }
        } else {
            instance = new LyricsConfig();
        }
        save();
    }

    public static void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, w);
        } catch (Exception ignored) {}
    }

    /** 根据当前时间计算 RGB 循环颜色 */
    public static int rgbColor(long nowMs, int speedSec) {
        int idx = (int) ((nowMs / (speedSec * 1000L)) % RGB_PRESETS.length);
        return RGB_PRESETS[idx];
    }
}
