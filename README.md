# 老李 Hook Tools

针对小天才手表「好友圈」应用的开源 Xposed / LSPosed 模块，提供资源替换、防删除、动态编辑等增强功能。

> 仅供个人学习与逆向技术研究使用，请勿用于任何违反目标应用服务条款或法律法规的用途。

## 功能特性

- **图片资源替换**：自定义好友圈头部背景、新消息背景
- **字符串替换**：自定义应用标题文字
- **颜色替换**：自定义标题、发布、昵称文字颜色
- **时间详细显示**：将相对时间显示为完整的 `yyyy-MM-dd HH:mm`
- **防删除**：拦截他人 / 同步删除动态，本地保留被删动态
- **动态编辑**：编辑动态文字内容、颜色、下划线、点赞数量、发布者名称（仅本地覆盖显示）
- **评论编辑**：编辑评论内容、颜色、下划线、评论者名称（仅本地覆盖显示）
- **保存媒体**：保存动态中的图片、视频到相册
- **保存头像**：长按用户头像保存到相册
- **链接跳转**：动态中的链接高亮并可点击跳转浏览器（可在设置中开关）
- **一键还原**：还原所有编辑过的动态与评论
- **检查更新**：应用内检测新版本并下载、安装（依赖自建后端）
- **查看公告**：拉取后端发布的公告（依赖自建后端）

> 说明：`检查更新` 与 `查看公告` 功能依赖一个后端服务，该后端不在本仓库内（未开源），如需使用请自行搭建，并在 `Constants.API_BASE_URL` 中修改为你的服务地址。

## 目标应用

- 应用名称：小天才手表「好友圈」
- 包名：`com.xtc.moment`

## 目录结构

```
.
├── app/
│   ├── src/main/
│   │   ├── assets/xposed_init        # Xposed 入口声明
│   │   ├── java/com/laoli/hooktools/
│   │   │   ├── hook/                 # 核心 Hook 逻辑
│   │   │   ├── ui/                   # 界面(主界面 / 日志 / 颜色 / 公告)
│   │   │   ├── util/                 # 工具类(日志 / 更新 / 图片 / 常量)
│   │   │   ├── prefs/                # 配置存储
│   │   │   └── provider/             # 激活状态回传
│   │   └── res/                      # 资源
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradle/wrapper/
```

## 环境要求

- 设备已 root，并安装 [LSPosed](https://github.com/LSPosed/LSPosed) / EdXposed 框架
- 构建环境：JDK 17+、Android SDK（compileSdk 34）、Gradle

## 构建

用 Android Studio 打开本目录直接构建，或用命令行：

```bash
gradle assembleDebug      # 调试版
gradle assembleRelease    # 正式版(需自行签名)
```

> Xposed API 以 `compileOnly` 引入，不打包进 APK。

## 使用说明

1. 安装编译好的 APK。
2. 在 LSPosed 中启用本模块，并勾选作用域「好友圈」(`com.xtc.moment`)。
3. 重启「好友圈」应用。
4. 打开本模块 App 进行功能开关与资源配置。

## 技术栈

- Kotlin、Xposed API、AndroidX、Material Design 3、Glide

## 免责声明

本模块通过 Hook 修改第三方应用行为，仅用于个人学习与技术研究。使用本模块可能违反目标应用的服务条款，并可能带来账号封禁等风险，请自行评估并承担相应后果。作者不对因使用本模块产生的任何问题负责。

## 许可证

本项目采用 [GNU General Public License v3.0 (GPL-3.0)](https://www.gnu.org/licenses/gpl-3.0.html) 开源。

## 联系

- 作者：老李不会飞
- 交流 QQ 群：746269236
