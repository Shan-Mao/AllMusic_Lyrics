package com.coloryr.allmusic.lyrics.mixin;

import com.coloryr.allmusic.lyrics.LyricsDisplay;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 Minecraft.tick() 以驱动歌词播放时钟（~20Hz）。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        LyricsDisplay.tick();
    }
}
