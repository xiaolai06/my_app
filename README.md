# My Life

个人使用的安卓生活管理 App，采用 Kotlin、Jetpack Compose、Room 和 DataStore。核心数据保存在本机，无需账号或服务器；天气为主动开启的可选联网功能。

## 当前功能

当前开发版本 **0.10.0-dev**（versionCode 11），五个入口为“首页、记录、计划、账本、我的”。

- 首页：汇总原任务、日程、生活事项、账本与近期记录。
- 记录：随记、日记、文件、收藏、信息卡，支持 Markdown、附件、分类和回收站。
- 计划：任务、日历、习惯、专注、目标与本地提醒。
- 账本：收支、账户、转账、退款、报销、预算、统计与 CSV 导入。
- 我的：个人设置、未完成编辑、生活工具，以及领域备份、恢复和普通文件导入导出。

四个领域主编辑退出时自动保留输入，在“我的”继续；正式保存后移除对应草稿。日期选择默认完整月历。功能实现、验证记录和未完成项统一见 [任务与交接](docs/ai/开发任务与交接.md)。

## 获取与构建

仓库：[xiaolai06/my_app](https://github.com/xiaolai06/my_app)。在 Android Studio 打开 **daily-life-android/**，配置 Android SDK 36 和兼容的 JDK（推荐 21，编译目标为 17）。在仓库根目录运行：

```powershell
.\tools\Invoke-AndroidBuild.ps1 :app:assembleDebug --console=plain
```

安装包生成于 `daily-life-android/app/build/outputs/apk/debug/app-debug.apk`。源码、依赖与测试说明见 [正式工程 README](daily-life-android/README.md)。

当前使用调试签名。目标手机、TalkBack、真实后台通知、性能和正式覆盖更新尚未完成验收；应用锁与跨领域整库覆盖恢复仍待实现。已有四领域独立备份与追加恢复，不等同于整库恢复。

## 开发入口

| 内容 | 入口 |
| --- | --- |
| 项目规则 | [AGENTS.md](AGENTS.md) |
| 当前任务、验证与下一步 | [开发任务与交接](docs/ai/开发任务与交接.md) |
| 功能、存储、工程与 UI 依据 | [文档索引](docs/README.md) |
| 组件与设计变量 | [设计系统](design-system/daily-life/MASTER.md) |
| 构建与检查脚本 | [工具说明](tools/README.md) |

正式业务只在 `daily-life-android/`；`android-environment-check/` 是独立的环境验收程序。`examples/` 为材质示例，`references/` 为本地参考资料说明。

仓库保留源码、测试、数据库 schema、Gradle Wrapper 和维护文档。安装包、构建缓存、本机配置、日志、真实数据、签名凭据和许可未确认的外部参考原件均不上传。历史本机证据以文本路径记录，新克隆无需这些文件即可构建。
