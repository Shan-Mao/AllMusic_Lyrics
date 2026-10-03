package com.coloryr.allmusic.lyrics.mixin;

import com.coloryr.allmusic.lyrics.LyricsDisplay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 {@link Hud#extractRenderState} 的 TAIL，
 * 在所有原版 HUD 元素（热键栏、聊天、Boss 条等）绘制完毕后渲染歌词覆盖层。
 *
 * 版本说明:
 *   26.1.x —— HUD 渲染位于 {@code Gui.extractRenderState(GuiGraphicsExtractor, DeltaTracker)}
 *   26.2   —— Minecraft 将 HUD 逻辑抽到新的 {@code net.minecraft.client.gui.Hud} 类，
 *             渲染入口变为 {@code Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)}；
 *             {@code Gui.extractRenderState(DeltaTracker, boolean, boolean)} 不再接收绘图上下文。
 *   因此本模组的 HUD 注入点由 {@code Gui} 迁移到 {@code Hud}，仅支持 26.2+。
 */
@Mixin(Hud.class)
public class HudLyricsMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                             CallbackInfo ci) {
        LyricsDisplay.render(graphics);
    }
}
