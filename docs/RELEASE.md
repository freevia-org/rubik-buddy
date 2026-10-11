# Releasing Rubik Buddy to Google Play

Application id: `org.freevia.rubikbuddy`. This follows the same process as the other Freevia
apps (for example Sudoku Buddy): one upload key per app, signing material supplied
through environment variables, and a `play-submission` folder kept outside the repository.
No secret ever lives in this repository.

## Versioning

- `versionName` is set in `composeApp/build.gradle.kts`.
- `versionCode` is the `VERSION_CODE` environment variable when set, otherwise the number of
  commits reachable from `HEAD` (`git rev-list --count HEAD`). Each Play upload needs a
  higher code than every earlier upload. Build from the exact commit you intend to ship and
  record the resulting code in the submission folder's README.

## Signing

Play App Signing is used: Google holds the app signing key and an upload key signs the
bundle. Each Freevia app has its OWN upload key. Play rejects a bundle signed with a key that
is already used by another app ("signed with a key that is also used to sign APKs delivered
to users"), so never reuse another app's keystore.

Rubik Buddy's upload key lives outside the repository, by the same convention as Sudoku Buddy:

- `C:\Users\anton\Documents\Freevia-secure\RubikBuddy\recovered\rubik-buddy-upload.jks`
- `C:\Users\anton\Documents\Freevia-secure\RubikBuddy\recovered\credentials.json.dpapi`
  (keystore password, key alias, key password; DPAPI-encrypted for the current Windows user)
- `C:\Users\anton\Documents\Freevia-secure\RubikBuddy\backup\rubik-buddy-upload.jks.dpapi`
  (DPAPI-protected copy of the keystore. DPAPI files only open for this Windows user on this
  machine, so also copy the keystore to encrypted media kept elsewhere and record the
  passwords in the password manager.)

Upload certificate SHA-256 (public; this is what Play registers on first upload):

```
D8:0D:68:04:11:22:73:BB:50:97:84:AA:CD:AC:BC:DB:86:9D:70:CD:09:FC:EF:AC:10:0D:0A:B1:B2:5B:14:5C
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
$dir = 'C:\Users\anton\Documents\Freevia-secure\RubikBuddy\recovered'
$c = [Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect(
       [IO.File]::ReadAllBytes("$dir\credentials.json.dpapi"), $null, 'CurrentUser')) | ConvertFrom-Json
$env:SIGNING_KEYSTORE          = "$dir\rubik-buddy-upload.jks"
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
Signing with this app's own upload key, complete the declarations from `docs/play-listing.md`,
and upload the `.aab` to Internal testing first, then promote the tested bundle.
