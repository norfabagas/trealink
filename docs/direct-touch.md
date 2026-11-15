# Direct touch on a mirrored phone

The user prioritized mirrored-screen touch over extending the screen. The target is Galaxy A07 / One UI 9 and Kenwood DMXGT400R. This change implements the Android input engine and standard VNC input path. **It does not implement the Kenwood's native T-Link USB transport or establish hardware compatibility.**

## Input path

Authenticated RFB absolute pointer coordinates → session/permission checks → letterbox-aware phone-coordinate mapping → Android Accessibility gesture strokes.

`RemoteTouchService` is user-enabled through Android settings and also requires Trealink's separate local touch opt-in. It requests gesture capability without accessibility window-content access or event subscriptions. It injects single-finger touches, not mouse events. `StrokeDescription.continueStroke` maintains a pressed finger between movement packets, enabling live dragging and long holds rather than replaying a gesture only after release. Consecutive move samples are coalesced while down/up boundaries are retained.

The bridge checks the active mirroring session, full-display capture request, current capture dimensions, unlocked/interactive phone and local setting before accepting input. It rejects hover and letterbox/out-of-bounds positions. A release outside the framebuffer is still delivered as a release signal, ending an existing touch at its last valid position without forwarding invalid coordinates. Active input is also released when the viewer disconnects, local control is turned off, capture is revoked or the service stops. Capture-session identifiers prevent stale queued events from controlling a later session. A 10-second held-touch timeout prevents indefinitely held input.

Control uses the phone's current display metrics. The fixed capture canvas can letterbox after rotation; coordinates are mapped into the actual content rectangle. App-only capture is not a supported control mode. On Android 14+, enabling touch requests the default-display capture configuration; a running app-only session needs restarting.

The RFB server responds to the optional Cursor pseudo-encoding with an empty cursor shape. It sends this only when the viewer advertises support. This is a client cursor-shape hint, not proof that a Kenwood firmware cursor will disappear. Relative trackpad input must be replaced by direct/absolute touch at the viewer/receiver; its original touch coordinates cannot be reconstructed reliably from arbitrary relative mouse movement.

## Reference APK observations

REA 6.8.0 / JADX 0.7.1 inventoried `ContextInformationAccessibilityService` and decompiled its `onAccessibilityEvent`. The inspected callback extracts package/class names and forwards component information; it does not establish the source of the reported pointer or the full input implementation. `UsbAccessoryProxy` has a branch involving a bearer selection named `AAP` and application startup, but that is not a recovered USB protocol contract. Raw evidence remains local in `analysis/original-accessibility*.json` and `analysis/usb-proxy*.json`.

JVCKENWOOD's [T-Link app listing](https://play.google.com/store/apps/details?id=com.jvckenwood.tlink) describes two-way control and Accessibility use, while also qualifying smartphone/receiver compatibility. Trealink's gesture design is independent implementation based on Android's public APIs, rather than a claim that the OEM app's cursor behavior has been patched.

## Verification approach

`test.ps1` exercises raw RFB packets, cursor negotiation and portrait/landscape/letterbox mapping. `test-touch.ps1` runs only on a disposable emulator. It enables the service in that emulator, drives real Android controls over authenticated RFB and restores the original Accessibility settings afterward. The diagnostic activity stores no data: its buttons, slider and scroll list exist only in memory. Tests also navigate Android Settings without changing its options, then verify that local off and stopping sharing block remote input.

The older independent-display diagnostic remains app-owned: its pointer input goes only to its own panel, never through the phone-touch service. Its presence does not mean Android Auto or a Kenwood receiver connection is available.

## Remaining car-side work

The DMXGT400R connection must deliver calibrated absolute press/move/release coordinates to this input engine. Native T-Link handshake, USB framing, authentication, codec negotiation and control messages have not been recovered or tested against the receiver. Until that transport is connected, VNC success must not be described as working car-display control. The car's own operating restrictions are not changed by this implementation.

Primary API references: [AccessibilityService.dispatchGesture](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [continued strokes](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription), [MediaProjection configuration](https://developer.android.com/reference/android/media/projection/MediaProjectionConfig), [RFB protocol](https://www.rfc-editor.org/rfc/rfc6143).
