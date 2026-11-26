# REA analysis of T-Link 1.3.12

Analysis date: 2026-11-15. Tool: **rea-agents 6.8.0**, using its Android provider, **jadx-headless-mcp 0.7.1**, Java 21.0.13. REA ran through its CLI without changing Codex/MCP registration. The APK was inspected statically; no original application data was accessed.

## Exact artifact

- Supplied path: `D:\Work\Trealink\T-Link_1.3.12_APKPure.apk`
- SHA-256: `2c0dec280bf8513ef5d2adefc11456316fa864969dbaad5758a883f88dd0a3d8`
- Package: `com.jvckenwood.tlink`; version `1.3.12`, code `10312`.
- Minimum SDK 26, target SDK 35.
- Parsed 1,386 classes and 1,117 resources.
- Package evidence: `ev_d57656057e9cf20f08350199b93b637b6d76e920df7d3c7c1ffadb6945867881`.

## Observed structure

`inspect-android-package` decoded the manifest. The launcher is `com.realvnc.androidsampleserver.activity.VNCMobileServerProxy`; the application is `VncServerApp`. Activities include `VNCMobileServer`, `WifiConnectActivity`, `LauncherSettingActivity`, `TutorialActivity`, `OverviewActivity`, and license/terms screens.

The manifest declares `VncServerService` with `mediaProjection|connectedDevice` foreground-service types, an accessibility service, and a USB accessory entry point. It requests network, Bluetooth, Wi-Fi, overlay, and media-projection related permissions.

`search-android-classes` with `com.realvnc.androidsampleserver` returned the connection activities and preset launcher buttons for Map, Music, Movie, Browser and Social apps. `inspect-android-class` inventoried the main activity and service; `inspect-android-method` decompiled each `onCreate` method with explicit overload index 0.

The main activity's `onCreate` configures a navigation drawer, obtains `WifiP2pManager`, binds to `VncServerService`, wires preset buttons, and reads a `UsbConnect` intent extra. The service's `onCreate` initializes an automotive VNC server, a handler thread, an encoder registration, network advertisement, and a local diagnostic log. Native-library initialization has an `UnsatisfiedLinkError` path. These observations establish that this is an automotive mirroring app rather than the folder-management app initially described.

Supplementary Android `aapt dump resources` inspection found the original primary/accent color `#760f25` and dark color `#510b1b`, USB settings resources and connection/tutorial UI identifiers. Trealink uses the observed color family with independently drawn graphics.

## Scope decision and reconstruction

The user explicitly chose **“Reproduce the mirroring app”** after being informed of the APK identity. The earlier folder-manager draft was removed from the application source and final APK. There is no folder UI in Trealink; listing/creating/renaming/deleting folders are reported as unsupported, not as tested features of the reference app. No test folders were left behind.

Trealink reimplements Android consent-based projection and a standard view-only RFB 3.8 server. It reproduces the basic sharing/connection/launcher workflow. It does not claim the proprietary automotive discovery, authentication extensions, USB accessory transport, native H.264 encoder, audio or remote input have been reconstructed.

## Reproduce the investigation

Supply REA's entry script and the separate JADX bridge JAR:

```powershell
.\scripts\analyze.ps1 `
  -ReaScript 'C:\path\to\rea-agents\scripts\rea.mjs' `
  -JadxJar 'D:\Work\rea-tools\jadx-headless-mcp-0.7.1-all.jar'
```

The exact CLI commands and artifact selectors are in that script. REA itself can be acquired from [its official repository/package](https://github.com/morluto/rea); acquire tooling in a trusted tools directory outside this target project. The app build does not require REA.

Full REA Evidence JSON is retained locally in this directory (`package.json`, `classes.json`, `main-activity.json`, `onCreate.json`, `vnc-service.json`, `service-onCreate.json`). Raw evidence includes decompiled reference code and is excluded from Git, as are the supplied APK and resource dump. The public repository contains this summary and reproducible commands.

## What this evidence cannot establish

Static decompilation does not prove runtime behavior, complete dynamic/reflection coverage or physical receiver compatibility. The APK signature was not verified by REA's Android provider. No folder-management screen was identified in the inspected declarations/classes; this is not a claim that the binary contains no filesystem operations. No proprietary implementation is redistributed.
