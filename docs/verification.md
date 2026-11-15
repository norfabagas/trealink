# Verification

Date: 2026-11-15. Host: Windows, Java 21.0.13. APK: `dev.trealink.app`, version 0.3.0, minimum API 26, target API 35. Android execution used a new disposable x86_64, 16 KB-page emulator running **Android 17** with a 320 × 640 phone display. The independent display diagnostic uses an additional private 800 × 480 virtual display. No physical phone or receiver was attached; Galaxy A07 / One UI 9 and Kenwood DMXGT400R compatibility remains untested.

## Build and protocol

`scripts/build.ps1` compiled the Java application against Android platform 35, converted it to DEX, packaged and aligned it, and verified its v2/v3 APK signatures. The build reads no contents from the reference APK.

`scripts/test.ps1` passed **17** real socket-level checks against `RfbServer`:

1. Wrong password is rejected.
2. RFB 3.8 authentication and framebuffer dimensions are correct.
3. 32-bit little-endian RGB pixels match a known framebuffer.
4. A requested subrectangle contains the expected pixels.
5. An out-of-bounds request produces an empty update.
6. Negotiated RGB565 big-endian pixels are correct.
7. An updated framebuffer reaches the viewer.
8. View-only key/pointer messages are consumed without applying input.
9. An oversized clipboard message is rejected and its connection closes.
10. A subsequent viewer can reconnect.
11. Stopping closes the active viewer socket.
12. Stopping closes the listening port.
13. Explicitly configured app-owned pointer callbacks receive authenticated, in-bounds pointer events.
14. Out-of-bounds pointer events are dropped.
15. Cursor shapes are emitted only after viewer negotiation.
16. The negotiated cursor shape has zero dimensions.
17. Framebuffer data remains correctly aligned after the cursor update.

The test client implements its authentication separately from the server and compares actual wire bytes. It does not mock the socket transport.

The same script passed **10 coordinate-mapping checks** covering scaled portrait input, landscape/portrait letterboxing and pillarboxing, content origins, frame edges, invalid dimensions and out-of-content positions. These are mathematical mapping checks; live rotation on the Samsung phone remains untested.

## Direct touch on Android

`scripts/test-touch.ps1` passed **10** checks against native Android controls using the real user-enabled Accessibility gesture API and actual authenticated RFB packets:

| Check | Observed result |
| --- | --- |
| Explicit local opt-in plus full-screen sharing | Touch bridge becomes ready after the service is enabled |
| Hover | No Android button action |
| Absolute tap | Native button's count increments |
| Press and hold | Native long-click count increments |
| Continuous drag | Native SeekBar moves past 70% |
| Swipe | Native ScrollView reveals later items |
| Viewer disconnect during a held touch | The native view receives finger-up |
| Cross-app touch | Opens the read-only Connected devices page in Android Settings |
| Local control off | Same remote tap no longer clicks the button |
| Stop sharing | Input session ends and the network listener closes |

The test activity keeps all counters in memory. Android Accessibility settings are changed only inside the disposable emulator, and their original values are restored afterward. A separate **test-only** UI probe uses `UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`: the legacy UI dump tool suppresses Accessibility services while inspecting them, so it cannot validate an active gesture service. The probe APK is excluded from the production APK and has no launcher activity.

Full local results: `.local/touch-test/results.json`; a sanitized copy is committed at [test-results/android-touch.json](test-results/android-touch.json). These tests validate phone-side gesture dispatch through VNC, **not** Kenwood T-Link transport, cursor rendering on the receiver, physical A07 compatibility, multi-touch or remote typing.

## Android end to end

`scripts/test-android.ps1` covers **13** Android checks: nine for the original mirroring flow and four for the independent-display diagnostic. The nine mirroring regressions passed; the four independent-display checks passed in a focused `-ExtensionOnly` run using the non-suppressing UI probe. Tests install without clearing data and drive the real Android consent UI. The focused run waits for an actual streamed-button pixel change after a tap. The observed behavior is:

| Operation | Result and scope |
| --- | --- |
| Cancel screen-sharing permission | Returns to app with cancellation status |
| Approve entire-screen capture | Foreground capture starts; an 8-character password appears |
| USB mode | ADB forwarding carries authenticated VNC and actual 320 × 640 nonblack screen pixels |
| Switch to another app | Captured pixels change; service continues in the background |
| Stop from Trealink | Projection stops and TCP port 5900 closes |
| Start another session | Android asks for consent again and password changes |
| Wi-Fi mode | Network interface listener serves actual pixels through emulator NAT, independently of ADB forwarding |
| Stop second session | Listener closes again |
| Lock-screen revocation | System ends projection; callback closes capture and listener |
| Independent session | Starts an app-owned display without a phone MediaProjection grant |
| Phone multitasking in independent mode | Phone opens Settings while VNC retains the known test-panel pixels at 800 × 480 |
| Independent input | Viewer tap changes the streamed test button and increments its counter; phone Settings keeps window focus |
| Independent stop | Virtual display is removed and port 5900 closes |

Local outputs: `.local/android-test/results.json`, `usb-frame.png`, `settings-frame.png`, `network-frame.png`, `extend-frame.png`, `extend-tap.png`, `extend-phone-settings.png`, and `stopped-ui.png`. Session-bearing screenshots are kept local; passwords expire when sessions stop.

The network check demonstrates the Wi-Fi-mode binding and protocol over emulated networking. **It does not establish physical LAN performance or JVC/Kenwood receiver compatibility.** The app uses standard raw RFB, not the original proprietary automotive transport/extensions. Physical USB, audio, receiver pairing, receiver-originated touch, DRM capture and other Android versions are unverified or unsupported as described in the README.

## Original folder request and data preservation

The supplied APK is a mirroring app. After the user clarified the intended scope, folder listing/creation/renaming/deletion were removed from the project and are **not applicable / unsupported**. They are not reported as successful operations of the reference APK.

All Android tests run in a newly created emulator rooted at `.local/avd`. Only Trealink's connection/output-mode and local touch-control preferences are persisted by the app; it requests no filesystem access. Temporary earlier host-side folder tests used a fresh system-temp root and removed only that root. No test folders remain. Existing workspace files and user data were not renamed or deleted.

The supplied APK's SHA-256 remains:

`2c0dec280bf8513ef5d2adefc11456316fa864969dbaad5758a883f88dd0a3d8`
