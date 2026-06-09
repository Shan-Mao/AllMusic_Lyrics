package com.coloryr.allmusic.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词解析器。
 *
 * 支持标准 LRC 格式:
 *   [mm:ss.xx]歌词文字
 *   [mm:ss]歌词文字
 *
 * 单行可拥有多个时间标签:
 *   [01:23.45][02:34.56]重复歌词
 */
public final class LyricsParser {

    /** 匹配 [mm:ss.xx] 或 [mm:ss] 时间标签 */
    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]");

    /** 匹配词级时间标签 <mm:ss.xx> */
    private static final Pattern WORD_TAG = Pattern.compile(
            "<\\d{1,3}:\\d{1,2}(?:\\.\\d{1,3})?>");

    private LyricsParser() {}

    /**
     * 将 LRC 文本解析为按时间排序的 LrcLine 列表。
     *
     * @param lrcText 原始 LRC 歌词文本
     * @return 排序后的歌词行列表，解析失败则返回空列表
     */
    public static List<LrcLine> parse(String lrcText) {
        if (lrcText == null || lrcText.isBlank()) {
            return Collections.emptyList();
        }

        List<LrcLine> result = new ArrayList<>();

        for (String raw : lrcText.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // 跳过元数据标签: [ti:...], [ar:...], [al:...], [by:...], [offset:...]
            if (line.matches("^\\[[a-zA-Z]+:.*\\]$")) continue;

            // 提取所有时间标签
            Matcher m = TIMESTAMP.matcher(line);
            List<Long> times = new ArrayList<>();
            int lastEnd = 0;
            while (m.find()) {
                times.add(toMs(m.group(1), m.group(2), m.group(3)));
                lastEnd = m.end();
            }

            if (times.isEmpty()) continue;

            // 时间标签后面的文本
            String text = line.substring(lastEnd).trim();
            text = WORD_TAG.matcher(text).replaceAll("").trim();
            if (text.isEmpty()) continue;

            for (long t : times) {
                result.add(new LrcLine(t, text));
            }
        }

        Collections.sort(result, (a, b) -> Long.compare(a.timeMs(), b.timeMs()));
        return result;
    }

    /**
     * 在给定播放时间下，找到应该显示的歌词行索引。
     * 返回最后一个 timeMs <= currentMs 的索引，若当前时间早于所有行则返回 -1。
     */
    public static int currentIndex(List<LrcLine> lines, long currentMs) {
        int idx = -1;
        for (int i = 0; i < lines.size() && lines.get(i).timeMs() <= currentMs; i++) {
            idx = i;
        }
        return idx;
    }

    public static long toMs(String min, String sec, String cs) {
        int m = Integer.parseInt(min);
        int s = Integer.parseInt(sec);
        int c = 0;
        if (cs != null && !cs.isEmpty()) {
            c = Integer.parseInt(cs);
            if (cs.length() == 2) c *= 10; // 百分秒 → 毫秒
        }
        return (m * 60L + s) * 1000L + c;
    }
}
