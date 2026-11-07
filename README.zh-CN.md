<div align="center">

# Focus

您的下一代私人 LLM 助理

[![CI](https://github.com/Celvra/Focus/actions/workflows/ci.yml/badge.svg)](https://github.com/Celvra/Focus/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com)

[English](README.md)

</div>

## 社区

[QQ](https://qm.qq.com/q/BeQPYWuzVS)

## 为什么选择它

它可以接入你的 RSS 和 Atom 订阅，使得你每天都能了解全新的事物

像 Muse 一样，它可以为你提供每日目标、动态、点子。

同时，也可以通过 Focus Bridge，连接你的电脑，并操作文件和终端。

它可以通过无障碍、root 或 Shizuku 控制这台手机。另一台 Focus 手机可以在局域网内配对，然后用同一套工具控制。助手可以读取屏幕、点击、输入、执行 shell 命令、管理文件、应用、设置和重启设备。

插件可以添加工具、页面、悬浮窗、设备能力、消息渠道和自动化。见 `docs/plugins.md`。

## 助手

- 管理目标、计划、提醒
- 保存想法、更新和记忆
- 读写已连接文件夹里的文件
- 添加和删除订阅，读取和搜索文章
- 列出供应商、测试连通性、切换模型
- 在已配对的电脑上执行命令、打开终端、编辑文件
- 通过无障碍、root shell 和 Shizuku 控制本机或已配对手机

当启用 Auto Pilot 时，接入 Jev / LLMs 自动判断安全性

设备控制在“设备”页面。开启 Focus 无障碍服务，按需授权 root 或 Shizuku，然后让助手使用设备工具。远程控制使用配对码和信任列表。

长任务可以使用持久 shell 会话、后台命令、无障碍事件等待、WebSocket 实时屏幕预览和常驻事件触发器。后台命令完成后会通知用户，并自动开始新一轮助手回复。触发器可以执行 shell 命令、发送通知，或直接开始新一轮助手回复。

## 模型

Focus 可以接入多个提供商，并从提供商拉取模型。

我们支持 OpenAI Response, Chat Completions, Anthropic Messages。

## 构建

需要 JDK 21 和 Android SDK 37.0。

## Bridge

Bridge 可以连接你的 Focus，并让 Focus 操控电脑。

## 许可证

MIT，见 `LICENSE`。
