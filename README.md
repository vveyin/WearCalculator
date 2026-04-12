# 🐾 嗷呜计算器 (WearCalculator)

![Release](https://img.shields.io/github/v/release/NickWoluff/WearCalculator?color=orange&label=最新版本)
![Platform](https://img.shields.io/badge/Platform-Wear%20OS-blue)
![Downloads](https://img.shields.io/badge/Downloads-650%2B-green)

**专为智能手表打造的高精度计算器，嗷呜！**

## ✨ 核心特性 (Features)

- 多类屏幕适配：深度适配方屏、圆屏，根据手表屏幕类型自动适配应用界面。
- 超高精度计算：底层重构为纯 BigDecimal 高精度数学引擎，彻底消除二进制浮点运算误差。
- 丝滑无界滚动：当算式或结果过长时，旋转表冠或左右滑动即可查看完整数据，再也不怕屏幕装不下。
- 防呆纠错逻辑：内置严谨的输入互锁机制，拒绝无意义的 Error 崩溃。
- 现代交互界面：遵循 Material Design 极简美学，美观易用。

## 📸 界面预览 (Screenshots)

| 🔘 圆屏 (Round) | 🔲 方屏 (Square) |
|:-------------:|:--------------:|
|               |                |

## 📥 下载与安装 (Installation)
**直接安装**：
>前往 **微思应用商店** 手表端，搜索“嗷呜计算器”下载安装。

**ADB手动安装**：
>1.  前往 [Releases](https://github.com/NickWoluff/WearCalculator/releases) 页面下载最新版本。
>2.  确保你的手表已开启**开发者模式**和**ADB 调试 / 无线调试**。
>3.  使用adb工具（如甲壳虫ADB助手、WearOS工具箱）或电脑端 ADB 命令行将 APK 推送到手表：
    ```bash
    adb install WearCalculator_vX.X.X.apk
    ```

## 🗺️ 未来计划 (Roadmap)

- [ ] 添加更多高级科学计算模式 (sin/cos/平方根)
- [ ] 历史计算记录保存与调出功能
- [ ] 更多自定义主题配色

## 💬 交流与反馈 (Community)
使用时若遇到bug，可提交至issue，或：

>加入 **嗷呜系列官方QQ交流群**：`1087525213`