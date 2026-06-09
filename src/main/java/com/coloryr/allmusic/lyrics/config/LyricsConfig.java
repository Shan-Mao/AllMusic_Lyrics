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

    // ---- 配置项 ----

    /** 是否显示歌词 HUD */
    public boolean showLyrics = true;

    /** 文字颜色 (ARGB) */
    public int textColor = 0xFF_FFFFFF;

    /** 背景颜色 (ARGB) */
    public int bgColor = 0x80_000000;

    /** 歌词框最大宽度 */
    public int maxWidth = 380;

    /** 歌词框距屏幕底部距离 */
    public int bottomOffset = 72;

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
}
