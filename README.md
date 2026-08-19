# 老李 Hook Tools

Xposed / LSPosed 模块，为小天才手表系列应用提供自定义修改（仅改本地显示，不改真实数据）。

## 支持的应用

- 好友圈 `com.xtc.moment`
- 运动 `com.xtc.sport`
- 个人中心 `com.xtc.personalcenter`

## 主要功能

- 好友圈：背景 / 消息背景 / 标题文字 / 主题色 / 详细时间 / 防删除 / 链接跳转 / 自定义字体 / 动态编辑 / 点赞 / 评论 / 头像保存
- 运动：能量值 / 一键红环 / 自定义字体 / 虚拟头像 / 排行榜排名与能量
- 个人中心：昵称 / 积分 / 实名 / 自定义字体 / 昵称颜色 / 自定义背景

## 构建

- Gradle 8.9 + OpenJDK 21
- `gradle assembleRelease` 产出未签名 APK，再用 `zipalign` + `apksigner` 签名

## 版本

- 1.4
