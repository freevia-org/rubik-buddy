# Releasing Rubik Buddy to Google Play

Application id: `org.freevia.rubikbuddy`. This follows the same process as the other Freevia
apps (for example Sudoku Buddy): one shared Freevia upload key, signing material supplied
through environment variables, and a `play-submission` folder kept outside the repository.
No secret ever lives in this repository.

## Versioning

- `versionName` is set in `composeApp/build.gradle.kts`.
- `versionCode` is the `VERSION_CODE` environment variable when set, otherwise the number of
  commits reachable from `HEAD` (`git rev-list --count HEAD`). Each Play upload needs a
  higher code than every earlier upload. Build from the exact commit you intend to ship and
  record the resulting code in the submission folder's README.

## Signing

Play App Signing is used: Google holds the app signing key and the Freevia upload key signs
the bundle. The upload key is the existing Freevia keystore, kept on the maintainer's
machine under `C:\Users\anton\Documents\Freevia-secure\` (outside the repository), with
credentials stored DPAPI-protected next to it and in the password manager. Its certificate
SHA-256 is public information and is how an upload is checked against Play's registered
upload certificate:

```
A5:10:D8:2B:87:E7:06:9B:53:D5:20:BE:CA:91:5B:35:2A:E9:6C:EA:85:0C:68:CA:00:61:17:E3:5D:75:D3:E5
```

The build reads these environment variables (or the Gradle properties in brackets, set in
the user-level `~/.gradle/gradle.properties`, never in the project):

| Variable | Gradle property |
| --- | --- |
| `SIGNING_KEYSTORE` (path to the `.jks`) | `signing.keystore` |
| `SIGNING_KEYSTORE_PASSWORD` | `signing.keystorePassword` |
| `SIGNING_KEY_ALIAS` | `signing.keyAlias` |
| `SIGNING_KEY_PASSWORD` | `signing.keyPassword` |

Without them `bundleRelease` still succeeds but produces an UNSIGNED bundle (this is what CI
does), which Play rejects.

## Build the signed bundle (Windows, PowerShell)

Decrypt the credentials into the current process only (never print or save them):

```powershell
Add-Type -AssemblyName System.Security
$dir = 'C:\Users\anton\Documents\Freevia-secure\SudokuBuddy\recovered'
$c = [Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect(
       [IO.File]::ReadAllBytes("$dir\credentials.json.dpapi"), $null, 'CurrentUser')) | ConvertFrom-Json
$env:SIGNING_KEYSTORE          = "$dir\sudoku-buddy-release.jks"
$env:SIGNING_KEYSTORE_PASSWORD = $c.keystorePassword
$env:SIGNING_KEY_ALIAS         = $c.keyAlias
$env:SIGNING_KEY_PASSWORD      = $c.keyPassword
.\gradlew.bat clean :composeApp:bundleRelease
Remove-Item Env:SIGNING_*
```

Output: `composeApp\build\outputs\bundle\release\composeApp-release.aab`.

Verify the signature and the certificate (compare only the SHA-256):

```powershell
jarsigner -verify -verbose -certs composeApp\build\outputs\bundle\release\composeApp-release.aab
keytool -printcert -jarfile composeApp\build\outputs\bundle\release\composeApp-release.aab
```

## Pre-upload checklist

- `.\gradlew.bat :rubik-model:jvmTest :rubik-vision:testAndroidHostTest :rubik-solver:testAndroidHostTest` passes.
- `.\gradlew.bat lint` shows no errors.
- The merged manifest requests only `CAMERA` (matches `docs/play-data-safety.md`).
- The bundle's application id is `org.freevia.rubikbuddy` and the label is "Rubik Buddy".
- `versionCode` is higher than the last uploaded build.
- Listing text and graphics: `docs/play-listing.md`, `assets/store/`, `assets/play/screenshots/`.

## Submission folder

Copy the signed bundle and listing material to
`Freevia\release-artifacts\rubik-buddy\play-submission\` (same layout as Sudoku Buddy):
`app-release.aab`, `app-icon-512.png`, `feature-graphic-1024x500.png`, `screenshots/`,
`play-console-submission.md` (from `docs/play-listing.md`), `privacy-policy.md`,
`release-notes-en-US.txt`, `README.md` with version code and SHA-256 checksums.

## Upload

In Play Console create the app (package `org.freevia.rubikbuddy`), enrol in Play App
Signing with the upload key above, complete the declarations from `docs/play-listing.md`,
and upload the `.aab` to Internal testing first, then promote the tested bundle.
