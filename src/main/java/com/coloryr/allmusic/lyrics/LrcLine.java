package com.coloryr.allmusic.lyrics;

/**
 * 单行歌词数据。
 * timeMs 是相对于歌曲开始的毫秒时间戳。
 */
public record LrcLine(long timeMs, String text) {

    public String formatTime() {
        long min = timeMs / 60000;
        long sec = (timeMs % 60000) / 1000;
        long cs  = (timeMs % 1000) / 10;
        return String.format("[%02d:%02d.%02d]", min, sec, cs);
    }

    @Override
    public String toString() {
        return formatTime() + text;
    }
}
