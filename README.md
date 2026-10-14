# Future Code Auto Video Cutter (Android, offline)

**Status (October 14, 2026):** Android debug APK **successfully compiled** and unit tests **passed** using GitHub Actions. The app has **not yet been tested on a physical Android phone**. Debug APK download: [successful Build #38536915613](https://github.com/futurecode064-cyber/future-code-auto-video-cutter/actions/runs/38536915613).

## What it does

1. **Auto Split:** select one local video with Android's file picker. Enter a duration in minutes (decimal values accepted), e.g. 5. A 2-hour video is divided into 24 MP4 files. The last part can be shorter. Segments cover the entire original timeline without intentionally omitting or overlapping intervals.
2. **Merge:** select at least two local videos, reorder with ↑/↓, delete unwanted choices with ×, and export one MP4 in the visible order.
3. **Exports:** H.264 MP4 video with AAC audio where supported, saved in `Movies/Future Code Auto Video Cutter` and viewable in Gallery. The app displays individual exported parts and offers an Open button.
4. **Device-local:** no login, API key, network requests, or `INTERNET` permission. Uses the Android system document picker instead of broad media permissions.
5. **Cancel and progress:** part and overall progress, cancellation, preservation of parts that were already saved. A partially saved output is removed if writing fails.

## Compatibility and limits

- Android **10 or later** (minSdk 29).
- Source format support and codec availability depend on the device and Android Media3 Transformer. Output is MP4; uncommon codecs, DRM-protected media, and defective files may fail.
- Trims are specified by timestamps; exact displayed duration may differ slightly because of source frame rate or encoded timestamps. Output may be re-encoded and large videos may take time.
- There is no arbitrary **video-duration** limit imposed by this app, but working storage and phone hardware limit practical file sizes. Extremely many output parts are capped at 10,000 to prevent accidental runaway jobs.
- **Keep the app open and screen awake for long exports.** This v1.0 does not yet implement an Android foreground service to continue exports if the activity is destroyed or the OS kills it.
- Export requires temporary storage for the part currently being produced (or for the full merged video), as well as space for the final saved file. Do not run on an almost-full phone.
- This app performs simple direct cuts and joins, **without** transitions or edits to the video content.
- Use your own, or authorized, videos.

## Build option A — Android Studio

1. Download and unzip the entire project, and open the **root `FutureCodeAutoVideoCutter` folder** in Android Studio.
2. Install Android SDK API 36 and Build Tools via SDK Manager; use JDK 17.
3. Gradle version **8.13** and Android Gradle Plugin **8.12.2** are specified/expected. If your Android Studio asks for a Gradle distribution, select Gradle 8.13. A Gradle Wrapper JAR is **not** bundled in this source ZIP.
4. Sync Gradle, then run `:app:assembleDebug` or choose **Build → Build APK(s)**.
5. Generated installation file: `app/build/outputs/apk/debug/app-debug.apk`.

## Build option B — GitHub Actions (easier if you have no SDK)

1. Create a **new** GitHub repository named `future-code-auto-video-cutter` (keep this separate from other Future Code projects).
2. Upload all files **inside** this project directory (including `.github/workflows/build-apk.yml`) to the repository's `main` branch. The ZIP itself must be extracted before upload, or upload using Git.
3. Open the repo's **Actions → Build Android APK → Run workflow** (push to `main` also triggers it).
4. After a successful run, open its **Artifacts** section and download `FutureCodeAutoVideoCutter-debug-apk` (ZIP with `app-debug.apk`).
5. The debug APK is for private testing and will require allowing installation from your chosen files app. It is **not** a production/release signing key or a Play Store release build.

The GitHub workflow will set up JDK 17, Android SDK 35, Gradle 8.13, run the JUnit split-interval tests, compile the app, and upload a debug APK.

## Development

Project contains a native Java `MainActivity`, pure-Java `ClipPlan` interval calculations, and AndroidX Media3 Transformer **1.11.1**. The system Storage Access Framework selects content URIs; generated MP4 files are exposed through Android MediaStore on Android 10+. No server is required to use the installed app.

**Testing:** A separate JVM verification (`ClipPlanSelfTest.java`) was executed when packaging this source and passed. That is **not** a substitute for running Gradle tests or installing and exercising the app on a real phone.

Android documentation: https://developer.android.com/media/media3/transformer/getting-started ; https://developer.android.com/media/media3/transformer/composition


## Delivery / GitHub limitation

The linked GitHub connector may be able to upload source files to an **existing** repository, but cannot create a brand-new repository. Create an **empty** repository under `futurecode064-cyber` first; then source files may be pushed using the linked GitHub connector. Never upload the source ZIP as a single file and expect Actions to compile it.

## Verified GitHub build (2026-10-14)

- GitHub Actions [Build Android APK #38536915613](https://github.com/futurecode064-cyber/future-code-auto-video-cutter/actions/runs/38536915613) — **success**.
- `:app:testDebugUnitTest` — **passed**.
- `:app:assembleDebug` — **passed**.
- Artifact: `FutureCodeAutoVideoCutter-debug-apk` (ZIP containing `app-debug.apk`).
- Uses Android SDK API 36, Android Gradle Plugin 8.12.2, Gradle 8.13, Java 17, Media3 1.11.1.
- Not verified on a physical Android device; testing with long clips and varied codecs is still needed.
