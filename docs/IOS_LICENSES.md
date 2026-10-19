# iOS runtime notice provenance
`NOTICE` and the bundled `iosApp/iosApp/ThirdPartyNotices.txt` are synchronized.
They retain the solver MIT notice, add the full Apache 2.0 license and Skiko
NOTICE, and reproduce Skia dependency notices from the versions pinned by
Skiko 0.9.37.4 (`skiko/gradle.properties`: Skia `m138-80d088a-1`).
The pinned iOS release package contains Skia, ICU, HarfBuzz, PNG/JPEG/WebP,
Wuffs, Expat, zlib, DNG and piex static archives. The notices intentionally
include optional components even when final linking strips unused code.
- [Skiko license](https://github.com/JetBrains/skiko/blob/v0.9.37.4/LICENSE)
- [Skiko notice](https://github.com/JetBrains/skiko/blob/v0.9.37.4/NOTICE)
- [Skia license](https://github.com/JetBrains/skia/blob/80d088a/LICENSE)
- [Skia dependency revisions](https://github.com/JetBrains/skia/blob/80d088a/DEPS)
- brotli: `https://skia.googlesource.com/external/github.com/google/brotli.git` at `6d03dfbedda1615c4cba1211f8d81735575209c8`
- dng_sdk: `https://android.googlesource.com/platform/external/dng_sdk.git` at `dbe0a676450d9b8c71bf00688bb306409b779e90`
- expat: `https://chromium.googlesource.com/external/github.com/libexpat/libexpat.git` at `8e49998f003d693213b538ef765814c7d21abada`
- harfbuzz: `https://chromium.googlesource.com/external/github.com/harfbuzz/harfbuzz.git` at `08b52ae2e44931eef163dbad71697f911fadc323`
- icu: `https://chromium.googlesource.com/chromium/deps/icu.git` at `364118a1d9da24bb5b770ac3d762ac144d6da5a4`
- libjpeg-turbo: `https://chromium.googlesource.com/chromium/deps/libjpeg_turbo.git` at `e14cbfaa85529d47f9f55b0f104a579c1061f9ad`
- libpng: `https://skia.googlesource.com/third_party/libpng.git` at `ed217e3e601d8e462f7fd1e04bed43ac42212429`
- libwebp: `https://chromium.googlesource.com/webm/libwebp.git` at `845d5476a866141ba35ac133f856fa62f0b7445f`
- piex: `https://android.googlesource.com/platform/external/piex.git` at `bb217acdca1cc0c16b704669dd6f91a1b509c406`
- wuffs: `https://skia.googlesource.com/external/github.com/google/wuffs-mirror-release-c.git` at `e3f919ccfe3ef542cfc983a82146070258fb57f8`
- zlib: `https://chromium.googlesource.com/chromium/src/third_party/zlib` at `646b7f569718921d7d4b5b8e22572ff6c76f2596`

Downloaded license/notice files are license texts, not executable code. Recheck
this inventory when upgrading runtime or rendering dependencies.
