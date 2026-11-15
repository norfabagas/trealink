# Smoke test — 2026-11-15

Tested APK: **Trealink 0.3.1** (`dev.trealink.app`, version code 4).

The implemented VNC mirroring and phone-side direct-touch paths pass the checks below. This is not evidence of a working native Kenwood T-Link connection or a guarantee of defect-free behavior on other hardware.

## Error found and fixed

Releasing a pressed pointer outside the framebuffer was discarded by the server's coordinate filter. Android could keep the finger down until disconnect or the held-touch timeout.

A new socket regression reproduced the failure before the fix. The server now reports an out-of-frame release separately, and the active session ends the finger at its last valid position. Invalid coordinates never reach Android. The independent diagnostic resets its pointer without synthesizing a click.

The Android regression presses a native control, releases outside the framebuffer and verifies `Finger: up` while the viewer remains connected. Keeping the connection open prevents disconnect cleanup from hiding the bug.

The test fixture also now opens the Android Settings root with task-reset flags: otherwise Settings could reopen its remembered subpage, making the cross-app navigation check fail. A failed end-to-end test now force-stops its test app before removing forwarding rules.

The final audit found that the emulator NAT redirect survived cleanup: deletion requires `tcp:15902`, whereas the old command supplied `tcp:15902:5900`. The script now uses the correct syntax and treats emulator-console `KO:` responses as failures, since ADB returns zero for those rejections. A focused add/remove check confirmed no active redirects remain, and a deliberately rejected command confirmed error detection. These two harness checks are additional to the 52 app/protocol checks below.

## Checks

| Suite | Passed | Evidence |
| --- | ---: | --- |
| Real socket RFB protocol | 18 | Authentication, pixel formats, reconnect, cleanup, pointer bounds, outside release, cursor framing |
| Coordinate mapping | 10 | Scaling, letterbox/pillarbox positions and invalid bounds |
| Android direct touch | 11 | Actual Accessibility taps, holds, drag, scroll, release, cross-app navigation, opt-out and stop |
| Android mirroring and independent diagnostic | 13 | Consent cancellation/approval, captured pixels, background updates, USB ADB tunnel, network binding via emulator NAT, password rotation, lock revocation, independent pixels/input and cleanup |

All **52 distinct checks** passed. The 13-check Android suite completed in a single full run against the fixed APK; the touch suite completed separately on the same disposable emulator. Shared protocol/mapping tests run at the start of each script and are counted once here.

Environment: Windows, JDK 21.0.13, Android SDK platform 35/build tools 36.0.0; disposable Android 17 x86_64 emulator with 16 KB pages and a 320 × 640 phone display. No real phone or head unit was attached. Build/signing verification passed using APK signature schemes v2 and v3.

The crash buffer and `Trealink`/`AndroidRuntime` error logs contain zero entries since this smoke session began. Final checks show no active MediaProjection, TCP 5900 listener, ADB forwarding rules or emulator redirects. Accessibility settings are restored to their prior values (no enabled services; accessibility disabled).

Sanitized results: [Android touch](test-results/android-touch.json), [Android mirroring](test-results/android-mirroring.json), [verification details](verification.md). Local screenshots and session-bearing UI captures remain ignored by Git.

## Reproduce

From `D:\Work\Trealink` in PowerShell, with the disposable emulator running:

```powershell
.\scripts\build.ps1
.\scripts\test.ps1
.\scripts\test-touch.ps1 -Serial emulator-5554
.\scripts\test-android.ps1 -Serial emulator-5554
```

Run the Android suites sequentially. They install without clearing app data; the touch suite restores the emulator's prior Accessibility settings. See the [README](../README.md#run-on-android) for APK installation, sharing consent and VNC viewer setup.

## Remaining limits

Galaxy A07 / One UI 9, physical Wi-Fi, native Kenwood DMXGT400R USB discovery/pairing, receiver-originated touch and receiver cursor rendering are untested. The proprietary Kenwood T-Link transport, Android Auto hosting, audio, multi-touch and remote keyboard input are not implemented. The independent-display mode is an app-owned test panel, not an Android Auto desktop. These checks cannot establish that the car can connect to or control Trealink.

The reference APK remains unchanged with SHA-256 `2c0dec280bf8513ef5d2adefc11456316fa864969dbaad5758a883f88dd0a3d8`. The tested development APK has SHA-256 `f0c3262832eb155c31a3b580b826a431841531b40641ef021a0269fca972b3ec`.
