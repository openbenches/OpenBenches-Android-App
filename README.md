# OpenBenches Android App

Android client for [OpenBenches.org](https://openbenches.org/) — a crowd-sourced map of memorial benches.

**Package:** `org.openbenches`  
**Current version:** 1.25 (versionCode 20)  
**Min SDK:** 28 · **Target / Compile SDK:** 36  
**Licence:** [MIT](LICENSE)

## Features

- Interactive OpenStreetMap / satellite map (osmdroid) with viewport clustering
- Browse global benches (disk-cached for offline map use), search inscriptions, random & newest benches
- Bench details with photos, fullscreen zoom, and download to gallery
- Add / edit benches (Auth0 login optional for attribution; anonymous upload supported)
- Camera & gallery photos, EXIF GPS checks, 90° rotate before upload
- On-device inscription OCR (OpenCV + PaddleOCR / ONNX)
- Save benches for later upload; bulk upload with username confirmation
- Local To-Do list (long-press map) with view-on-map, import/export JSON
- Contributors leaderboard, account details, dark mode
- About, Terms, Support, and Colophon screens

## Project structure

```
Open Benches/
├── app/                 # Main Android application (Jetpack Compose)
├── ppocr-sdk/           # Vendored PaddleOCR Android ONNX SDK + models
├── gradle/              # Version catalog & wrapper
├── LICENSE
└── README.md
```

Application source lives under `app/src/main/java/org/openbenches/`:

| Package | Role |
|---------|------|
| `ui/` | Screens (map, add/edit bench, details, pending uploads, etc.) |
| `data/` | API clients, Auth0 JWT helpers, local stores (pending uploads, to-do, cache) |
| `util/` | Location, OCR, image URLs, photo rotate |
| `ui/theme/` | Brand colours, Material 3 theme, shared components |

## Requirements

- Android Studio (recent stable) or JDK 17+ for the Gradle wrapper
- Device or emulator with Google Play Services (fused location)
- Network access to `https://openbenches.org/` for live data and uploads

## Build & run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Or open the project in Android Studio and Run on a device/emulator.

### Release (Play Store)

```bash
./gradlew :app:bundleRelease
# Optional helpers for Play Console uploads:
./gradlew :app:copyReleaseMappingForPlay
./gradlew :app:copyReleaseNativeDebugSymbolsForPlay
```

## Configuration

Auth0 values are injected via `buildConfigField` and manifest placeholders in `app/build.gradle.kts`.

Optional overrides in `gradle.properties` (local or CI — do not commit secrets):

```properties
AUTH0_DOMAIN=your-tenant.eu.auth0.com
AUTH0_CLIENT_ID=your_native_client_id
AUTH0_SCHEME=openbenches
```

Defaults target the production OpenBenches Auth0 tenant.  
**Do not embed an Auth0 client secret in the Android app.**

`local.properties` (SDK path) is gitignored.

## Testing

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## API & backend

| Endpoint | Use |
|----------|-----|
| https://openbenches.org/api/ | Benches, search, details, user benches |
| https://openbenches.org/upload | Multipart bench upload |
| https://openbenches.org/update | Multipart bench edit |
| https://openbenches.org/leaderboard | Contributors (parsed HTML) |

Uploads may be challenged by CDN/WAF bot protection. The app detects those HTML challenge pages and shows a clear user message instead of raw script/CSS.

## Inscription OCR

On-device OCR uses:

- **OpenCV** — photo preprocessing
- **`:ppocr-sdk`** — PaddleOCR PP-OCRv5 mobile ONNX models + Microsoft onnxruntime-android (16 KB page-size aligned)

See [`ppocr-sdk/README.md`](ppocr-sdk/README.md).

## Maps

- **osmdroid** with OpenStreetMap (Mapnik) and Esri World Imagery tiles
- Custom viewport clustering (`ViewportBenchClusterer`)
- Play Services Location for GPS / follow-me

## Related

- End-user walkthrough (screens, buttons, features): [`docs/README.md`](docs/README.md)
- Website & data project: [openbenches/openbenches.org](https://github.com/openbenches/openbenches.org)
- Live site: https://openbenches.org/

## Licence

This app is released under the [MIT Licence](LICENSE).  
`ppocr-sdk` is vendored from [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) under Apache-2.0.
