package com.coloryr.allmusic.lyrics.mixin;

import com.coloryr.allmusic.client.core.AllMusicCore;
import com.coloryr.allmusic.codec.MusicPack;
import com.coloryr.allmusic.lyrics.LyricsDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 后备方案：通过 {@code AllMusicCore.packDo(MusicPack)} 拦截播放/停止/清除指令与歌曲信息。
 * 主要歌词获取仍然通过聊天消息监听完成。
 *
 * API 变更说明:
 *   前置模组 3.7.4 的签名为 {@code packDo(CommandType type, String data, int data1)}
 *   前置模组 4.1.8 起改为 {@code packDo(MusicPack pack)}，命令类型从 {@code pack.type} 读取，
 *   数据载荷则按类型分派到 {@code StringMusicPack / IntMusicPack / LyricMusicPack} 等子类。
 */
@Mixin(AllMusicCore.class)
public class AllMusicCoreMixin {

    @Inject(method = "packDo", at = @At("TAIL"))
    private static void onPackDo(MusicPack pack, CallbackInfo ci) {
        if (pack == null || pack.type == null) return;

        switch (pack.type) {
            // 歌曲信息（"歌名 | 歌手"）→ 兜底激活歌词显示
            case INFO -> {
                if (pack instanceof MusicPack.StringMusicPack s
                        && s.data != null && !s.data.isBlank()) {
                    LyricsDisplay.onSongInfo(s.data);
                }
            }
            // 服务器直接下发 LRC 歌词（4.x 新增）→ 优先使用，比自行搜索更精准
            case LYRIC -> {
                if (pack instanceof MusicPack.LyricMusicPack l
                        && l.lyric != null && !l.lyric.isBlank()) {
                    LyricsDisplay.onServerLyric(l.lyric, l.tlyric);
                }
            }
            case PLAY -> LyricsDisplay.onPlay();
            case STOP -> LyricsDisplay.onStop();
            case CLEAR -> LyricsDisplay.onClear();
            default -> { /* 其余包类型无需处理 */ }
        }
    }
}
