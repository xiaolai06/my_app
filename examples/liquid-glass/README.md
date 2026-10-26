# 原生浅玻璃基础材质示例

`SoftGlassSurface.kt` 是一个 Compose 容器：透明渐变底色、两片静态柔光、渐变细边和外阴影。子内容独立绘制，文字不参加背景处理。

它不提供实时背景模糊、透镜折射、业务状态或自动点击语义。需要放在受控的页面背景上；文字颜色由应用主题或调用者提供。深浅模式均有参数，实际合成画面可读性仍须验证。

## 编译验证

- 日期：2026-10-24。
- 环境：现有 `android-environment-check`，JDK 21、Kotlin 2.2.10、AGP 8.12.2、Compose BOM 2025.08.00。
- 方式：使用 `diagnostics/compile-glass-example.init.gradle` 临时把本目录加入编译来源，执行 `:app:compileDebugKotlin --offline --no-configuration-cache`。
- 结果：`BUILD SUCCESSFUL in 25s`；确认生成 `com/example/dailylife/ui/glass/SoftGlassSurfaceKt.class`。
- 没有修改环境项目的持久 Gradle 配置，没有为本示例组装或安装新 APK，没有执行图形效果真机测试。

## 接入

复制 Kotlin 文件到正式项目的 `ui/glass` 目录，按实际包名调整。依赖 Compose Foundation／UI；当前环境中的 Material 3 已间接提供这些依赖。

容器根据内容决定尺寸，调用者添加自己的 padding。按钮、导航和复选框使用合适的原生交互组件和语义，不要把材质容器自身当成完整按钮。

完整实现方式见 [安卓液态玻璃效果实现说明](../../docs/research/安卓液态玻璃效果实现说明.md)。
