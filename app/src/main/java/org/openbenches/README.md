# Application source (`org.openbenches`)

Kotlin / Jetpack Compose source for the OpenBenches Android app.

For setup, build, Auth0 config, API notes, and licence, see the project root
`README.md`.

## Layout

| Path | Contents |
|------|----------|
| `MainActivity.kt` | Navigation shell, About screen, map chrome, menus |
| `OpenBenchesApplication.kt` | Application class, Coil image loader |
| `ui/` | Screens & map helpers (MapScreen, AddBench, EditBench, …) |
| `ui/theme/` | Colours, typography, shared Compose components |
| `data/` | Retrofit/API, Auth JWT, pending uploads, to-do store, caches |
| `util/` | Location, OCR, weserv image URLs, photo rotate |
