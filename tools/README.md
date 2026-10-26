# 本地工具用途

在 `D:/desktop/myapp` 的 PowerShell 中使用。工具按用途区分，环境项目通过不能作为业务 App 的完成证据。

| 文件 | 用途 | 是否修改环境或运行设备 |
| --- | --- | --- |
| `Enter-AndroidEnvironment.ps1` | 为当前终端设置 SDK/JDK 路径 | 只影响当前进程环境 |
| `Invoke-AndroidBuild.ps1` | 构建 `android-environment-check` | 生成环境验收产物，不构建正式工程 |
| `Test-AndroidEnvironment.ps1` | 读取工具链、SDK、设备与虚拟化状态 | 写 `diagnostics/` 报告；不是纯文档检查 |
| `Verify-AndroidApp.ps1` | 启动专用模拟器并安装验证环境 App | 操作模拟器与环境包，可能准备专用设备 |
| `Configure-AndroidEnvironment.ps1` | 初次配置工具和环境 | 会修改本机环境，非日常开发必跑步骤 |
| `Restore-AndroidEnvironment.ps1` | 按备份回退本机环境配置 | 会修改本机环境，仅用于相应恢复需求 |
| `Test-ProjectDocs.ps1` | 检查本地 Markdown 链接、结构和 V3 JSON | 只读检查，不联网、不构建、不操作设备 |

## 日常使用

```powershell
# 文档整理后的检查
.\tools\Test-ProjectDocs.ps1

# 机器可读结果
.\tools\Test-ProjectDocs.ps1 -AsJson

# 正式工程创建后，先进入同一套工具环境
. .\tools\Enter-AndroidEnvironment.ps1
Set-Location -LiteralPath '.\daily-life-android'
.\gradlew.bat :app:assembleDebug --console=plain
```

正式业务工程尚未创建；上面的业务构建命令仅供之后使用。完整构建、测试、安装和签名操作见 [工程指南](../docs/安卓本地版工程结构与开发指南.md#build)；现有环境验收记录见 [环境报告](../docs/安卓开发环境配置与验证.md)。
