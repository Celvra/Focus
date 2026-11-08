<div align="center">

# Focus

你的下一代私人 LLM 助理

[![CI](https://github.com/Celvra/Focus/actions/workflows/ci.yml/badge.svg)](https://github.com/Celvra/Focus/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com)

[English](README.en.md) · [QQ 交流群](https://qm.qq.com/q/BeQPYWuzVS)

</div>

Focus 是跑在 Android 上的私人助理。它不只是聊天框，还能记住你的事、管理目标和提醒、读订阅和文件、控制手机，也可以通过 Bridge 操作电脑。

## 下载

最新版本在 [Releases](https://github.com/Celvra/Focus/releases/latest)。

- APK 支持 Android 8.0 及以上，推荐 Android 13 及以上。
- Release 里同时提供 Windows、macOS 和 Linux 的 Bridge。

## 第一次打开

1. 添加一个模型提供商。填 Base URL、API Key，选择协议，然后选择模型。
2. 按向导完成初始设置。
3. 建议开启自启动、无限制后台和后台锁定。

日历、麦克风、通知这些权限，以及无障碍、Shizuku、Stellar、Root 这些能力，都可以按需开启，不开启也能正常聊天。

## 日常

Focus 有一个今日页，把每天值得看的东西放在一起。

- 动态会从你订阅的 RSS 和 Atom 里整理，喜欢的可以保留，不感兴趣的可以去掉。
- 点子会根据你的目标和最近在做的事生成，可以直接变成任务。
- 目标支持进行中、暂停、完成，也能设置截止时间。
- 提醒到点会发通知，也可以让 Focus 帮你安排。
- 资料库可以看文章、管理订阅、读写已连接的文件夹。
- 记忆和用户资料会跨对话保留并自我迭代，助手也会记住你希望它叫什么。

日历事件、文件、订阅和文章都能直接交给助手处理，不需要你手动复制粘贴。

## 主会话和旁聊

主会话是普通聊天，自己直接使用工具，也能把可以并行的任务派给旁聊。

旁聊和主会话并行运行，有自己的模型、工具和完整过程。完成后结果会回到主会话，由主会话决定是否告诉你。旁聊也可以直接提升为主会话，旧主会话会降为旁聊。

- 可以限制同时运行的旁聊数量，多出来的会排队。
- 附件会自动带给旁聊，图片和文件不需要重复上传。
- 看门狗会处理超时、重复调用工具和卡住的任务。
- 语音助手和悬浮助手都走当前主会话。
- 主会话和旁聊使用当前选择的模型，压缩、判定、标题、建议可以单独配置辅助模型。
- 助手也能创建和管理定时任务、Webhook、桌面快捷方式，不需要你进设置页操作。

## 随手可用

- 悬浮助手可以停在任意应用上方，直接提问，不用来回切换。
- 可以把当前屏幕附给助手，也可以手绘一块区域再问，适合看图、翻译、提取文字，类似即圈即搜。
- 语音输入支持本地离线模型，也支持云端转写。
- 下拉快捷设置里有 Focus 磁贴。
- 桌面有 4x2 微件，会显示最近回复和运行状态。
- 长按应用图标，或者让助手创建桌面快捷方式，都能快速打开 Focus。

Focus 也是一个 Xposed 模块，但目前只用于 ColorOS 15 的电源键劫持，以替换小布助手。由于我没有其他设备，暂时无法开发其他系统的劫持模块。

在 AOSP 或类原生系统上，只需把 Focus 配置为语音助理，并开启电源键唤出语音助理，就能长按电源键打开 Focus。

## 手机和电脑

本机控制支持三种方式：

- 无障碍服务：读屏、点按、长按、滑动、输入、按键、打开应用、截图，以及监听界面和通知事件。
- Shizuku、Stellar 这类兼容 Shizuku API 的工具：shell、后台命令、文件、应用、设置、虚拟屏。
- Root：比 Shizuku 权限更高，能用 root shell 和更多系统级操作。

只用无障碍也能完成大部分日常自动化。需要虚拟屏、静默安装、修改系统设置或运行 shell 时，才需要 Shizuku 或 Root。

同一局域网里还可以配对另一台已安装 Focus 的手机。配对后可以查看它的屏幕、点击、输入、运行命令。你可以配对多台手机，只要并发足够，甚至可以组成自动化集群。

虚拟屏需要 Android 10 及以上，并且要有 Shizuku 或 Root。虚拟屏会显示在悬浮小窗里，你可以随时接管。自己看会有点卡，主要用于观察，对助手的自动化没有影响。

Bridge 是电脑端的常驻服务，建议配置为开机自启的后台服务。配对后 Focus 可以：

- 读写、编辑、搜索工作区文件。
- 运行 shell 命令，打开持久终端。
- 在后台运行长命令，结束后通知你，也可以自动开始新一轮对话。
- 查看图片和视频，单个文件最大 20 MB。
- 读取工作区里的 AGENTS.md 和 CLAUDE.md。

启动方式：

```sh
cd bridge
cargo build --release
./target/release/focus-bridge serve
```

Bridge 会打印六位配对码。配对码五分钟内有效，只能使用一次；配对成功后，设备授权会长期保留。详细命令和 API 见 [bridge/README.md](bridge/README.md)，建议直接交给 AI 阅读。

## 自动运行

Focus 支持多种触发器，不用你一直守在旁边，前提是 Android 没把后台进程杀掉。

- Cron：支持五段和六段表达式，也支持 L、W、# 这些 Quartz 写法。
- Webhook：每个触发器有自己的地址，外部应用可以直接调用。设置页会显示实际端口。
- 设备触发器：按应用、界面文字或事件类型匹配，可以执行 shell、发通知，或者直接开始一段对话。
- 后台命令：Bridge 和手机上的后台任务结束后可以通知，也可以把结果交给助手继续处理。
- 订阅刷新：有新文章时会在主会话提醒。
- 提醒：到点触发，不会漏掉。
- 看门狗：任务卡住或反复调用工具时会介入。

触发器可以选主会话或旁聊。助手也能自己创建、暂停、删除，不需要你手动配置。

## 审批

执行敏感操作前，可以让你先确认。Focus 提供三种策略：

- Yolo：全部直接执行。
- Auto Pilot：shell 命令和文件改动先交给判定模型检查。
- 手动：每次操作都需要你批准。

Auto Pilot 可以使用 Jev，也可以改用普通 LLM 判断。你可以设置风险阈值、判定超时和带多少条上下文。超过阈值、超时或者拿不准的操作，都会转成手动确认。

## 模型

Focus 可以接入多个提供商，也可以从接口拉取模型列表。

内置预设包括 OpenAI、OpenRouter、DeepSeek、Anthropic 和 TypeSafe。为了简洁，默认不会添加这么多提供商。也可以自己添加任意兼容接口，填 Base URL、请求头、认证方式和模型 ID 即可。

支持的协议：

- OpenAI Responses
- OpenAI Chat Completions
- Anthropic Messages

主会话和旁聊使用当前选择的模型。上下文压缩、安全判断、会话标题和开场建议可以单独指定辅助模型，没有指定时跟随当前模型。

## 插件和技能

插件是一个后缀为 `.focusplugin` 的 APK。它和宿主同进程运行，可以直接使用 Focus 的 Compose、Koin 和数据层。

插件可以添加：

- 工具和提示词
- 设置页或抽屉里的页面
- 悬浮窗
- 设备访问
- 聊天应用渠道
- 自动化任务
- 通知监听

插件有两种写法。dex 插件自己写 Kotlin 和 Compose，自由度高。script 插件只包含清单和 JavaScript，适合让助手现场创建。

安装可以从文件、链接或插件市场进行。安装和卸载都需要你批准。插件市场还没正式开放，等用户多起来再说。

助手也能用 `plugin_create` 当场写一个脚本插件，再用 `plugin_list` 和 `plugin_logs` 检查加载状态。完整格式和示例见 [docs/plugins.md](docs/plugins.md)，可运行的示例在 `samples/plugin-hello`。

技能是一个包含 `SKILL.md` 的文件夹。助手平时只看到名称和描述，任务匹配时才加载完整内容。应用技能、工作区技能和插件技能都可以管理，也支持导入导出。

MCP 服务器同样可以接入，支持 Streamable HTTP、HTTP 加 SSE 和 stdio，工具和资源都能在对话里调用。

网页搜索内置 DeepSeek、Tavily、TinyFish、Exa、Bing、DuckDuckGo、Yandex。

## 聊天应用

Focus 可以把聊天应用接进来，让消息直接变成对话。

- 目前仅支持微信和元宝。
- 每个聊天可以绑定到自己的对话，互不干扰。
- 支持群聊的渠道可以设置必须 @ 机器人才回复，也可以只接受机器人所有者的消息。
- 可以选择是否发送思考过程和工具进度。

## 安全

- API Key 用 Android Keystore 的 AES-GCM 加密保存。
- 应用关闭了系统备份，对话、索引和设置默认留在本机。后续会加入兼容 S3 的备份。
- Bridge 走明文 HTTP，只适合可信局域网。即使有配对码，也不要把端口暴露到公网。
- Webhook 地址自带令牌，不要随意分享。
- 插件通过 Dex 注入，与宿主同进程运行，权限和宿主相同。只安装你信任的插件。
- 设备控制、虚拟屏和远程控制都会显示常驻通知，可以随时停止。

## 自己编译

需要 JDK 21、Android SDK 37.0 和构建工具 36.0.0。语音输入部分还需要 NDK 工具链。

```sh
./gradlew :app:assembleDebug
```

默认只构建 arm64-v8a。需要全部架构时运行：

```sh
./gradlew :app:assembleDebug -Pfocus.abis=all
```

APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

跑单元测试：

```sh
./gradlew testDebugUnitTest
```

编译 Bridge 需要 Rust 1.82 及以上：

```sh
cd bridge
cargo build --release
```

编译示例插件：

```sh
./gradlew :samples:plugin-hello:assembleDebug
```

## 交流

加入 [QQ 交流群](https://qm.qq.com/q/BeQPYWuzVS)。

## 许可证

MIT，见 [LICENSE](LICENSE)。
