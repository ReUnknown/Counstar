<div align="center">

# Counstar

**YouTube, nicer to look at.**

A YouTube client for Android phones, built with Material 3 Expressive.

[![Latest release](https://img.shields.io/github/v/release/ReUnknown/Counstar?label=latest&color=a8c7fa)](https://github.com/ReUnknown/Counstar/releases)
![Android 12+](https://img.shields.io/badge/Android-12%2B-3ddc84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff?logo=kotlin&logoColor=white)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

[**Download the latest APK →**](https://github.com/ReUnknown/Counstar/releases/latest)

</div>

---

## Why Counstar

Your YouTube, with your account: your home feed, subscriptions and history are all there. The
layout is the one you already know from the YouTube app; everything on top of it is redone in
Material 3 Expressive, with acrylic bars, a glow that comes from the video you're watching, and
springy, physical motion.

## Features

- Sign in with Google and get your own home feed, with its filter chips
- Subscriptions, history and notifications
- Search
- Full-screen and floating mini player: the video keeps playing in the corner as you browse
- Like and subscribe
- Ambient glow on the watch page, taken live from the video
- Up next with filters: everything, the same channel, or what you haven't watched
- Updates itself from GitHub releases

## Install

1. Download `Counstar-x.y.z.apk` from the [latest release](https://github.com/ReUnknown/Counstar/releases/latest).
2. Open it and allow installing from your browser or file manager when asked.
3. Sign in with Google. Google handles your password; Counstar never sees it.

Counstar checks GitHub for new versions once a day and offers them with a single tap
(**You → Updates** to check by hand).

**Requires** Android 12 or newer on a 64-bit phone.

## Build it yourself

```bash
git clone https://github.com/ReUnknown/Counstar.git
cd Counstar
./gradlew assembleDebug
```

You'll need JDK 21 (the one bundled with Android Studio works). The APK lands in
`app/build/outputs/apk/debug/`. Release builds are signed with a key kept outside the repo.

**Built with** Kotlin, Jetpack Compose, Material 3 Expressive, Media3 (ExoPlayer), Coil and Ktor.

## Credits & license

The YouTube client is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist)'s
InnerTube library and [InnerTubeX](https://github.com/MetrolistGroup/innertubex). Thank you to
everyone who works on them.

Counstar is free software under the [GNU GPL v3](LICENSE). It isn't affiliated with or endorsed
by YouTube or Google. You need your own YouTube account to use it.
