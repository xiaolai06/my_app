# My Life · 安卓工程

正式 Kotlin/Compose 单 `app` 模块，核心数据使用本机 Room、DataStore 和内部附件目录。当前版本 **0.10.0-dev / versionCode 11**，开发包名 `com.example.dailylife.debug`。

## 功能与使用

| 入口 | 已接入的功能 |
| --- | --- |
| 首页 | 原业务聚合、日期、近期事项、账本与记录、可选城市天气 |
| 记录 | 随记、日记、图片/文件、收藏、信息卡、Markdown、分类、标签、手账样式 |
| 计划 | 任务/子任务、完整月历、日程/重复实例、习惯打卡、专注计时、目标/里程碑 |
| 账本 | 收支、转账/手续费、退款、报销/回款、账户、预算、统计、CSV 导入 |
| 我的 | 个人设置、未完成编辑、清单、纪念日、订阅、缴费、衣橱、穿搭、物品、借还、专题与计算 |

记录、计划、账本/报销和生活事项主编辑返回或“稍后继续”会先保留输入，实际暂存成功才退出，失败留在原页；同一内容反复退出更新同一份草稿。在“我的”查看摘要并继续，正式保存后移除，草稿不参与统计。

普通文件导入导出集中在“我的 → 数据与备份”，包括账目 CSV、记录 Markdown 与附件原件。导出读取最新正式内容。领域 ZIP 备份与追加恢复也在这里；卸载前应先备份。

五入口、内页、弹层使用公共原生玻璃组件与选择器，支持浅深主题、大字体和减少动态。API31+ 导航使用 Haze 背景模糊，其余为静态玻璃材质；物理折射和设备 GPU 表现尚未验收。详细功能与最新验证见 [任务与交接](../docs/ai/开发任务与交接.md#functional-status)，视觉以 [设计系统](../design-system/daily-life/MASTER.md) 为准。

## 数据规则

- 页面 → ViewModel → Repository；页面不直接操作 DAO、DataStore 或文件。Room 与 DataStore 保持单实例。
- 稳定 UUID，金额为 Long 整数分，日期与时间点分开。财务写入统一处理交易、分录、关系和日志，事务内重查额度、版本与幂等约束。
- Room schema **4**，保留 schema 1–4 与 AutoMigration 1→2→3→4；旧库升级前保留内部副本，不启用破坏性迁移。
- 原附件保存在应用内部；永久删除检查全部引用与财务依赖。提醒按系统权限反馈，实际到点仍受设备能力与省电策略影响。
- 无账号、服务器或云同步；联网仅用于用户主动开启的城市天气。文件经系统选择器与 FileProvider 交换，无广泛存储或定位权限。

### 备份兼容与边界

| 领域 | 当前导出 | 读取兼容 |
| --- | --- | --- |
| 记录 | version 2 | version 1 / 2 |
| 计划 | version 1 / schema 2 | version 1 |
| 账本 | version 2 / schema 3 | version 1 / 2 |
| 工具 | version 2 | version 1 / 2 |

ZIP 的领域 schema 标记与 Room 数据库版本分别管理。恢复先校验版本、关系、路径、大小和哈希，再事务追加；已有编号保留本机版本。建议先恢复记录，再恢复计划和账本，最后恢复工具；缺失的账目关联不会自动补绑。账本最多 10000 笔交易，工具最多 5000 个主项；各自合计最多 50000 行、1000 个附件/256MB，清单最多 10MB。

ZIP 未加密，可能包含真实金额和私密资料。四个独立领域包不等于跨领域同快照或整库覆盖恢复，数据库事务也不等于多文件原子切换。详细约束见 [工程指南](../docs/安卓本地版工程结构与开发指南.md#backup)。

## 工具链

| 项目 | 固定版本/配置 |
| --- | --- |
| Gradle / AGP | 8.13 / 8.12.2 |
| Kotlin / Compose 编译插件 | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2025.08.00 |
| Room / Lifecycle / DataStore | 2.7.2 / 2.9.2 / 1.1.7 |
| CommonMark / Haze | 0.24.0 / 1.6.10 |
| 本地 Android 测试 | Robolectric 4.16.1，API 35 |
| Android / JVM | min 26、compile/target 36、JVM 17 |

Haze 的许可与 NOTICE 保留在 `app/src/main/assets/licenses/`。依赖沿用已验证组合，不因版本更新提示整体升级。

## 构建与验证

在 Android Studio 打开本目录，Gradle JDK 推荐选择 Java 21，配置本机 SDK 36。`local.properties` 不提交。也可在仓库根目录运行：

```powershell
.\tools\Invoke-AndroidBuild.ps1 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
.\tools\Invoke-AndroidBuild.ps1 :app:assembleDebugAndroidTest --console=plain
# 需要可用模拟器或已授权的测试手机
.\tools\Invoke-AndroidBuild.ps1 :app:connectedDebugAndroidTest --console=plain
# 只检查文档
.\tools\Test-ProjectDocs.ps1
```

`src/repositoryTest/java` 的同一套仓库用例编入本地与设备测试；`src/test/java` 还包括规则、ViewModel、并发和原生本地 UI 测试。Robolectric 验证不等于真实设备验收。首次构建需下载固定依赖，SDK/JDK 路径由 [环境脚本](../tools/README.md) 探测或显式指定。

安装包：`app/build/outputs/apk/debug/app-debug.apk`。本地测试报告：`app/build/reports/tests/testDebugUnitTest/`；设备报告：`app/build/reports/androidTests/connected/debug/`；静态检查：`app/build/reports/lint-results-debug.html`。

## 待完成

应用锁/后台私密预览、跨领域完整备份与覆盖恢复、内容合并与部分记录附件能力、正式包名与签名、覆盖更新、万条数据性能，以及 OPPO Find X9 Pro 的输入法、系统文件选择器、TalkBack、真实后台计时与通知验收。逐项范围和状态见 [任务清单](../docs/ai/开发任务与交接.md#3-任务清单)。
