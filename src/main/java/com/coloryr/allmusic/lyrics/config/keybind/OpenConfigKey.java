package com.coloryr.allmusic.lyrics.config.keybind;

import com.coloryr.allmusic.lyrics.config.LyricsConfig;
import com.coloryr.allmusic.lyrics.config.LyricsConfigScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 打开歌词设置界面的快捷键，默认 Z。
 * 支持任意组合键（如 I+L、Ctrl+Z 等）。
 */
public final class OpenConfigKey {

    private static boolean wasDown;

    private OpenConfigKey() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            wasDown = false;
            return;
        }

        int[] keys = LyricsConfig.get().configKeys;
        if (keys == null || keys.length == 0) return;

        boolean allDown = true;
        for (int k : keys) {
            if (GLFW.glfwGetKey(mc.getWindow().handle(), k) != GLFW.GLFW_PRESS) {
                allDown = false;
                break;
            }
        }

        if (allDown && !wasDown) {
            mc.setScreen(new LyricsConfigScreen(null));
        }
        wasDown = allDown;
    }

    /** 格式化按键名称 */
    public static String keyNames(int[] keys) {
        if (keys == null || keys.length == 0) return "无";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) sb.append('+');
            sb.append(rawName(keys[i]));
        }
        return sb.toString();
    }

    /** 单键名称 */
    public static String rawName(int code) {
        String name = GLFW.glfwGetKeyName(code, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase();
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE -> "空格";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "Alt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_ENTER -> "回车";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_BACKSPACE -> "退格";
            case GLFW.GLFW_KEY_DELETE -> "Del";
            case GLFW.GLFW_KEY_UP -> "↑"; case GLFW.GLFW_KEY_DOWN -> "↓";
            case GLFW.GLFW_KEY_LEFT -> "←"; case GLFW.GLFW_KEY_RIGHT -> "→";
            case GLFW.GLFW_KEY_F1 -> "F1"; case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3"; case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5"; case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7"; case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9"; case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11"; case GLFW.GLFW_KEY_F12 -> "F12";
            default -> String.valueOf((char) code).toUpperCase();
        };
    }
}
