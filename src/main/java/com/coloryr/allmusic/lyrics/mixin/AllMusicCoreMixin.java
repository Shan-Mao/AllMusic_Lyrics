package com.coloryr.allmusic.lyrics.mixin;

import com.coloryr.allmusic.client.core.AllMusicCore;
import com.coloryr.allmusic.codec.CommandType;
import com.coloryr.allmusic.lyrics.LyricsDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 后备方案：通过 packDo 拦截播放/停止/清除命令。
 * 主要歌词获取通过聊天消息监听完成。
 */
@Mixin(AllMusicCore.class)
public class AllMusicCoreMixin {

    @Inject(method = "packDo", at = @At("TAIL"))
    private static void onPackDo(CommandType type, String data, int data1, CallbackInfo ci) {
        if (type == CommandType.INFO) {
            // 后备：如果消息拦截失败，用 packDo 传过来的歌名搜索
            LyricsDisplay.onNowPlaying(data);
        } else if (type == CommandType.PLAY) {
            LyricsDisplay.onPlay();
        } else if (type == CommandType.STOP) {
            LyricsDisplay.onStop();
        } else if (type == CommandType.CLEAR) {
            LyricsDisplay.onClear();
        }
    }
}
