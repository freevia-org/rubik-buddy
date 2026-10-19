# Screenshot provenance

These are unedited native Simulator captures of Rubik Buddy 1.0.0 in Release
configuration. They show the actual iOS interface; Android screenshots and
simulated camera imagery are not used. JPEG is captured directly by simctl, without alpha. Status bar overrides set 9:41 and full
battery for capture, then are cleared.

- iPhone: dedicated iPhone 17 Pro Max simulator, iOS 27.0, portrait 1320×2868.
- iPad: dedicated iPad Pro 13-inch (M5) simulator, iOS 27.0, portrait 2064×2752.
- Capture command: `IOS_SIMULATOR_UDID=<test-device> ./scripts/capture-ios-screenshot.sh iphone 01-home`
  (use `ipad` for the iPad set).
- Release build command: `IOS_CONFIGURATION=Release IOS_DERIVED_DATA="$PWD/build/xcode-store-simulator" ./scripts/build-ios.sh`.

The home screen provides the minimum native screenshot for each supported device
family. Add camera/solution screenshots from the final real-device validation when
hardware is available. Do not submit a sample-frame scan screen as a real camera
capture. Review every screenshot against the final signed build before upload.

Apple accepts 1–10 screenshots per device size; the local validator checks counts,
image headers and the documented large iPhone/13-inch iPad portrait sizes. See
[Apple's current specification](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications).
