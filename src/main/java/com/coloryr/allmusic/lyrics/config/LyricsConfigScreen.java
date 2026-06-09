package com.coloryr.allmusic.lyrics.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 歌词 HUD 配置界面。
 */
public class LyricsConfigScreen extends Screen {

    private static final int BTN_W = 150;
    private static final int BTN_H = 20;
    private static final int PADDING = 28;

    private final Screen parent;

    public LyricsConfigScreen(Screen parent) {
        super(Component.literal("AllMusic Lyrics 配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LyricsConfig cfg = LyricsConfig.get();
        int cx = width / 2;
        int y = 40;

        // 显示开关
        addRenderableWidget(CycleButton.onOffBuilder(cfg.showLyrics)
                .create(cx - BTN_W / 2, y, BTN_W, BTN_H,
                        Component.literal("显示歌词"),
                        (btn, on) -> { cfg.showLyrics = on; LyricsConfig.save(); }));
        y += PADDING;

        // 文字颜色
        addRenderableWidget(Button.builder(
                        Component.literal(presetName(cfg.textColor)), btn -> {
                    cfg.textColor = nextPreset(cfg.textColor);
                    btn.setMessage(Component.literal(presetName(cfg.textColor)));
                    LyricsConfig.save();
                })
                .pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PADDING;

        // 背景透明度
        addRenderableWidget(Button.builder(
                        Component.literal(bgLabel(cfg.bgColor)), btn -> {
                    cfg.bgColor = nextBgAlpha(cfg.bgColor);
                    btn.setMessage(Component.literal(bgLabel(cfg.bgColor)));
                    LyricsConfig.save();
                })
                .pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PADDING;

        // 歌词宽度
        addRenderableWidget(Button.builder(
                        Component.literal("歌词宽度: " + cfg.maxWidth), btn -> {
                    cfg.maxWidth = cfg.maxWidth >= 500 ? 200 : cfg.maxWidth + 60;
                    btn.setMessage(Component.literal("歌词宽度: " + cfg.maxWidth));
                    LyricsConfig.save();
                })
                .pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PADDING;

        // 底部距离
        addRenderableWidget(Button.builder(
                        Component.literal("底部距离: " + cfg.bottomOffset), btn -> {
                    cfg.bottomOffset = cfg.bottomOffset >= 140 ? 30 : cfg.bottomOffset + 10;
                    btn.setMessage(Component.literal("底部距离: " + cfg.bottomOffset));
                    LyricsConfig.save();
                })
                .pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PADDING + 10;

        // 完成
        addRenderableWidget(Button.builder(Component.literal("完成"), btn -> onClose())
                .pos(cx - 50, y).size(100, BTN_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.centeredText(font, title, width / 2, 15, 0xFF_FFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ---- 颜色预设 ----

    private static final int[] PRESETS = {
            0xFF_FFFFFF, 0xFF_55FFFF, 0xFF_55FF55,
            0xFF_FFFF55, 0xFF_FF55FF, 0xFF_FF5555
    };
    private static final String[] PRESET_NAMES = {
            "白色", "青色", "绿色", "黄色", "品红色", "红色"
    };

    private static String presetName(int color) {
        for (int i = 0; i < PRESETS.length; i++)
            if (PRESETS[i] == color) return "文字颜色: " + PRESET_NAMES[i];
        return "文字颜色: 自定义";
    }

    private static int nextPreset(int cur) {
        for (int i = 0; i < PRESETS.length; i++)
            if (PRESETS[i] == cur) return PRESETS[(i + 1) % PRESETS.length];
        return PRESETS[0];
    }

    private static String bgLabel(int color) {
        int a = (color >> 24) & 0xFF;
        return "背景透明度: " + (a * 100 / 255) + "%";
    }

    private static int nextBgAlpha(int cur) {
        int[] alphas = {0x40, 0x60, 0x80, 0xA0, 0xC0, 0x00};
        int curA = (cur >> 24) & 0xFF;
        for (int i = 0; i < alphas.length; i++)
            if (alphas[i] == curA)
                return (alphas[(i + 1) % alphas.length] << 24) | 0x000000;
        return 0x80_000000;
    }
}
