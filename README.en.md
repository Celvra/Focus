<div align="center">

# Focus

Your next-generation private LLM assistant

[![CI](https://github.com/Celvra/Focus/actions/workflows/ci.yml/badge.svg)](https://github.com/Celvra/Focus/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com)

[中文](README.md) · [QQ group](https://qm.qq.com/q/BeQPYWuzVS)

</div>

Focus is a private assistant that runs on Android. It is more than a chat box. It remembers things for you, manages goals and reminders, reads feeds and files, controls your phone, and can operate your computer through Bridge.

## Download

The latest build is on [Releases](https://github.com/Celvra/Focus/releases/latest).

- The APK supports Android 8.0 and later. Android 13 or later is recommended.
- Each release also ships Bridge for Windows, macOS, and Linux.

## First launch

1. Add a model provider. Enter the Base URL and API key, choose a protocol, then select a model.
2. Complete the setup wizard.
3. We recommend enabling autostart, unrestricted background usage, and background locking.

Calendar, microphone, and notification permissions, along with accessibility, Shizuku, Stellar, and Root, are all optional. Focus can still chat without them.

## Everyday use

Focus has a Today page that gathers what is worth seeing each day.

- Updates are collected from your RSS and Atom feeds. Keep what you like and dismiss what you do not.
- Ideas are generated from your goals and recent activity, and can become tasks directly.
- Goals can be active, paused, or completed, and can have a due date.
- Reminders fire a notification when due, and Focus can schedule them for you.
- The Library lets you read articles, manage feeds, and read or write files in connected folders.
- Memories and profile data persist across chats and evolve over time. The assistant also remembers what you want to be called.

Calendar events, files, feeds, and articles can all be handed to the assistant directly. No copy-paste needed.

## Super agent

Inspired by DeepSeek's Super Agent architecture.

Focus's main conversation is handled by a resident Router. Every message you send goes to the Router first. It either answers directly or splits the work into side chats.

Side chats run in parallel with the main conversation. Each side chat has its own model tier, tools, and full trace, and reports back to the Router when done. The Router then decides whether to tell you directly, show a result card, or ask a follow-up.

- Side chats come in light, standard, and heavy tiers for tasks of different complexity.
- You can limit how many side chats run at once. Extra work queues up.
- Attachments are forwarded to side chats automatically. Images and files do not need to be uploaded again.
- The watchdog handles timeouts, repeated tool calls, and tasks that stop reporting.
- The voice assistant and overlay assistant both use the main conversation, so they share the same Router.
- You can assign separate models to the Router, side chats, subagents, compaction, the watchdog, and the safety judge.

The Router can also create and manage scheduled tasks, webhooks, and home screen shortcuts itself, without opening Settings.

## Always within reach

- The overlay assistant can float above any app so you can ask without switching back and forth.
- You can attach the current screen or draw a region and ask about it. This works well for reading images, translating, and extracting text, similar to Circle to Search.
- Voice input supports a local offline model and cloud transcription.
- There is a Focus tile in Quick Settings.
- A 4x2 home screen widget shows the latest reply and running state.
- Long-press the app icon, or ask the assistant to create a home screen shortcut, to open Focus quickly.

Focus is also an Xposed module, but currently only for hijacking the power button on ColorOS 15 to replace the XiaoBu assistant. I do not have other devices, so I cannot develop hijack modules for other systems yet.

On AOSP or near-stock systems, set Focus as the voice assistant and enable power-button activation for the voice assistant. Then a long press on the power button opens Focus.

## Phone and computer

Local device control has three modes:

- Accessibility service: read the screen, tap, long-press, swipe, type, press keys, open apps, take screenshots, and listen for UI and notification events.
- Shizuku, Stellar, and other tools compatible with the Shizuku API: shell, background commands, files, apps, settings, and virtual displays.
- Root: higher privilege than Shizuku, with a root shell and more system-level operations.

Accessibility alone covers most everyday automation. Shizuku or Root is only needed for virtual displays, silent installs, system settings changes, or shell commands.

You can also pair another phone with Focus on the same local network. Once paired, you can view its screen, tap, type, and run commands. You can pair multiple phones. With enough concurrency, you can even build an automation cluster.

Virtual displays require Android 10 or later and Shizuku or Root. The virtual display appears in a floating window and you can take over at any time. Watching it yourself can be laggy. It is mainly for observation and does not affect automation.

Bridge is a resident service for your computer. It is best configured as a background service that starts on boot. Once paired, Focus can:

- Read, write, edit, and search workspace files.
- Run shell commands and open persistent terminals.
- Run long commands in the background, notify you when they finish, and optionally start a new turn.
- View images and videos, up to 20 MB per file.
- Read AGENTS.md and CLAUDE.md from the workspace.

Start it with:

```sh
cd bridge
cargo build --release
./target/release/focus-bridge serve
```

Bridge prints a six-digit pairing code. The code is valid for five minutes and can be used once. After pairing, the device authorization stays valid. See [bridge/README.md](bridge/README.md) for commands and API details. It is best to let an AI read it.

## Automation

Focus supports several triggers, so you do not have to stay around, as long as Android does not kill the background process.

- Cron: five- and six-field expressions, plus Quartz syntax such as L, W, and #.
- Webhook: each trigger has its own URL that external apps can call. Settings shows the actual port.
- Device triggers: match by app, on-screen text, or event type, then run a shell command, send a notification, or start a chat.
- Background commands: Bridge and on-device background tasks can notify you when they finish, and can pass results back to the assistant.
- Feed refresh: new articles can enter the Router.
- Reminders: fire on time.
- Watchdog: steps in when a task is stuck or keeps calling tools.

The Router can create, pause, and delete these triggers itself. You do not need to configure them manually.

## Approvals

Focus can ask you to confirm sensitive operations. There are three policies:

- Yolo: run everything directly.
- Auto Pilot: shell commands and file changes go to a judge model first.
- Manual: every operation needs your approval.

Auto Pilot can use Jev or a regular LLM. You can set the risk threshold, judge timeout, and how many messages of context to include. Operations above the threshold, timed-out judgments, or uncertain decisions fall back to manual confirmation.

## Models

Focus can connect to multiple providers and pull model lists from their APIs.

Built-in presets include OpenAI, OpenRouter, DeepSeek, Anthropic, and TypeSafe. To keep things simple, not all of them are added by default. You can also add any compatible endpoint by filling in the Base URL, headers, authentication, and model ID.

Supported protocols:

- OpenAI Responses
- OpenAI Chat Completions
- Anthropic Messages

Different roles can use different models. The Router handles dispatch, side chats have light, standard, and heavy tiers, and subagents, context compaction, the watchdog, the safety judge, chat titles, and opening suggestions can all be assigned separately. When unset, they follow the main model. Heavy side chats fall back to standard, and subagents fall back to light.

## Plugins and skills

Plugins are APKs with the `.focusplugin` extension. They run in the same process as the host and can use Focus's Compose, Koin, and data layer directly.

Plugins can add:

- Tools and prompts
- Pages in Settings or the drawer
- Overlays
- Device access
- Chat app channels
- Automation tasks
- Notification listeners

There are two ways to write a plugin. A dex plugin uses Kotlin and Compose and gives you the most freedom. A script plugin contains only a manifest and JavaScript, which makes it suitable for the assistant to create on the spot.

You can install plugins from a file, a link, or the plugin marketplace. Installation and removal both need your approval. The plugin marketplace is not officially open yet. It will come later, once there are more users.

The assistant can also create a script plugin on the spot with `plugin_create`, then check its load state with `plugin_list` and `plugin_logs`. See [docs/plugins.md](docs/plugins.md) for the full format and `samples/plugin-hello` for a runnable example.

A skill is a folder containing `SKILL.md`. The assistant normally sees only the name and description, and loads the full content when a task matches. App skills, workspace skills, and plugin skills can all be managed and imported or exported.

MCP servers are supported too, over Streamable HTTP, HTTP with SSE, and stdio. Their tools and resources can be called in chat.

Built-in web search includes DeepSeek, Tavily, TinyFish, Exa, Bing, DuckDuckGo, and Yandex.

## Chat apps

Focus can connect chat apps and turn messages into conversations.

- Currently only WeChat and Yuanbao are supported.
- Each chat can be bound to its own conversation.
- Channels that support group chat can require an @ mention before the bot replies, and can accept messages only from the bot owner.
- You can choose whether to send reasoning and tool progress.

## Security

- API keys are encrypted with AES-GCM in Android Keystore.
- System backup is disabled. Conversations, indexes, and settings stay on the device by default. S3-compatible backup will be added later.
- Bridge uses plain HTTP and is only suitable for trusted local networks. Do not expose its port to the internet, even with a pairing code.
- Webhook URLs contain a token. Do not share them casually.
- Plugins are injected through Dex and run in the same process as the host, with the same permissions. Only install plugins you trust.
- Device control, virtual displays, and remote control all show a persistent notification and can be stopped at any time.

## Build

Requires JDK 21, Android SDK 37.0, and build tools 36.0.0. The voice input module also needs the NDK toolchain.

```sh
./gradlew :app:assembleDebug
```

By default only arm64-v8a is built. To build all ABIs:

```sh
./gradlew :app:assembleDebug -Pfocus.abis=all
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Run unit tests:

```sh
./gradlew testDebugUnitTest
```

Building Bridge requires Rust 1.82 or later:

```sh
cd bridge
cargo build --release
```

Build the sample plugin:

```sh
./gradlew :samples:plugin-hello:assembleDebug
```

## Community

Join the [QQ group](https://qm.qq.com/q/BeQPYWuzVS).

## License

MIT, see [LICENSE](LICENSE).
