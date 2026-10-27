# OpenBenches Android App

Displays benches from [OpenBenches API](https://openbenches.org/api/benches/) on OpenStreetMap using osmdroid.

## Features
- Fetches and displays all benches as pins
- Different pin colors for different types (demo: id mod 3)
- Error handling and Sentry integration
- Unit and UI tests with Compose
- Uses OpenStreetMap for map display

## Setup
1. Add your Sentry DSN in `MainActivity.kt` (replace the placeholder).
2. Build and run the app.

## Testing
- Run tests with `./gradlew test` and `./gradlew connectedAndroidTest`

## API
- [OpenBenches API](https://openbenches.org/api/benches/)
