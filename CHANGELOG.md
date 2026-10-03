# Changelog

> Versioning rule: an explicitly requested version number wins; otherwise each
> cumulative update increments to the next `1.x.0`.

## 1.5.0 — 2026-10-03

### ⚠️ Breaking changes
- **Ported to Minecraft 26.2** (from 26.1.2)
  - **26.1.x is no longer supported.** In 26.2 the whole HUD rendering pipeline moved
    to the new `Hud` class, so the injection point is incompatible and a single build
    cannot target both versions.
  - Prerequisite raised to **AllMusic Client >= 4.1.8**

### ✨ Added
- **Server-provided lyrics take priority.** AllMusic Client 4.x sends lyrics straight
  from the server via the new `LYRIC` packet, which is more accurate than local search.
  These are now preferred over self-fetched results.
- **Automated mixin injection-point verification** — new `gradlew verifyMixins` task.
  It statically parses the compiled bytecode and confirms every `@Mixin` target class
  and `@Inject` method really exists in Minecraft / the prerequisite mod, catching
  breaking API changes like the 26.x ones before the game ever starts.

### 🔧 Improved
- **HTTP layer rewritten** — dropped the Apache HttpClient5 dependency in favour of the
  JDK's built-in `java.net.http`.
  - AllMusic Client 4.1.8 relocates and embeds HttpClient5 under
    `com.coloryr.allmusic.libs.*`, so no usable external jar exists any more.
    This removes the dependency problem entirely.
  - Both fetchers now share one set of headers / timeouts / rate-limit retry logic.
- **Removed the `java.awt.Color` dependency** — the colour editor's HSB↔RGB conversion
  is now a self-contained pure-math implementation. The `java.desktop` module is not
  guaranteed to be available at Minecraft runtime, so the previous code was a latent
  hazard.
- Unified song-name handling: chat messages, broadcast info and the `packDo` fallback
  now share a single activation path.
- **Standardised release artifact naming** — the built jar is now
  `AllMusic_Lyrics-1.5.0-fabric-26.2.jar` (`mod-version-loader-mcversion`), matching
  the naming style of the bundled prerequisite
  (`[fabric-26.2]AllMusic_Client-4.1.8.jar`).
  - The version comes from `mod_version` in `gradle.properties` and stays in sync with
    `fabric.mod.json`, so it only has to be changed in one place.

### 🐛 Fixed
- **HUD injection no longer applied.** In 26.2 `Gui.extractRenderState` no longer
  receives the drawing context; we now inject into
  `Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`.
- **`packDo` fallback was completely dead.** AllMusic Client 4.1.8 changed the signature
  from `packDo(CommandType, String, int)` to `packDo(MusicPack)`, so the old injection
  could never apply. Rewritten to dispatch on the new `MusicPack` subclasses.
- **Wrong song-name parsing on the fallback path.** The `INFO` payload from `packDo` is
  `"title | artist"` with no `正在播放：` prefix, so the old regex matching failed
  silently. Added a direct entry point.
- **Config screens could not be opened.** 26.2 moved `Minecraft.setScreen(...)` and the
  `Minecraft.screen` field onto `Gui`; now uses `minecraft.gui.setScreen(...)` /
  `gui.screen()`.
- Removed the obsolete `GuiLyricsMixin` so it cannot conflict with the new
  `HudLyricsMixin`.

### 🧰 Toolchain

| Component | Before | After |
|-----------|--------|-------|
| Minecraft | 26.1.2 | **26.2** |
| Fabric Loom | 1.17.3 | **1.18.2** (requires Gradle ≥ 9.7) |
| Gradle | 9.5.0 | **9.8.0** |
| Fabric API | 0.151.0+26.1.2 | **0.161.0+26.2** |
| Fabric Loader | 0.18.3 | **0.19.3** |
| Mod Menu | 18.0.0-beta.1 | **20.0.3** |
| AllMusic Client | 3.7.4 | **4.1.8** |

## 1.4.0 — 2026-06-13

### ✨ Added
- **Full colour editor** — HSV/RGB picker + hue strip + sliders + HEX input + preview
- **Keybind system** — bind any key combination (e.g. `I+L`) right from the settings page
- **Settings reworked to vanilla layout** — full-width `CycleButton` toggles, colour
  buttons with live colour preview
- **Playlist** — button-based count, sliders/inputs for opacity, width and distance

### 🐛 Fixed
- Unified `ctx.fill` colour format to ARGB, added Alpha to `HSBtoRGB`
- Pre-render the 2D picker into a texture, rebuild it when the hue changes
- Colour button HEX now tinted live via `withColor`

## 1.3.1 — 2026-06-13

### ✨ Added
- **`/musiclist` pagination** — detect the total via `trackCount` and page through
  `offset` to pull every song in a playlist

### 🐛 Fixed
- Playlist API returned only the first 10 tracks → now fetches all
- `//music` double-slash when sending commands → fixed
- Multiple commands sent in the same tick were dropped → 600 ms spacing
- Crash when `topTrackIds` was `JsonNull` → safe null checks
- NetEase `-447` anti-scraping response → added cookie masquerading
- Same cookie handling added to the lyrics endpoint

## 1.3.0 — 2026-06-13

### ✨ Added
- **`/musiclist` command** — accepts a NetEase playlist link, fetches every song and
  queues them one by one
  - `/musiclist <netease playlist url>` fetches the playlist and sends the first N songs
  - `/musiclist` with no argument continues sending from the cache
  - Send count configurable in the settings screen (1–20)
- **Paged settings screen** — page 1 for lyrics, page 2 for playlists
- Pre-cache TTL extended to 20 minutes, reset automatically on game start

## 1.2.0 — 2026-06-09

### ✨ Added
- **RGB colouring** — independent rainbow cycling for lyrics and song title
  - 7-colour preset (red → orange → yellow → green → cyan → blue → magenta)
  - Separate toggles and speeds (1–8 s) for title and lyrics
  - Falls back to a static colour when RGB is off

### 🔧 Improved
- Cache lifecycle: pre-cache expires automatically after 20 minutes

## 1.1.0 — 2026-06-09

### ✨ Added
- **Mod Menu configuration screen** — live adjustments once Mod Menu is installed:
  - Show lyrics on/off
  - Text colour (6 presets)
  - Background opacity (25 %–100 %, or off)
  - Max lyric box width and bottom offset
  - Saved to `config/allmusic_lyrics.json`, applied instantly

### 🔧 Improved
- Lyrics now render on a single line (current line only)
- Lyrics are cached while parsing and only shown on playback, avoiding flicker on track change
- Preferred path fetches by song ID directly, skipping the search step

### 🐛 Fixed
- Minecraft 26.1 `Gui.extractCameraOverlays` → `extractRenderState`
- `Screen.render` → `extractRenderState`
- Mod Menu entry point now implements `ModMenuApi`

## 1.0.0 — 2026-06-09

### 🎉 Initial release
- Automatic NetEase Cloud Music / LRCLIB synced LRC lyrics
- Real-time scrolling lyrics on the in-game HUD
- Server POS time synchronisation
- Chat message listening (`正在解析歌曲[ID]` / `正在播放`)
- Client-side only, no server installation required
