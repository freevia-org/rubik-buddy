# Google Play listing: Rubik Buddy

Copy and answers to paste into Play Console. Keep them consistent with
`docs/play-data-safety.md` and `docs/privacy-policy.md`.

## Basics

| Field | Value |
| --- | --- |
| App name (title, max 30) | Rubik Buddy |
| Application id | `org.freevia.rubikbuddy` |
| App or game | App |
| Category | Tools (alternative: Puzzle under Games, which makes it a game listing) |
| Free or paid | Free |
| Contact email | info@freevia.org |
| Website | https://freevia.org/rubik-buddy/ |
| Privacy policy URL | https://freevia.org/rubik-buddy/privacy |
| Contains ads | No |

## Short description (max 80 characters)

```
Scan a 3×3 cube with your camera and solve it offline with a 3D move guide.
```

(75 characters.)

## Full description (max 4000 characters)

```
Turn a scrambled 3×3 cube into a clear sequence of moves. Rubik Buddy guides you through scanning each face, checking the colors, and following a solution on an interactive 3D cube.

SCAN THE CUBE
• Follow the on-screen rotation guide to scan all six faces with your camera.
• Tap any sticker to correct a color the camera missed. Even lighting helps distinguish similar colors.
• The app checks whether the entered cube is possible before solving it.

FOLLOW THE SOLUTION
• Read one move at a time, with the current turn shown on an animated, draggable 3D cube.
• Step back, replay moves or restart the guide whenever you need.
• Works fully offline.

Rubik Buddy is designed for a standard 3×3 cube with the usual six colors. Camera images are processed on your device during scanning and are not stored or sent anywhere. There is no account, ads, analytics or tracking.

Rubik's Cube is a trademark of its owner. Rubik Buddy is independent and is not affiliated with or endorsed by that owner. The app is open source under the MIT License: https://github.com/freevia-org/rubik-buddy
```

This copy matches the current public English (US) Play listing and is below the limit.

## Content rating (IARC questionnaire)

Category: utility / reference. Answer **No** to all of: violence, sexual content,
profanity, controlled substances, gambling, user-generated content, user-to-user
interaction, location sharing, purchases, and unrestricted web access. The app has no
network access. Expected outcome: suitable for everyone (PEGI 3 / ESRB Everyone); the
actual rating is assigned by the questionnaire.

## Data safety form

Matches `docs/play-data-safety.md`:

- Does your app collect or share any of the required user data types? **No.**
- The camera is used only for on-device processing; frames are not stored or transmitted,
  so nothing is "collected" under Play's definition (data processed only on device and not
  sent off the device).
- Encryption in transit / data deletion request: not applicable.
- No third-party SDKs that collect data.
- Privacy policy: https://freevia.org/rubik-buddy/privacy

## Other declarations

- Target audience: 13 and over is the simplest choice. Choosing under-13 groups brings
  Families policy requirements; the app is not designed specifically for children.
- Ads: No. News app: No. Government app: No. Financial features: none. Health: none.
- App access: all functionality is available without login, no special instructions.
- Permissions: `CAMERA` (core feature). The camera `uses-feature` is required, so the app
  is only offered on devices with a camera.

## Graphic assets

| Asset | Requirement | File | Status |
| --- | --- | --- | --- |
| App icon | 512x512 PNG, up to 1 MB | `assets/store/icon_512.png` | Regenerated, reads "RUBIK BUDDY" |
| Feature graphic | 1024x500 PNG or JPEG | `assets/store/feature_graphic.png` | Regenerated, reads "RUBIK BUDDY" |
| Phone screenshots | at least 2, up to 8; 16:9 or 9:16, each side 320-3840 px | `assets/play/screenshots/` | See below |

Both graphics come from `assets/gen_assets.py` (Pillow); regenerate with
`python assets/gen_assets.py`. The icon has rounded corners with a transparent margin;
Play applies its own mask and a full-bleed square is the more standard submission, so
consider that if the review preview looks off.

### Phone screenshots (required: minimum 2)

Captured on a physical phone (1080x2412) with a real cube and the phone's clean status
bar (Android demo mode), in `assets/play/screenshots/`:

1. `01-scan.png`: scan screen, live camera with the tile grid and neighbour-colour guides.
2. `02-initial-position.png`: solve screen after a valid scan, showing how to hold the cube.
3. `03-solution-step-1.png`: move 2 of 19, with the turn named and shown on the 3D cube.
4. `04-solution-step-2.png`: move 4 of 19.

Still worth adding before launch: a tile-correction screen and the solved screen.

Capture command: `adb exec-out screencap -p > assets/play/screenshots/<name>.png`. For a clean
status bar, enable demo mode first (`settings put global sysui_demo_allowed 1` and the
`com.android.systemui.demo` broadcasts), and turn it off afterwards.

## Developer account notes (verify in Play Console, rules change)

- New personal developer accounts created after 13 November 2023 have generally had to run
  a closed test with at least 12 testers opted in for 14 continuous days before applying
  for production access. Organization accounts are generally exempt. Confirm the current
  requirement in Play Console (Dashboard, "Set up your app").
