package com.coloryr.allmusic.lyrics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AllMusic Lyrics 附属模组 —— 客户端入口。
 *
 * 监听 AllMusic Client 的聊天消息：
 * - "正在解析歌曲[ID]"  → 后台静默获取歌词，缓存起来
 * - "正在播放：歌名"    → 激活显示已缓存的歌词
 */
public class AllMusicLyrics implements ClientModInitializer {

    public static final String MOD_ID = "allmusic_lyrics";
    public static final Logger LOGGER = LoggerFactory.getLogger("AllMusicLyrics");

    @Override
    public void onInitializeClient() {
        LOGGER.info("AllMusic Lyrics 初始化...");

        LyricsFetcher.init();

        // 监听系统消息（AllMusic 的提示消息在这里）
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.contains("[AllMusic")) {
                LOGGER.info("检测到: {}", text);

                if (text.contains("正在解析歌曲") || text.contains("music.163.com")) {
                    // 解析阶段 → 后台获取歌词，不显示
                    LyricsDisplay.onSongParsing(text);
                } else if (text.contains("正在播放")) {
                    // 播放阶段 → 激活歌词显示
                    LyricsDisplay.onNowPlaying(text);
                }
            }
        });

        // 普通聊天监听（/music 命令可能在 CHAT 中出现）
        ClientReceiveMessageEvents.CHAT.register((message, signedMsg, sender, params, time) -> {
            String text = message.getString();
            if (text.contains("[AllMusic")) {
                LOGGER.info("检测到聊天: {}", text);
                if (text.contains("正在解析歌曲") || text.contains("music.163.com")) {
                    LyricsDisplay.onSongParsing(text);
                } else if (text.contains("正在播放")) {
                    LyricsDisplay.onNowPlaying(text);
                }
            }
        });

        LOGGER.info("AllMusic Lyrics 初始化完成！");
    }
}
