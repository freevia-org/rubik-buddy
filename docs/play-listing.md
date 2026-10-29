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
Rubik Buddy helps you solve a standard 3x3 Rubik's Cube. Point your phone camera at each
face of the cube, check the colors it read, and follow a short sequence of moves shown on
an animated 3D cube until the cube is solved.

How it works
- Scan: hold the cube as shown and scan the six faces one after another. The app guides
  you through each rotation.
- Check: if the camera misread a sticker (lighting can make orange, red, yellow and white
  look alike), tap the tile and pick the correct color.
- Solve: Rubik Buddy checks that the cube you entered is possible and computes a
  solution with the Kociemba two-phase algorithm. Step through the moves one at a time,
  replay them, or restart.

Features
- Camera-based scan of all six faces
- Tap-to-correct for any tile the camera got wrong
- Detects impossible scans (wrong color counts, twisted pieces) instead of giving a wrong answer
- Step-by-step move instructions with an animated, draggable 3D cube
- Works fully offline

Privacy
Rubik Buddy needs only the camera permission, and only to read the colors of your cube.
Camera images are processed on your device while you scan and are never stored or sent
anywhere. There is no account, no ads, no analytics and no tracking.

Notes
- Designed for the standard 3x3 cube with the usual six colors (white, yellow, red,
  orange, blue, green).
- Color detection depends on lighting. Good, even light gives the best results.
- Rubik's Cube is a trademark of its owner. Rubik Buddy is an independent app and is not
  affiliated with or endorsed by the trademark owner.

Open source under the MIT License: https://github.com/freevia-org/rubik-buddy
```

The text above is roughly 1,700 characters, well under the limit.

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
