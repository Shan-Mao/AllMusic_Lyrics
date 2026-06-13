package com.coloryr.allmusic.lyrics.config;

import com.coloryr.allmusic.lyrics.config.keybind.OpenConfigKey;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class LyricsConfigScreen extends Screen {

    private static final int GAP = 24, BW = 200;
    private final Screen parent; private int page;
    private boolean waitingForKey; private int waitTicks;
    private int activeSlider = -1, dragSlider = -1, sldY;
    private EditBox bgBox, wBox, dBox;

    public LyricsConfigScreen(Screen parent) {
        super(Component.literal("AllMusic Lyrics 配置"));
        this.parent = parent;
    }

    @Override protected void init() {
        clearWidgets();
        switch (page) { case 0 -> lyric(); case 1 -> key(); case 2 -> playlist(); }
        // 底栏
        String[] next = {"快捷键 →", "歌单 →", "歌词 →"};
        addRenderableWidget(Button.builder(Component.literal(next[page]), b -> { page = (page + 1) % 3; init(); })
                .pos(width / 2 - 100, height - 30).size(BW, 20).build());
    }

    // ================================================================
    //  歌词页
    // ================================================================

    private int cy;

    private void lyric() {
        LyricsConfig c = LyricsConfig.get(); cy = 32; int cx = width / 2, bx = cx - BW / 2;

        addCycle(cy, "显示歌词", c.showLyrics, v -> { c.showLyrics = v; save(); });
        addCycle(cy += GAP, "歌词 RGB", c.rgbMode, v -> { c.rgbMode = v; save(); init(); });
        if (c.rgbMode) { cy += GAP; addStep(cy, "RGB速度", c.rgbSpeed, 1, 6, "s", v -> { c.rgbSpeed = v; save(); init(); }); }
        cy += GAP;
        addColorBtn(cy, "歌词颜色", c.textColor, !c.rgbMode, "textColor", "歌词");

        cy += GAP + 4;
        addCycle(cy, "歌名 RGB", c.titleRgbMode, v -> { c.titleRgbMode = v; save(); init(); });
        if (c.titleRgbMode) { cy += GAP; addStep(cy, "RGB速度", c.titleRgbSpeed, 1, 8, "s", v -> { c.titleRgbSpeed = v; save(); init(); }); }
        cy += GAP;
        addColorBtn(cy, "歌名颜色", c.titleColor, !c.titleRgbMode, "titleColor", "歌名");

        cy += GAP + 4;
        sldY = cy;
        bgBox = addSlider(cy, "背景透明度", (c.bgColor >>> 24) * 100 / 255, 0, 100, "%", v -> {
            c.bgColor = (v * 255 / 100) << 24; save(); });
        wBox = addSlider(cy += GAP, "歌词宽度", c.maxWidth, 100, 600, "", v -> { c.maxWidth = v; save(); });
        dBox = addSlider(cy += GAP, "底部距离", c.bottomOffset, 10, 200, "", v -> { c.bottomOffset = v; save(); });
    }

    private void addCycle(int y, String label, boolean val, java.util.function.Consumer<Boolean> cb) {
        CycleButton<Boolean> btn = CycleButton.onOffBuilder(val)
                .create(width / 2 - BW / 2, y, BW, 20, Component.literal(label),
                        (b, v) -> { cb.accept(v); });
        addRenderableWidget(btn);
    }

    private void addStep(int y, String label, int val, int min, int max, String unit, java.util.function.IntConsumer cb) {
        int bx = width / 2 - BW / 2;
        var b = Button.builder(Component.literal(label + ": §e" + val + unit), btn -> {
            int nv = val >= max ? min : val + 1; cb.accept(nv);
        }).pos(bx, y).size(BW, 20).build();
        addRenderableWidget(b);
    }

    private EditBox addSlider(int y, String label, int val, int min, int max, String unit, java.util.function.IntConsumer cb) {
        int bx = width / 2 - BW / 2;
        var lw = new MultiLineTextWidget(Component.literal(label), font);
        lw.setX(bx); lw.setY(y + 2); lw.setMaxWidth(80);
        addRenderableWidget(lw);
        // 输入框
        int ix = bx + BW - 42;
        EditBox eb = new EditBox(font, ix, y, 40, 16, Component.literal(""));
        eb.setValue(val + unit); eb.setResponder(s -> {
            try { int v = Integer.parseInt(s.replace(unit, "")); v = clamp(v, min, max); cb.accept(v); }
            catch (Exception ignored) {}
        }); addRenderableWidget(eb);
        return eb;
    }

    private void addColorBtn(int y, String label, int color, boolean active, String key, String title) {
        int bx = width / 2 - BW / 2;
        String hex = String.format("#%06X", color & 0xFFFFFF);
        var c = Component.literal(label + " | ").append(
                Component.literal(hex).withColor(color & 0xFFFFFF));
        var b = Button.builder(c, btn -> {
            if (minecraft != null) minecraft.setScreen(new ColorEditorScreen(this, key, title));
        }).pos(bx, y).size(BW, 20).build();
        b.active = active; addRenderableWidget(b);
    }

    // ================================================================
    //  快捷键页
    // ================================================================

    private void key() {
        LyricsConfig c = LyricsConfig.get(); int y = 35, bx = width / 2 - BW / 2;
        String kn = OpenConfigKey.keyNames(c.configKeys);
        addRenderableWidget(Button.builder(Component.literal("快捷键: §e" + kn), btn -> {})
                .pos(bx, y).size(BW, 20).build()); y += GAP;
        addRenderableWidget(Button.builder(Component.literal("§6修改按键..."), btn -> {
            waitingForKey = true; waitTicks = 0; init();
        }).pos(bx, y).size(BW, 20).build()); y += GAP;
        addRenderableWidget(Button.builder(Component.literal("恢复默认 (Z)"), btn -> {
            c.configKeys = new int[]{90}; save(); init();
        }).pos(bx, y).size(BW, 20).build());
    }

    @Override public void tick() {
        if (waitingForKey && minecraft != null) {
            if (waitTicks < 5) { waitTicks++; return; }
            long h = minecraft.getWindow().handle();
            java.util.List<Integer> pressed = new java.util.ArrayList<>();
            for (int k = 32; k <= 350; k++)
                if (GLFW.glfwGetKey(h, k) == GLFW.GLFW_PRESS) pressed.add(k);
            if (!pressed.isEmpty()) {
                int[] a = new int[pressed.size()];
                for (int i = 0; i < a.length; i++) a[i] = pressed.get(i);
                LyricsConfig.get().configKeys = a; save(); waitingForKey = false; init();
            }
        }
    }

    // ================================================================
    //  歌单页
    // ================================================================

    private void playlist() {
        LyricsConfig c = LyricsConfig.get(); int y = 35, bx = width / 2 - BW / 2;
        addRenderableWidget(Button.builder(
                        Component.literal("每次发送: §e" + c.playlistSendCount + " 首"), btn -> {
                    c.playlistSendCount = c.playlistSendCount >= 20 ? 1 : c.playlistSendCount + 1; save(); init();
                }).pos(bx, y).size(BW, 20).build()); y += GAP + 5;
        var mw = new MultiLineTextWidget(Component.literal("§7/musiclist <网易云歌单链接>\n§7自动获取歌单并逐首点歌"), font);
        mw.setMaxWidth(260); mw.setX(bx); mw.setY(y); addRenderableWidget(mw);
    }

    // ================================================================
    //  Render
    // ================================================================

    @Override public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float d) {
        super.extractRenderState(ctx, mx, my, d);
        String[] ts = {"歌词设置", "快捷键设置", "歌单设置"};
        ctx.centeredText(font, Component.literal(ts[page]), width / 2, 12, 0xFFFFFFFF);
    }

    @Override public void onClose() { waitingForKey = false; if (minecraft != null) minecraft.setScreen(parent); }

    // ================================================================
    //  Helpers
    // ================================================================

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private void save() { LyricsConfig.save(); }
}
