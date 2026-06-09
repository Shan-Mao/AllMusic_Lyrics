# AllMusic Lyrics

[![Modrinth](https://img.shields.io/badge/Modrinth-allmusic__lyrics-00AF5C?logo=modrinth)](https://modrinth.com/project/allmusic_lyrics)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)

**AllMusic Client 歌词附属模组** — 自动获取网易云 / LRCLIB 同步歌词并显示在游戏 HUD 上。

## 作者

| 作者 | 主页 |
|------|------|
| **shanmao** | https://modrinth.com/user/shanmao |
| Color_yr | AllMusic Client 前置 |
| Claude | 开发协助 |

## 前置模组

本模组是 [AllMusic Client](https://github.com/Coloryr/AllMusic_Client) 的附属模组，需要先安装：

- **AllMusic Client** `>= 3.7.4`
- **Minecraft** `26.1.x`
- **Fabric Loader** `>= 0.18.3`
- **Fabric API**

## 安装

1. 下载 `AllMusic_Lyrics-1.0.0.jar`
2. 放入 Minecraft 的 `mods/` 文件夹
3. 确保 `mods/` 中已有 AllMusic Client
4. 启动游戏

## 使用方法

1. 在游戏中使用 `/music <网易云链接>` 点歌，例如：
   ```
   /music https://music.163.com/song?id=123456
   ```

2. 歌曲被添加时**不会**显示歌词（避免切歌闪烁）

3. 歌曲真正开始播放时（出现 `正在播放：歌名`），歌词自动出现在屏幕下方热键栏上方

4. 歌词自动同步滚动，歌曲结束后自动消失

## 歌词获取链路

```
玩家输入 /music <网易云链接>
    │
    ▼
[AllMusic3] 正在解析歌曲[123456]     ← 客户端聊天消息监听捕获
    │
    │  提取网易云歌曲 ID
    ▼
https://music.163.com/api/song/lyric?id=123456  ← 直接获取 LRC 歌词
    │
    │  解析 [mm:ss.xx] 格式
    ▼
歌词后台缓存（pendingLines）         ← 不显示，等待播放
    │
    ▼
[AllMusic3] 正在播放：歌名 | 歌手    ← 客户端聊天消息监听捕获
    │
    │  pendingLines → lines → 激活 HUD 渲染
    ▼
┌─────────────────────────────┐
│      歌名 | 歌手             │  ← 灰色小字
│      当前歌词行              │  ← 白色大字，同步滚动
└─────────────────────────────┘
```

### 后备链路（歌名搜索）

如果"正在解析歌曲"消息未被捕获，则在"正在播放"时走歌名搜索：

```
正在播放：歌名 | 歌手
    │
    ├─ ① 网易云搜索 "歌名 歌手"  → 获取歌曲ID → 获取LRC
    ├─ ② 网易云搜索 "歌名"       → 获取歌曲ID → 获取LRC（仅歌名）
    └─ ③ LRCLIB API 搜索        → 获取LRC（国际后备）
```

### 第三方路径（packDo 后备）

如果聊天消息监听全部失败，通过 Mixin 注入 `AllMusicCore.packDo(INFO, songName)` 作为最后兜底：

```
AllMusicCore.packDo(INFO, "歌名 | 歌手")
    │
    └─ 同上搜索流程
```

## 关键正则匹配

| 触发消息 | 正则 | 动作 |
|---------|------|------|
| `正在解析歌曲[123456]` | `正在解析歌曲[：:]?\s*(\d+)` | 直接 ID 获取 LRC |
| `/music https://music.163.com/song?id=123456` | `music\.163\.com.*[/#]song\?id=(\d+)` | 直接 ID 获取 LRC |
| `正在播放：歌名 \| 歌手 by: xxx` | `正在播放[：:]\s*(.+?)(?:\s+by:.*)?$` | 激活歌词显示 |

## 调试日志

在 Minecraft 日志中搜索 `AllMusicLyrics` 可查看完整运行状态：

```
[AllMusicLyrics] 后台获取歌词 ID=123456
[AllMusicLyrics] 歌词已缓存: 35 行 (等待播放)
[AllMusicLyrics] 正在播放: 歌名 | 歌手
[AllMusicLyrics] 歌词显示已激活: 歌名 | 歌手
```

## 源码

- 本模组：https://modrinth.com/project/allmusic_lyrics
- 前置模组源码：https://github.com/Coloryr/AllMusic_Client

## 许可证

GPL-3.0
