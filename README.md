# AllMusic Lyrics

<p align="right"><a href="更新日志.md">📋 更新日志 →</a></p>

[![Modrinth](https://img.shields.io/badge/Modrinth-allmusic__lyrics-00AF5C?logo=modrinth)](https://modrinth.com/project/allmusic_lyrics)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?logo=gnu)](LICENSE)
[![Bilibili](https://img.shields.io/badge/Bilibili-shanmao-FB7299?logo=bilibili)](https://space.bilibili.com/543353261)

**AllMusic Client 歌词附属模组** — 自动获取网易云 / LRCLIB 同步歌词并显示在游戏 HUD 上。

## 作者

| 作者 | 主页 |
|------|------|
| **shanmao** | https://modrinth.com/user/B_shanmao |
| Color_yr | AllMusic Client 前置 |
| Claude | 开发协助 |

## 前置模组

本模组是 [AllMusic Client](https://github.com/Coloryr/AllMusic_Client) 的附属模组，需要先安装：

- **AllMusic Client** `>= 4.1.8`
- **Minecraft** `>= 26.2`
- **Fabric Loader** `>= 0.19.3`
- **Fabric API**
- **Java** `>= 25`

> ⚠️ 从 1.5.0 起本模组**仅支持 Minecraft 26.2 及以上**。
> 26.2 将 HUD 渲染整体迁移到了新的 `Hud` 类，注入点与 26.1.x 不兼容。
> 如需 26.1.x，请使用 1.4.0。

## 安装

1. 下载 `AllMusic_Lyrics-1.5.0-fabric-26.2.jar`
2. 放入 Minecraft 的 `mods/` 文件夹
3. 确保 `mods/` 中已有 AllMusic Client 4.1.8 及以上
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

### 首选链路（服务器直发）

前置模组 4.x 会通过 `LYRIC` 数据包直接下发 LRC 歌词，比本地搜索更精准：

```
服务器下发 MusicPack.LYRIC
    │
    │  含 lyric / tlyric，优先取翻译歌词
    ▼
LyricsParser.parse → lines → 激活 HUD 渲染
```

### 主链路（歌曲 ID 直取）

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

如果聊天消息监听全部失败，通过 Mixin 注入 `AllMusicCore.packDo(MusicPack)` 作为最后兜底：

```
AllMusicCore.packDo(MusicPack)
    │
    ├─ INFO  → StringMusicPack.data（"歌名 | 歌手"）→ 激活 + 搜索流程
    ├─ LYRIC → LyricMusicPack.lyric                 → 直接显示
    ├─ PLAY  → 播放时钟启动
    ├─ STOP  → 播放时钟暂停
    └─ CLEAR → 清空全部状态
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
[AllMusicLyrics] 服务器下发歌词: 42 行
[AllMusicLyrics] packDo 歌曲信息: 歌名 | 歌手
```

## 构建

```bash
./gradlew build
```

需要 **JDK 25 及以上**（26.2 与 Loom 1.18 的硬性要求）。
`gradle.properties` 中的 `org.gradle.java.home` 已指向本机 JDK；如路径不同请自行修改。

构建前还需自备前置模组 jar（**未随仓库提供**，已被 `.gitignore` 排除）：

```
libs/[fabric-26.2]AllMusic_Client-4.1.8.jar
```

从 [AllMusic Client 发布页](https://modrinth.com/mod/allmusic_client)下载对应文件放入 `libs/` 即可，
文件名需与 `build.gradle` 中的 `files('libs/...')` 一致。

### Mixin 注入点校验

```bash
./gradlew verifyMixins
```

该任务会静态解析编译后的字节码，逐条确认每个 `@Mixin` 目标类与 `@Inject` 方法
在 Minecraft 与前置模组中真实存在。26.x 这类破坏性 API 改动（HUD 迁移、
`packDo` 签名变更）都会在此被提前发现，而不是等玩家启动时崩溃。

需要本机有 `python`，或用 `-PpythonExe=<路径>` 指定解释器。

## 源码

- 本模组：https://modrinth.com/project/allmusic_lyrics
- 前置模组源码：https://github.com/Coloryr/AllMusic_Client

## 许可证

GPL-3.0
