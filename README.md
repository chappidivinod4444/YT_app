# Vidflow

A personal, ad-free video app for Android with its own design. It reads public video pages
with the open-source [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)
library and plays them in its own player, so no ads are shown.

## Install

Open **Releases** on your phone and install `Vidflow.apk` from the newest release.

## Features

- Home with categories, and a **Subscriptions** feed of your channels' newest videos
- Search with suggestions and filters (videos, channels, playlists)
- Watch page with quality choice, description, related videos
- Full screen, picture-in-picture, background play with media notification
- Mini player while you browse
- Subscribe to channels and watch history, stored on the phone (no account)
- Resume where you left off
- Share a link from any app to Vidflow to open it

## Note

The site changes often. When videos stop loading, the extractor library version in
`app/build.gradle.kts` usually needs updating to the newest release.

Licensed under GPL-3.0 (the extractor library is GPL-3.0).
