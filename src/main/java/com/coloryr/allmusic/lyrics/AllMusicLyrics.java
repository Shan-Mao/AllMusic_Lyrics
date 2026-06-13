package com.coloryr.allmusic.lyrics;

import com.coloryr.allmusic.lyrics.config.LyricsConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

/**
 * AllMusic Lyrics 附属模组 —— 客户端入口。
 */
public class AllMusicLyrics implements ClientModInitializer {

    public static final String MOD_ID = "allmusic_lyrics";
    public static final Logger LOGGER = LoggerFactory.getLogger("AllMusicLyrics");

    @Override
    public void onInitializeClient() {
        LOGGER.info("AllMusic Lyrics 初始化...");

        LyricsFetcher.init();
        PlaylistFetcher.init();

        // 注册 /musiclist 命令
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            // /musiclist <url>
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("musiclist")
                    .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("url", greedyString())
                            .executes(cmd -> {
                                String url = cmd.getArgument("url", String.class);
                                PlaylistFetcher.fetchFromMessage(url)
                                        .exceptionally(ex -> { LOGGER.error("歌单异常: {}", ex.toString()); return null; })
                                        .thenAccept(songs -> {
                                    if (songs != null && !songs.isEmpty()) {
                                        int count = LyricsConfig.get().playlistSendCount;
                                        PlaylistFetcher.sendSongs(count);
                                        if (PlaylistFetcher.remaining() > 0) {
                                            sendFeedback("已发送 " + count + " 首，剩余 " +
                                                    PlaylistFetcher.remaining() + " 首。再次 /musiclist 继续发送");
                                        }
                                    } else {
                                        sendFeedback("§c获取歌单失败，请检查链接");
                                    }
                                });
                                return 1;
                            })));

            // /musiclist（无参数 = 继续发送剩余）
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("musiclist")
                    .executes(cmd -> {
                        if (PlaylistFetcher.remaining() > 0) {
                            int count = LyricsConfig.get().playlistSendCount;
                            PlaylistFetcher.sendSongs(count);
                            if (PlaylistFetcher.remaining() > 0) {
                                sendFeedback("已发送 " + count + " 首，剩余 " +
                                        PlaylistFetcher.remaining() + " 首");
                            } else {
                                sendFeedback("歌单已全部发送完毕");
                            }
                        } else {
                            sendFeedback("§c没有缓存的歌单。请先用 /musiclist <网易云歌单链接>");
                        }
                        return 1;
                    }));
        });

        // 聊天消息监听
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.contains("[AllMusic")) {
                LOGGER.info("检测到: {}", text);
                if (text.contains("正在解析歌曲") || text.contains("music.163.com")) {
                    LyricsDisplay.onSongParsing(text);
                } else if (text.contains("正在播放")) {
                    LyricsDisplay.onNowPlaying(text);
                }
            }
        });

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

    private static void sendFeedback(String msg) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[AllMusicLyrics]§r " + msg));
        }
    }
}
