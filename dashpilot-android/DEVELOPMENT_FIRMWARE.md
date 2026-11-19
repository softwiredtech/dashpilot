# Development firmware

In DashKit settings, expand **Firmware → Developer → Development firmware**.
Select an open PR from `softwiredtech/dashkit-firmware`, then download and verify
its build. Review the PR, full head commit, build date and verified version
before confirming installation. Release firmware and the local BIN picker
remain available in the same settings screen.

PR builds require `.github/workflows/pr-firmware.yml` in the upstream firmware
repository. If that workflow is absent, the picker reports that it is unavailable
and offers GitHub navigation; it does not substitute builds from another repository.

## GitHub authentication

Public PR, workflow and artifact metadata can be read anonymously. GitHub's
[artifact download endpoint](https://docs.github.com/en/rest/actions/artifacts#download-an-artifact)
requires authentication. Enter a user-supplied fine-grained token with
**Actions: read** access to the firmware repository; no repository write
permission is needed. DashPilot has no existing GitHub sign-in to reuse.

The token is held only in the firmware screen's memory, is not saved across
activity recreation, and is never bundled with the APK. It is sent only to
`api.github.com`. The signed artifact download request has no Authorization
header. Leaving settings discards the token.

Alternatively, choose **Open build on GitHub**, sign in there, download the
artifact ZIP and extract **dashkit-firmware.bin** for the local BIN picker.
`dashkit-firmware-usb.bin` is for USB flashing only.

## Selection and verification

- Only `.github/workflows/pr-firmware.yml` PR runs matching the current full
  head SHA are eligible. The latest successful matching run is selected.
  Older commits are never substituted for pending or failed builds.
- Only an unexpired `dashkit-pr-<PR>-<short SHA>` artifact from that run is used.
- ZIP extraction stays in bounded memory, rejects duplicate and traversal
  entries, and selects the root app image by its exact filename.
- The app image must match its SHA-256 in `SHA256SUMS.txt` and have the ESP
  image header. `build-info.json` must match the PR, full SHA, version,
  `esp32s3` target and selected GitHub run URL.
- The PR head/open state and artifact availability are rechecked after download
  and immediately before starting the existing BLE OTA uploader.

## Verification

Run `:app:testDebugUnitTest :app:assembleDebug` from the Android Gradle project.
`DevelopmentFirmwareTest` covers selection, unavailable builds, expired and
stale artifacts, archive/checksum/metadata rejection, app versus USB selection,
authentication/rate-limit errors, and credential isolation on downloads.

For a device check, connect DashKit, select a current successful PR build,
verify its preview, and confirm installation. Existing download/OTA error and
progress controls handle the install. Also check refreshing pending builds,
changing a PR head while confirmation is open, and opening GitHub without a
token. These checks require an available PR artifact and a physical DashKit.
