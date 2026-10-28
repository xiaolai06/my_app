# 本地工具用途

在项目根目录的 PowerShell 中使用。工具按用途区分，环境项目通过不能作为业务 App 的完成证据。

| 文件 | 用途 | 是否修改环境或运行设备 |
| --- | --- | --- |
| `Enter-AndroidEnvironment.ps1` | 自动探测或显式指定 SDK/JDK，检查 Java 兼容性 | 只影响当前进程环境 |
| `Invoke-AndroidBuild.ps1` | 默认构建正式工程 `daily-life-android`；可通过 `-ProjectPath` 选择其他工程 | 生成指定工程产物；只有 connected 测试任务才操作已连接设备 |
| `Test-AndroidEnvironment.ps1` | 读取工具链、SDK、设备与虚拟化状态 | 写 `diagnostics/` 报告；不是纯文档检查 |
| `Verify-AndroidApp.ps1` | 启动专用模拟器并安装验证环境 App | 操作模拟器与环境包，可能准备专用设备 |
| `Configure-AndroidEnvironment.ps1` | 初次配置工具和环境 | 会修改本机环境，非日常开发必跑步骤 |
| `Restore-AndroidEnvironment.ps1` | 按备份回退本机环境配置 | 会修改本机环境，仅用于相应恢复需求 |
| `Test-ProjectDocs.ps1` | 检查维护文档的本地链接、结构和 V3 JSON；跳过被忽略的外部参考包 | 只读检查，不联网、不构建、不操作设备 |

## 日常使用

```powershell
# 文档整理后的检查
.\tools\Test-ProjectDocs.ps1

# 机器可读结果
.\tools\Test-ProjectDocs.ps1 -AsJson

# 正式业务工程：构建、无需模拟器的测试和静态检查
.\tools\Invoke-AndroidBuild.ps1 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain

# 需要手动指定路径时（替换为实际安装位置）
.\tools\Enter-AndroidEnvironment.ps1 -SdkPath 'C:\Android\Sdk' -JdkPath 'C:\Java\jdk-21'

# 只验证旧环境工程时必须明确指定
.\tools\Invoke-AndroidBuild.ps1 -ProjectPath '.\android-environment-check' assembleDebug --console=plain
```

正式业务工程已经存在。脚本优先使用兼容的 `JAVA_HOME`，然后探测项目 `android-setup/jdk21` 中的便携 JDK 和本机常见安装位置；不会自动安装或修改系统环境。Gradle 8.13 的启动 JDK 支持 17–23，本机验证使用 Java 21；JVM 编译目标仍为 17。Android Studio 自带 JDK 的版本不一定兼容此项目，不能直接假定可用。[Gradle 兼容表](https://docs.gradle.org/current/userguide/compatibility.html)

完整构建、测试、安装和签名操作见 [工程指南](../docs/安卓本地版工程结构与开发指南.md#build)；本轮实际结果见 [任务与交接](../docs/ai/开发任务与交接.md)。
