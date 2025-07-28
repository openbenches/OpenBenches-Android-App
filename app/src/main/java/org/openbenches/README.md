# OpenBenches Android App

Displays benches from [OpenBenches API](https://openbenches.org/api/benches/) on Google Maps.

## Features
- Fetches and displays all benches as pins
- Different pin colors for different types (demo: id mod 3)
- Error handling and Sentry integration
- Unit and UI tests with Compose

## Setup
1. Add your Sentry DSN in `MainActivity.kt` (replace the placeholder).
2. Ensure your Google Maps API key is in `AndroidManifest.xml` as a meta-data tag.
3. Build and run the app.

## Testing
- Run tests with `./gradlew test` and `./gradlew connectedAndroidTest`

## API
- [OpenBenches API](https://openbenches.org/api/benches/)
