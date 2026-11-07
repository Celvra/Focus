<div align="center">

# Focus

Your next-generation private LLM assistant

[![CI](https://github.com/Celvra/Focus/actions/workflows/ci.yml/badge.svg)](https://github.com/Celvra/Focus/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com)

[中文](README.zh-CN.md)

</div>

## Community

[QQ](https://qm.qq.com/q/BeQPYWuzVS)

## Why choose it

It can connect to your RSS and Atom feeds, so you can discover something new every day.

Like Muse, it can provide you with daily goals, updates, and ideas.

It can also connect to your computer via Focus Bridge to operate files and the terminal.

It can control this phone through accessibility, root, or Shizuku. Another Focus phone can be paired over the local network and controlled with the same tools. The assistant can read the screen, tap, type, run shell commands, manage files, packages, settings, and reboot the device.

Plugins can add tools, pages, overlays, device access, channels and automation. See `docs/plugins.md`.

## Assistant

- Manage goals, plans, and reminders
- Save ideas, updates, and memories
- Read and write files in connected folders
- Add and remove subscriptions, read and search articles
- List providers, test connectivity, and switch models
- Execute commands, open a terminal, and edit files on a paired computer
- Control this phone or a paired phone with accessibility, root shell, and Shizuku

When Auto Pilot is enabled, connect to Jev / LLMs to automatically assess safety.

Device control is on the Device page. Enable the Focus accessibility service, optionally grant root or Shizuku, then ask the assistant to use the device tools. Remote control uses a pairing code and a trust list.

Persistent shell sessions, background commands, accessibility event waiting, live WebSocket screen preview, and persistent event triggers are available for longer tasks. Finished background commands notify the user and start a new assistant turn. Triggers can run a shell command, send a notification, or start a new assistant turn when an accessibility event matches.

## Models

Focus can connect to multiple providers and pull models from providers.

We support OpenAI Response, Chat Completions, and Anthropic Messages.

## Build

Requires JDK 21 and Android SDK 37.0.

## Bridge

Bridge can connect to your Focus and let Focus control your computer.

## License

MIT, see `LICENSE`.