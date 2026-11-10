# OpenBenches Android — User Guide

A walkthrough of the OpenBenches app: what you see on screen, what each control does, and how the main features work.

OpenBenches maps memorial benches from [openbenches.org](https://openbenches.org/). You can browse the map, search inscriptions, open bench details, add or edit benches, save drafts for later upload, and keep a personal To-Do list of places to visit.

---

## First launch

1. Install and open the app.
2. If location permission has not been granted, you see a full-screen prompt explaining that location is needed to show your position on the map, with a **Grant Location Permission** button.
3. After permission is granted, the main **map screen** appears and the app loads the global bench catalogue (cached on device for offline map use).

Without location permission you cannot use the map. Other screens that need the internet (search, login, uploads, leaderboard) also require a network connection when you use them.

---

## The map screen (home)

This is the default view. Top to bottom:

### Header banner

A gold-style strip across the top with:

| Part | What it shows / does |
|------|----------------------|
| OpenBenches logo | Brand mark on the left |
| Title + status | **OpenBenches**, a line like “A map of *N* memorial benches”, and a status line under it (how many benches are drawn in the current view, search/To-Do filters, offline mode, or loading) |
| Account chip (when logged in) | Provider icon / avatar and username on the right |

On info screens (About, Terms, and similar), tapping the header returns you to the map.

### Map itself

- **Street map** (OpenStreetMap) or **satellite** (Esri World Imagery) tiles.
- **Bench markers** (individual benches) and **coloured cluster bubbles** when many benches are nearby and you are zoomed out (green → yellow → orange → red as the count grows; large counts may show as `1k`, etc.).
- **Your location** as a marker when GPS is available.
- **To-Do markers** for places you have long-pressed to save (see [To-Do list](#to-do-list)).

**Gestures**

| Gesture | Result |
|---------|--------|
| Pinch / double-tap | Zoom |
| Drag | Pan the map |
| Tap empty map | Closes any open bench popup |
| Tap a **cluster** | Zooms in to reveal benches inside it |
| Tap a **bench marker** once | Opens a popup with a short preview |
| Tap the same bench again (or use the popup action) | Opens full **bench details** |
| **Long-press** the map | Adds a **To-Do** pin at that location |
| Tap a **To-Do** pin | Asks whether to remove that pin |

While “follow my location” is active (after **Locate**), the map keeps recentering on you until you pan, zoom, or tap.

### Top-right controls

| Control | Action |
|---------|--------|
| **Layers** (stack icon) | Menu to switch **Street map (OpenStreetMap)** or **Satellite (Esri)** — choice is remembered |
| **Offline** chip | Appears when the device is offline (or using cached bench data); reminds you that live features may be limited |

### Bottom toolbar (gold map buttons)

Buttons sit along the bottom. In portrait they spread across the width; in landscape they group near the centre.

| Button | Label | What it does |
|--------|-------|--------------|
| Gear | **Menu** | Opens the main menu (About, theme, login, To-Do tools, etc.) |
| Magnifying glass | **Search** | Opens **Search Inscriptions** |
| Search-off icon | **Clear** | Clears an active search or To-Do map filter. If nothing is filtered, a slash appears on the icon and tapping shows “No search filter is active” |
| Plus | **Add** | Opens **Add Bench** |
| Crosshair | **Locate** | Only shown when location permission is granted. Centers the map on you and turns on short follow-me until you move the map yourself |
| Upload | **Upload** | Opens **Saved benches to upload** when you have drafts. If none are saved, a slash appears on the icon and tapping shows “No benches saved for upload” |

---

## Main menu (gear)

| Item | What it does |
|------|--------------|
| **About** | Story of OpenBenches.org, how to help, social links, and your installed **app version** |
| **Dark mode** / **Light mode** | Toggles theme; preference is saved |
| **Login** | Opens Auth0 Universal Login (disabled while offline). Shown when you are logged out |
| **Logout** | Ends the Auth0 session and clears local login data (disabled while offline) |
| **Account details** | Username, provider, avatar, and related account info (when logged in) |
| **Terms of Service** | App / site terms |
| **Support OpenBenches** | Ways to support the project |
| **To-Do list** | Expands submenu — see [To-Do list](#to-do-list) |
| **Contributors Leaderboard** | Ranked contributors (needs network) |
| **Colophon** | Libraries and platforms the app is built with |

---

## Search Inscriptions

Opened from the **Search** button.

| Control | Action |
|---------|--------|
| Text field | Type words from a plaque / inscription |
| **Search** | Finds matching benches, filters the map to those results, and zooms to fit them. Status line shows something like “Search: *shown* of *matches* benches on map” |
| **Random Bench** | Opens details for a random bench ID (needs the global count and network) |
| **Newest 5 benches** | Filters the map to the five most recently added benches and zooms to fit |
| **Cancel** | Closes the dialog without changing the filter |

Use **Clear** on the bottom bar to remove a search (or To-Do) filter and show the normal map again.

Offline: Random / Newest / search against the live API will not work; cached benches may still appear on the map. Opening details while offline shows **Bench unavailable**.

---

## Bench details

Opened from a bench marker (second tap / popup) or from Random Bench.

| Control | Action |
|---------|--------|
| Back | Returns to the map |
| Pencil (**Edit**) | Opens **Edit Bench** — only when you are logged in |
| Link / copy | Copies the openbenches.org URL for that bench to the clipboard |
| Photo strip | Plaque, bench, view (and other) photos; tap one for fullscreen |
| Fullscreen: prev / next | Browse photos |
| Fullscreen: download | Saves the photo to your gallery |
| Fullscreen: close | Leaves fullscreen |
| Inscription & info | Text and metadata for the bench |
| Mini-map | Shows the bench location (same street/satellite style as the main map) |

---

## Add Bench

Opened from **Add**. Use this to photograph a memorial bench and send it to OpenBenches (or save it for later).

### Photos

- Horizontal row of photo slots. The first empty slot is always available so you can keep adding.
- Each filled slot has:
  - **Gallery** or **Camera** to set/replace the image
  - A **type** dropdown (e.g. Close-up of the inscription, Long shot of the bench, View from the bench, Panoramic view, VR 360, Video)
  - **Remove** to delete that photo
- Tap a photo to open **fullscreen**, where you can:
  - **Rotate** 90° clockwise (GPS EXIF is kept when present)
  - **Select area to read** — draw a region; on-device OCR (OpenCV + PaddleOCR) fills or appends the inscription field
  - Browse previous / next photos

The **first photo** is used for location: it must include GPS metadata (typical for camera shots with location on). Gallery picks may need **photo location** permission so EXIF GPS can be read. Missing GPS blocks upload with a clear dialog.

### Inscription

- Multi-line text field for the plaque wording.
- Clear (×) removes the text.
- OCR from fullscreen selection can fill this automatically.

### Location map

- Shows the proposed bench location (from the photo GPS / device location).
- Used as a visual check before upload.

### Upload / save

Top-right **Upload** runs validation, then asks:

| Choice | Result |
|--------|--------|
| **Upload now** | Sends the bench to openbenches.org (with progress). On success you see the new bench ID |
| **Save to upload later** | Stores the draft on the device (opens/updates the pending list) |

The confirm dialog shows the **username** that will be attributed (your Auth0 name if logged in, or the anonymous upload name the app uses when not logged in).

**Back** with unsaved changes asks **Discard bench?** — Exit or Cancel.

Login is **optional**: you can upload anonymously; signing in attributes the bench to your account.

---

## Edit Bench

From bench details when logged in. Similar to Add Bench:

- See existing photos; add new ones (camera/gallery), rotate in fullscreen, set types.
- Edit the inscription.
- Adjust location on the map when applicable.
- **Upload** submits an update to openbenches.org.
- Success refreshes the details screen.

---

## Saved benches to upload (Pending)

Opened from the bottom **Upload** button when at least one draft exists (also reachable after “save for later” from Add Bench).

| Control | Action |
|---------|--------|
| Back | Return to the map |
| **Upload all benches** | Confirms username, then uploads every saved draft (with progress) |
| Per row: **Edit** | Reopens Add Bench for that draft |
| Per row: **Delete** | Removes the draft |
| Per row: **Upload now** | Confirms username and uploads that one bench |

Successful uploads remove those drafts. Failures may show GPS-related messages or a notice if the site returns a bot-protection challenge page instead of a normal response.

---

## To-Do list

Personal pins for places you want to visit. Stored only on the device (not uploaded as benches).

### On the map

- **Long-press** to add a pin at that coordinate.
- Tap a pin → dialog with coordinates → **Delete** or **Keep**.

### Menu → To-Do list

| Item | Action |
|------|--------|
| **View To-Do list** | Filters the map like a search so only To-Do context is emphasised (status line shows To-Do counts). Empty list shows a snackbar |
| **Clear To-Do list** | Confirms, then deletes every pin |
| **Load To-Do list** | Import a JSON file and **append** new items |
| **Save To-Do list** | Export current pins as `openbenches-todo-list.json` (or the name you choose) |

**Clear** on the bottom bar also exits the To-Do map filter (same control as clearing search).

---

## Login & account

### Login

1. Menu → **Login**.
2. Read the short explanation of Auth0 Universal Login.
3. Providers: GitHub, Facebook, LinkedIn, X, WordPress, Discord, OpenStreetMap.
4. Tap the gold continue button → browser / Custom Tab → sign in → return to the app.

Your session is stored locally until logout or expiry.

### Account details

Shows profile picture (when available), display name, and verified-via provider. Back (or the header) returns to the map.

### Why log in?

- Attribute uploads and edits to your OpenBenches identity.
- Edit existing benches from the details screen.
- See account details and appear correctly on the contributors leaderboard over time.

Anonymous add/upload still works if you prefer not to sign in.

---

## Contributors Leaderboard

Menu → **Contributors Leaderboard** (online only).

- Scrollable list of contributors with avatars, names, and bench counts.
- Search/filter the list; clear the search field with ×.
- Tap a contributor (when their user id is available) to load **their benches** onto the map as a filter (similar to search), then return to explore those markers.

---

## About, Terms, Support, Colophon

All opened from the menu. Shared behaviours:

- Header banner (tap to go back to the map).
- Scrollable content.

| Screen | Content |
|--------|---------|
| **About** | Project background, contributors, how to help, social links, **App Version** |
| **Terms of Service** | Terms and related privacy notes (including Auth0) |
| **Support OpenBenches** | Support / donate information and links |
| **Colophon** | Platform, UI, maps, networking, OCR, and auth libraries with versions |

---

## Offline behaviour

| Feature | Offline |
|---------|---------|
| Map tiles / cached benches | May still show; **Offline** chip can appear |
| Open bench details | Blocked with “Bench unavailable” if the detail cannot be loaded |
| Search / Random / Newest / Leaderboard / Login | Need network |
| Add Bench draft (**Save to upload later**) | Works on device |
| Upload | Needs network |

The app caches the global bench list so the map stays useful without a connection once data has been downloaded at least once.

---

## Quick reference — common tasks

| I want to… | Do this |
|------------|---------|
| Find a plaque by wording | **Search** → type text → **Search** |
| Surprise me | **Search** → **Random Bench** |
| See what was added lately | **Search** → **Newest 5 benches** |
| Switch to satellite | Top-right **Layers** → Satellite (Esri) |
| Jump to where I am | **Locate** |
| Remember a spot for later | Long-press the map |
| Add a new memorial bench | **Add** → photos + inscription → **Upload** → now or later |
| Finish drafts later | **Upload** (bottom) → edit / upload / upload all |
| Credit myself on uploads | Menu → **Login** first |
| Fix a bench I contributed | Open details → **Edit** (must be logged in) |
| Dark UI | Menu → **Dark mode** |

---

## Related docs

- Project overview, build, and config: [`../README.md`](../README.md)
- OCR SDK notes: [`../ppocr-sdk/README.md`](../ppocr-sdk/README.md)
- Live site: https://openbenches.org/
