package com.coloryr.allmusic.lyrics.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu 集成入口。
 * 安装 Mod Menu 后，模组列表会出现齿轮配置按钮。
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LyricsConfigScreen::new;
    }
}
