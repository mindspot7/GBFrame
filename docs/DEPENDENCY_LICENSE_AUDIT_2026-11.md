# GBSat and GBCast dependency license audit

Date: 2026-11-02  
Scope: local Android release runtime dependency graphs and ARM64 native build inputs. This is a technical inventory, not legal advice or a certification for store publication.

## License decision

- **GBFrame:** GPL-2.0-or-later. The owner authorized relicensing the GBFrame project code under these terms. The former GPLv3 license text remains in `LICENSE-GPL-3.0.txt` as historical reference.
- **GBSat:** GPLv3. Ymir is GPLv3; GBFrame is used under GPLv3. The app now exposes a consolidated third-party notice asset in About.
- **GBCast:** GPLv3 for the project source distribution. The inspected Flycast production sources allow GPL-2.0-or-later, so they may be distributed under GPLv3. xBRZ is GPLv3; FreeType is built using its FTL option; Apache-2.0 dependencies are compatible with GPLv3. A GPL-2.0-only declaration was found in xxHash test code, which is not part of the Android target. Preserve per-file notices and each dependency's own terms.

GPLv3 is compatible with Apache-2.0; GPLv2-only is not. GPL-2.0-or-later source can be used under GPLv3. This explains why the app source declarations are aligned to GPLv3 while Flycast files retain their upstream headers.

## Google Play SDK terms — unresolved publication review

Both apps package Google Play Billing 9.1.0 and transitive Google Play Services/transport artifacts through GBFrame. Billing's resolved Maven metadata identifies the Android Software Development Kit License; those artifacts are not represented as open source in the app notices. The notices link to Google's current SDK terms.

The Billing AAR/DEX is bundled into the APK. This audit does not establish whether those binaries qualify for GPL's system-library exception or whether Google's SDK terms permit the combined distribution under GPLv3. GNU guidance says nonfree libraries outside the system-library exception require permission to link/combine with GPL software. A legal determination or written permission/exception is still required before describing the complete APK as wholly GPL-compatible for publication. Keep the SDK terms separate from the GPL declarations and do not remove Google's notices.

## Dependency notices added

Each app's About page now opens a `THIRD-PARTY-NOTICES.txt` asset. The assets include full notices/licenses extracted from local resolved release archives, native dependency license texts, and references to separately displayed GPL, MIT, CC BY-SA, and UnRAR notices. The current assets cover:

- **GBSat Android runtime:** AndroidX/Compose, Kotlin, Apache Commons Compress/Codec/IO/Lang, Junrar, Billing and Play Services metadata; Apache NOTICE files were extracted from the resolved archives.
- **GBSat native target:** Ymir, GBFrame, rcheevos, mio, fmt, concurrentqueue, xxHash, libchdr, miniz, zstd 1.5.7, LZMA SDK, stb, dr_libs, and cereal where built into the configured target.
- **GBCast Android runtime:** AndroidX/Compose, Kotlin, Apache Commons, Apache HttpComponents, SLF4J, Junrar, Billing and Play Services metadata; archive LICENSE/NOTICE texts were extracted from the resolved runtime graph.
- **GBCast Android native target:** Flycast, xBRZ, Breakpad, xxHash, GLM, glslang, libchdr plus zlib/zstd/LZMA, Oboe, Boost.Nowide, miniupnpc, libzip, libjuice, tinygettext/tinycmmc, libelf, FreeType (FTL), Vulkan-Headers, Vulkan Memory Allocator, libadrenotools including linker namespace bypass, Android Game SDK/Swappy, PicoTCP, libunwind, VIXL, Dear ImGui/ImPlot, libretro-common, and generated GLAD sources.

The bundled cheat databases keep their separate CC BY-SA 4.0 license assets. Junrar's UnRAR terms allow use to handle RAR archives and prohibit recreating the proprietary RAR compression algorithm; the app only extracts archives.

## Local evidence and remaining checks

- Gradle reports: `gbsat-gradle-deps.txt`, `gbcast-gradle-deps.txt`, and `gbcast-gbframe-deps.txt` in the audit workspace. Runtime archive LICENSE/NOTICE documents were read from the exact versions in those reports.
- Native input inventory: app CMake files and checked-in dependency license headers/files in the local Android target.
- The previous APK inspection predates these notice assets. A new APK build and package inspection are required to confirm that the final signed APK contains the updated About assets and has no packaging exclusions.
- This audit does not prove the corresponding-source offer, Play Console declaration, or Google SDK/GPL compatibility. Match the source commit to every published binary and complete the Billing legal review before claiming publication readiness.

## Primary references

- [GNU license compatibility](https://www.gnu.org/licenses/license-compatibility.en.html)
- [GNU GPL FAQ: system library and incompatible-library exceptions](https://www.gnu.org/licenses/gpl-faq.en.html)
- [Android SDK terms](https://developer.android.com/studio/terms)
- [Google Play Billing integration](https://developer.android.com/google/play/billing/integrate)
- [Junrar/UnRAR license](https://github.com/junrar/junrar/blob/master/LICENSE)
- [Flycast source](https://github.com/flyinghead/flycast)
- [FreeType licenses](https://gitlab.freedesktop.org/freetype/freetype/-/blob/master/docs/FTL.TXT)
