<div align="center">

<img src="docs/screenshots/banner.png" alt="TorrentRemote" width="100%" />

# TorrentRemote

**Manage all your qBittorrent and Transmission servers from one phone.**

**English** | [简体中文](README.zh-CN.md)

[![CI](https://github.com/smhjw/qbitremote/actions/workflows/ci.yml/badge.svg)](https://github.com/smhjw/qbitremote/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/smhjw/qbitremote?display_name=tag&sort=semver)](https://github.com/smhjw/qbitremote/releases)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

</div>

---

TorrentRemote is a native Android app (Kotlin + Jetpack Compose) for remote torrent management. Live speeds from every server sit on one home screen, each server gets its own chart dashboard, and the torrent list and detail pages cover day-to-day work end to end. qBittorrent and Transmission servers mix freely in the same UI.

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/home.png" width="250" alt="Home" /><br /><sub>Home: all servers at a glance</sub></td>
    <td align="center"><img src="docs/screenshots/torrents.png" width="250" alt="Torrent list" /><br /><sub>Torrent list</sub></td>
    <td align="center"><img src="docs/screenshots/details.png" width="250" alt="Torrent details" /><br /><sub>Torrent details</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/server-dashboard.png" width="250" alt="Server dashboard" /><br /><sub>Server dashboard and upload map</sub></td>
    <td align="center"><img src="docs/screenshots/charts.png" width="250" alt="Charts" /><br /><sub>Category, tag and tracker charts</sub></td>
    <td></td>
  </tr>
</table>

## Features

### Multiple servers, one app
- qBittorrent WebUI API (4.x / 5.x) and Transmission RPC side by side, with automatic endpoint fallback for older versions
- Save many server profiles; each keeps a cached snapshot that stays visible while it is offline
- Exponential backoff for unreachable servers, and polling pauses in the background to save battery and data

### Home and dashboards
- Combined upload/download speed with a live chart, plus a stack of server cards you can long-press to reorder
- A dashboard per server: daily upload world map, category share, per-tag upload, tracker sites, state, share-ratio and size distribution
- Dashboard cards can be hidden and reordered, and the layout is remembered per server

### Torrents
- Swipe right to pause or resume, swipe left to delete; long-press to multi-select and act on many at once; pull to refresh
- Filter by state, category, tag and tracker site; the filter panel is a grouped checklist with live per-option counts, multi-select within a group, and a removable summary of active conditions above the list. Also search, and 11 sort orders
- Details page with Info, Trackers, Peers and Files tabs: connected peer list, per-file download priority, rename, move, export, re-announce and recheck
- Edit or remove trackers (passkeys are masked), cross-seed detection, per-torrent speed and share-ratio limits
- Add torrents by magnet link, URL or `.torrent` file, or share a link into the app from anywhere

### Speed limits
- Read and change global and alternative speed limits, including the scheduler (time window and days)
- With several servers online, pick the target server right inside the dialog

### Design and comfort
- A calm, minimal dark and light design with monospaced numbers, plus an optional custom background image
- Edge-to-edge layout and predictive back gesture on recent Android versions
- Torrent-completed notifications that only show the title on the lock screen
- English and Simplified Chinese

### Security
- Server credentials are encrypted with Android Keystore (AES-GCM) and excluded from backups
- A clear warning when connecting to a public address over plain HTTP (LAN addresses are not flagged)
- Release builds are minified with R8, and logging is stripped

## Requirements

| | |
|---|---|
| Android | 8.0 (API 26) or newer, targets Android 16 (API 36) |
| qBittorrent | 4.x / 5.x with WebUI enabled |
| Transmission | 2.80+ with RPC enabled |

## Download

- [Google Play](https://play.google.com/store/apps/details?id=com.hjw.qbremote)
- [GitHub Releases](https://github.com/smhjw/qbitremote/releases): signed APK with a `SHA256SUMS-vX.Y.Z.txt` checksum manifest

Prebuilt binaries are never committed to this repository. Please verify downloads against the checksum manifest of each release.

## Build from source

```bash
./gradlew assembleDebug
```

Only JDK 17 and an Android SDK (`ANDROID_HOME`) are needed. CI runs unit tests, lint, a debug build and an unsigned release build on every push. Signed release bundles are built with `scripts/build-release-aab.ps1`, which needs a local keystore (see [AGENTS.md](AGENTS.md)).

If the machine has no JDK 17 / Android SDK, the project can also be built inside Docker by reusing a host that already ships an Android SDK — see [docs/docker-build-env.zh-CN.md](docs/docker-build-env.zh-CN.md).

## Author

Built and maintained by **蜂巢 @hjw666** ([profile](https://pting.club/users/hjw666)).

## Community

Thanks to the [LINUX DO](https://linux.do) community for feedback and support.

## License

See [LICENSE](LICENSE). Bundled fonts (Geist, Geist Mono) are under the SIL Open Font License, see [licenses/](licenses/).
