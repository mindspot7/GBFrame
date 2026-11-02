# GBSat and GBCast dependency license audit

Date: 2026-11-02  
Scope: Android release runtime dependency graphs and ARM64 Android native build inputs in the local checkouts. This is a technical inventory, not legal advice or a certification for store publication.

## Evidence inspected

- Gradle `releaseRuntimeClasspath` reports for GBSat, GBCast, and GBCast's GBFrame module.
- Existing release APK contents for packaged native libraries and `assets/licenses`.
- GBFrame, GBSat, and GBCast license declarations and About-page license entries.
- Android CMake source/link configuration and the configured ARM64 build tree.

The local dependency reports are `gbsat-gradle-deps.txt`, `gbcast-gradle-deps.txt`, and `gbcast-gbframe-deps.txt` in the audit workspace. APKs inspected were the local release APKs present on 2026-11-02; this does not establish that GitHub artifacts are identical.

## GBSat

### Main license and compatibility

GBSat and Ymir are GPL-3.0. GBFrame is GPL-2.0-or-later and GBSat uses it under GPLv3. Apache-2.0 components can be combined with GPLv3, subject to preserving their license and any NOTICE material.

### Android runtime dependencies

The resolved graph includes AndroidX/Jetpack Compose, Kotlin standard library, Apache Commons Compress 1.28.0 and its Commons Codec/IO/Lang dependencies, Junrar 8.1.2, and Google Play Billing 9.1.0 with Google transport, encoders, and Play Services transitive dependencies. AndroidX, Kotlin, and Apache Commons are Apache-2.0. Junrar uses the UnRAR license. Billing and its Play Services graph identify the Android Software Development Kit License in artifact metadata; do not describe those artifacts as Apache-2.0 or as open source based only on Maven coordinates.

Junrar's terms permit use and redistribution to handle RAR archives, while prohibiting use to recreate the proprietary RAR compression algorithm. The apps use it to extract texture packs. Preserve the license and restriction notice.

### Native dependencies built into the APK

The Android library build links Ymir, rcheevos, cereal, mio, fmt, concurrentqueue, xxHash, libchdr and its compression dependencies, stb, and dr_libs. The checked-in notices identify rcheevos as MIT; mio/fmt/stb/dr_libs provide permissive license choices; concurrentqueue, xxHash, and libchdr require their BSD-style notices. The Android package also contains `libc++_shared.so` and an AndroidX graphics library.

### Existing APK notices

The inspected APK contains the GPLv3 text, rcheevos MIT, and the bundled cheat database's CC BY-SA 4.0 notice. It does not contain the full set of third-party license texts and attribution notices for the Android and native dependencies above.

## GBCast

### Main license and compatibility

The Flycast production source inspected for the Android target declares GPL version 2 or, at the recipient's option, any later version. The local GBCast About text and `LICENSE` currently present GPLv2. The scan found GPL-2.0-only declarations in xxHash test sources; those are not part of the Android release target. xBRZ, which is compiled into the core, is GPLv3. FreeType is compiled for Android and its project offers the FreeType License (FTL), which is compatible with GPLv3 but not GPLv2. Apache-2.0 dependencies are also compatible with GPLv3, not GPLv2.

This makes GPLv3 the apparent compatible GPL version for the combined open-source components, provided the precise Flycast source revision and all included source notices are verified. Do not update the GBCast's displayed or repository license until the Billing SDK terms below have been assessed.

### Android runtime dependencies

GBCast resolves the GBFrame Android dependencies above and additionally Apache Commons Lang 3.18.0, Apache HttpClient 5.0.3 with HttpCore, and SLF4J Android 1.7.35. Their source projects use Apache-2.0 or MIT licenses. Google Play Billing 9.1.0 and its Play Services/transport graph are also present through GBFrame.

### Native dependencies built into the APK

The configured ARM64 Android target compiles or links Breakpad, xxHash, GLM, glslang, libchdr plus zlib/zstd/LZMA dependencies, Oboe, Boost.Nowide, miniupnpc, libzip, libjuice, tinygettext, libelf, FreeType, Vulkan-Headers, Vulkan Memory Allocator, libadrenotools, Android Game SDK/Swappy, PicoTCP, rcheevos, and xBRZ. Material licenses include BSD-style, MIT, Boost-1.0, Apache-2.0, MPL-2.0, FTL, GPL-2.0-or-later/GPL-3.0, and public-domain components. The exact per-file license notices in each dependency source tree remain authoritative; some projects contain multiple licenses and subcomponent notices.

### Billing SDK publication review

The resolved Maven POM for `com.android.billingclient:billing:9.1.0` explicitly labels it `Android Software Development Kit License`, not Apache-2.0. The library is distributed in the APK. The Android SDK agreement restricts use of the SDK to development for compatible Android implementations and separately says open-source SDK components are governed by their own open-source licenses. Whether this binary can be combined and distributed as part of a GPLv3-covered APK is not established by the POM or by this inventory. Obtain a licensing determination or written permission/exception before claiming the complete GBCast APK is GPLv3-compatible. The same question applies to its transitive Play Services artifacts.

### Existing APK notices

The inspected APK contains the GPLv2 text, rcheevos MIT, and the bundled cheat database's CC BY-SA 4.0 notice. It does not contain a full third-party notice set for the Android runtime libraries or the native dependencies above.

## Publication actions

1. Complete legal review of Google Play Billing and transitive Play Services under the proposed GPLv3 distribution of GBCast. Keep the current license label unchanged until resolved.
2. Generate per-app third-party notices from the exact release dependency graphs and native source revisions. Include full license texts and required NOTICE/attribution material, not only license names or URLs.
3. Expose the notices from each app's About page. The shared `AboutPage` already accepts a list of license documents; each app must supply its own dependency notice asset.
4. Re-run this inventory whenever Gradle or native submodule dependencies change, and inspect the final release APK rather than relying only on source-tree license files.

## Primary references

- GNU license compatibility: https://www.gnu.org/licenses/license-compatibility.en.html
- Android SDK terms: https://developer.android.com/studio/terms
- Google Play Billing integration: https://developer.android.com/google/play/billing/integrate
- Junrar license: https://github.com/junrar/junrar/blob/master/LICENSE
- Flycast source: https://github.com/flyinghead/flycast
- FreeType licenses: https://gitlab.freedesktop.org/freetype/freetype/-/blob/master/docs/FTL.TXT
- Oboe license: https://github.com/google/oboe/blob/main/LICENSE
