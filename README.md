# LinkVault

**Save useful links, organize them with notes, and find them again fast.**

LinkVault is a local-first Android app for collecting links from browsers, social apps, chats, and anywhere Android sharing works. It keeps saved URLs, notes, categories, and visual organization in one focused place so useful content does not get lost in messages, browser tabs, or temporary notes.

## Highlights

- **Fast link capture** — add links manually or share text/URLs into LinkVault from other Android apps.
- **Notes-first organization** — attach short notes, choose categories, and edit saved items quickly.
- **Visual categories** — create categories with built-in icons or custom images from your gallery.
- **Search everywhere** — search saved links from the main list or inside a category.
- **Per-note sharing** — share any saved note/link back to other apps.
- **CSV import and export** — back up your vault or move data with `Link,Note,Category` CSV files.
- **Vietnamese and English UI** — switch language directly in Settings.
- **Personal themes** — choose Light, Dark, or System mode and six color styles: Denim Cool, Forest Jade, Blossom Rose, Peach Amber, Lavender Mist, and Teal Breeze.
- **Update check** — check the latest public GitHub Release from Settings and open the APK download page when a newer version is available.

## App Screens

| Links | Categories | Settings |
| --- | --- | --- |
| <img src="docs/screenshots/Links.jpg" alt="Links screen" width="260" /> | <img src="docs/screenshots/Categories.jpg" alt="Categories screen" width="260" /> | <img src="docs/screenshots/Settings.jpg" alt="Settings screen" width="260" /> |

## Main Flows

### Links

Save a URL with a note and category, then open, edit, delete, or share it from the list. The add/edit popup is designed for mobile keyboards and keeps category selection accessible while typing.

### Categories

Create visual categories, choose a localized default icon or crop a custom gallery image, and reorder categories by drag and drop. Category detail screens include their own search and the same link actions as the main list.

### Settings

Switch language, appearance mode, and color theme. Import or export CSV files, open the user guide, log out, or check for a newer GitHub Release.

## Data and Privacy

LinkVault stores data locally on the device using Room. Each signed-in Google account gets its own local vault. CSV import/export is user initiated, and generated APKs, keystores, `.env` files, and other sensitive artifacts should stay out of git.

## Built With

- Kotlin
- Jetpack Compose
- Material 3
- Room local database
- Kotlin Coroutines and Flow
- Google account picker
- OkHttp for public GitHub Release checks

## Development

Open the project in Android Studio, sync Gradle dependencies, then run the `app` configuration on an emulator or Android device.

Common commands:

| Command | Description |
| --- | --- |
| `gradle :app:assembleDebug` | Build a debug APK |
| `gradle :app:testDebugUnitTest` | Run local unit tests |
| `gradle :app:assembleRelease` | Build the signed release APK when release signing env vars are configured |

## Current App Details

- App name: **LinkVault**
- Current release: **0.6**
- Minimum SDK: **24**
- Target SDK: **36**
