package com.coloryr.allmusic.lyrics.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 歌词 HUD 配置界面（分页）。
 * 第1页：歌词显示  |  第2页：歌单设置
 */
public class LyricsConfigScreen extends Screen {

    private static final int BTN_W = 150;
    private static final int BTN_H = 20;
    private static final int PAD = 25;

    private final Screen parent;
    private int page; // 0=歌词 1=歌单

    public LyricsConfigScreen(Screen parent) {
        super(Component.literal("AllMusic Lyrics 配置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (page == 0) initLyricsPage();
        else initPlaylistPage();

        // 底部分页按钮
        int bx = width / 2 - 100;
        int by = height - 30;
        addRenderableWidget(Button.builder(
                        Component.literal(page == 0 ? "下一页 →" : "← 上一页"), btn -> {
                    page = page == 0 ? 1 : 0;
                    init();
                }).pos(bx, by).size(200, BTN_H).build());
    }

    private void initLyricsPage() {
        LyricsConfig cfg = LyricsConfig.get();
        int cx = width / 2, y = 30;

        addRenderableWidget(CycleButton.onOffBuilder(cfg.showLyrics)
                .create(cx - BTN_W / 2, y, BTN_W, BTN_H,
                        Component.literal("显示歌词"),
                        (btn, on) -> { cfg.showLyrics = on; LyricsConfig.save(); }));
        y += PAD;

        addRenderableWidget(Button.builder(
                        Component.literal("歌词RGB: " + onOff(cfg.rgbMode)), btn -> {
                    cfg.rgbMode = !cfg.rgbMode;
                    btn.setMessage(Component.literal("歌词RGB: " + onOff(cfg.rgbMode)));
                    LyricsConfig.save(); init();
                }).pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PAD;

        if (cfg.rgbMode) {
            addRenderableWidget(Button.builder(
                            Component.literal("歌词RGB速度: " + cfg.rgbSpeed + "s"), btn -> {
                        cfg.rgbSpeed = cfg.rgbSpeed >= 6 ? 1 : cfg.rgbSpeed + 1;
                        btn.setMessage(Component.literal("歌词RGB速度: " + cfg.rgbSpeed + "s"));
                        LyricsConfig.save();
                    }).pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
            y += PAD;
        }

        addRenderableWidget(Button.builder(
                        Component.literal("歌名RGB: " + onOff(cfg.titleRgbMode)), btn -> {
                    cfg.titleRgbMode = !cfg.titleRgbMode;
                    btn.setMessage(Component.literal("歌名RGB: " + onOff(cfg.titleRgbMode)));
                    LyricsConfig.save(); init();
                }).pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PAD;

        if (cfg.titleRgbMode) {
            addRenderableWidget(Button.builder(
                            Component.literal("歌名RGB速度: " + cfg.titleRgbSpeed + "s"), btn -> {
                        cfg.titleRgbSpeed = cfg.titleRgbSpeed >= 8 ? 1 : cfg.titleRgbSpeed + 1;
                        btn.setMessage(Component.literal("歌名RGB速度: " + cfg.titleRgbSpeed + "s"));
                        LyricsConfig.save();
                    }).pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
            y += PAD;
        }

        addRenderableWidget(Button.builder(
                        Component.literal(bgLabel(cfg.bgColor)), btn -> {
                    cfg.bgColor = nextBgAlpha(cfg.bgColor);
                    btn.setMessage(Component.literal(bgLabel(cfg.bgColor)));
                    LyricsConfig.save();
                }).pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PAD;

        addRenderableWidget(Button.builder(
                        Component.literal("歌词宽度: " + cfg.maxWidth), btn -> {
                    cfg.maxWidth = cfg.maxWidth >= 500 ? 200 : cfg.maxWidth + 60;
                    btn.setMessage(Component.literal("歌词宽度: " + cfg.maxWidth));
                    LyricsConfig.save();
                }).pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PAD;

        addRenderableWidget(Button.builder(
                        Component.literal("底部距离: " + cfg.bottomOffset), btn -> {
                    cfg.bottomOffset = cfg.bottomOffset >= 140 ? 30 : cfg.bottomOffset + 10;
                    btn.setMessage(Component.literal("底部距离: " + cfg.bottomOffset));
                    LyricsConfig.save();
                }).pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
    }

    private void initPlaylistPage() {
        LyricsConfig cfg = LyricsConfig.get();
        int cx = width / 2, y = 30;

        addRenderableWidget(Button.builder(
                        Component.literal("每次发送: " + cfg.playlistSendCount + " 首"), btn -> {
                    cfg.playlistSendCount = cfg.playlistSendCount >= 20 ? 1 : cfg.playlistSendCount + 1;
                    btn.setMessage(Component.literal("每次发送: " + cfg.playlistSendCount + " 首"));
                    LyricsConfig.save();
                }).pos(cx - BTN_W / 2, y).size(BTN_W, BTN_H).build());
        y += PAD + 5;

        addRenderableWidget(new MultiLineTextWidget(
                Component.literal(
                        "§7使用 /musiclist <网易云歌单链接>\n" +
                        "§7模组会自动获取歌单歌曲\n" +
                        "§7并逐一发送 /music 指令点歌\n" +
                        "§7剩余歌曲缓存在内存中"),
                font).setMaxWidth(280).setCentered(true));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        String t = page == 0 ? "歌词设置" : "歌单设置";
        ctx.centeredText(font, Component.literal(t), width / 2, 10, 0xFF_AAAAAA);
        ctx.centeredText(font, title, width / 2, -10, 0xFF_FFFFFF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // ---- 工具 ----

    private static String onOff(boolean b) { return b ? "§a开" : "§c关"; }

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
