package com.coloryr.allmusic.lyrics.mixin;

import com.coloryr.allmusic.lyrics.LyricsDisplay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 Gui.extractRenderState() 的 TAIL，
 * 在所有 HUD 元素绘制完毕后渲染歌词覆盖层。
 *
 * 注：Minecraft 26.1 中 extractCameraOverlays 已重命名为 extractRenderState。
 */
@Mixin(Gui.class)
public class GuiLyricsMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        LyricsDisplay.render(graphics);
    }
}
