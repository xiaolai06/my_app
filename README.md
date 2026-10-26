# 日常 · 个人安卓 App

给自己使用的安卓个人生活管理 App，采用 Kotlin、Compose、Room 和 DataStore。核心数据在本机，无需账号或服务器。完整功能为“今天、记录、计划、账本、功能箱”五入口，头像进入设置。

## 当前状态

- 已有：完整功能与数据模型、安卓本地方案、五入口 UI/UX V3.2 和设计变量。
- 已实现：[正式安卓工程与五入口业务页面](daily-life-android/README.md)。记录、计划接入真实 Room 数据；首页汇总原业务，账本含收支、转账、退款、报销、预算和 CSV，功能箱含生活清单、纪念日、订阅缴费、物品借还、专题与计算子页。各领域分别追加备份恢复。
- 验证范围与剩余工作见 [任务与交接](docs/ai/开发任务与交接.md)；增强玻璃、整库覆盖恢复、正式签名和目标手机验收仍待完成。

后续状态与下一步统一维护在 [开发任务与交接](docs/ai/开发任务与交接.md)，不要将网页预览、环境程序或计划目录当成已实现的 App。

## 从这里开始

| 使用者要做什么 | 入口 |
| --- | --- |
| 让 AI 接手开发 | [AGENTS.md](AGENTS.md) → [AI 开发执行说明](docs/ai/AI开发执行说明.md) → [任务与交接](docs/ai/开发任务与交接.md) |
| 查看全部有效文档和优先级 | [文档索引](docs/README.md) |
| 查每个功能、字段、模型与开源参考 | [完整功能与数据模型 V2.0](docs/个人生活管理App_完整功能与数据模型设计_V2.0.md) |
| 看技术、目录和开发打包方式 | [技术与存储](docs/安卓本地版技术与存储方案.md)、[工程结构与开发指南](docs/安卓本地版工程结构与开发指南.md) |
| 实现页面、组件、动效与适配 | [UI/UX V3](docs/iOS风格安卓UI与UX动效设计_V3.md)、[设计系统](design-system/daily-life/MASTER.md) |
| 使用本机工具 | [工具说明](tools/README.md)、[环境报告](docs/安卓开发环境配置与验证.md) |

## 文件夹说明

```text
myapp/
├─ AGENTS.md                   AI 项目规则与有效依据
├─ README.md                   项目入口
├─ docs/                       当前功能、技术、工程、UI 和环境文档
│  ├─ ai/                      执行说明、开发任务与交接
│  ├─ research/                玻璃实现、Apple 资源等研究记录
│  └─ archive/                 旧三入口方案，仅历史参考
├─ design-system/daily-life/   V3 组件约定与设计变量
├─ tools/                      环境工具、只读文档检查
├─ examples/                   独立 Compose 材质示例
├─ references/                 视频帧和原始 Web 参考包
├─ diagnostics/                本机日志与验收材料，不提交
├─ android-environment-check/  环境验收程序
└─ daily-life-android/         正式原生工程，五入口业务页面
```

## 交给 AI 的开始说明

```text
请先读取 AGENTS.md、docs/README.md 和 docs/ai/开发任务与交接.md。
本次授权范围是：[填写 DEV 编号或阶段]。
按当前有效的功能 V2、UI V3 和安卓本地工程方案完成实现与相关验证。
不要使用历史三入口规格，不要把环境程序当成业务工程。
更新任务状态、实际验证结果和下一步；不能将未执行的检查标为通过。
```

正式工程已建立。后续接手先查看任务进度，不重复创建工程；五入口已接入本地业务，完整个人版的发布、性能和公共能力仍按任务清单推进。

## 获取源码

仓库：[xiaolai06/my_app](https://github.com/xiaolai06/my_app)。克隆后在 Android Studio 中打开 `daily-life-android/`，配置本机 Android SDK 与 JDK 17，按 [正式工程说明](daily-life-android/README.md) 构建。`local.properties` 由本机生成，不提交到仓库。

仓库包含源码、测试、数据库 schema、设计变量与文档；`diagnostics/` 中的本机日志、截图和安装包，以及许可尚未确认的外部参考原件不随源码上传。文档中指向这些本机验收材料的链接在新克隆中可能不可用，历史验证记录仍按当次实际结果保留。

## 文档检查

在工作区 PowerShell 中运行 `./tools/Test-ProjectDocs.ps1`，检查本地链接、Markdown 结构和设计 JSON。它不会运行安卓构建或操作手机。
