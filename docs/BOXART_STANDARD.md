# GB-family box-art sizing standard

Every box-art card in one system's library uses a frame sized to the dominant format of that system's box-art collection. GBFrame applies the same frame in Showcase, Grid, and All in One.

## Choose the ratio

1. Use the system's `Named_Boxarts` source configured for `Covers.init`.
2. Measure width ÷ height for distinct game titles. Prefer the full catalog; otherwise sample titles across alphabetical ranges and available regions. Avoid letting many revisions of one game count as many votes.
3. Group near-equal ratios (allow a few pixels of scan/canvas variance) and select the largest cluster. Record the selected ratio and source in the app's integration notes.
4. Pass the selected ratio explicitly as `frameAspectRatio` to `Covers.init`. GBFrame has no system-specific default; every consumer must provide its ratio.

The ratio is **width divided by height**. GBFrame decodes downloaded and user-selected covers into the selected frame, and its shared library layouts use that same ratio for cards, reflections, and 3D faces. Apps must not override those dimensions locally.

## Current reference profiles

- **Sega Saturn / GBSat:** keep `0.66f`, preserving the current library frame.
- **Sega Dreamcast / GBCast:** use `1.0f` (square). A check of 11 named images from Libretro's Dreamcast box-art set found dimensions from 512×504 through 512×514, including U.S., European, and Japanese scans. The dominant frame therefore changes from the previous 0.66 portrait frame to square.

Sources: [Libretro Sega - Saturn box art](https://thumbnails.libretro.com/Sega%20-%20Saturn/Named_Boxarts/) and [Libretro Sega - Dreamcast box art](https://thumbnails.libretro.com/Sega%20-%20Dreamcast/Named_Boxarts/).

## GBFrame API

```kotlin
Covers.init(
    dataDir = appDataDir,
    system = "Sega - Dreamcast",
    frameAspectRatio = 1.0f,
)
```

The `frameAspectRatio` argument is required. Never omit it: each consuming app owns its system profile and must pass the measured dominant ratio before exposing the library.
