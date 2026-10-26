# Apple 官方设计资源获取与安卓适配说明

> 研究记录：资源连接状态和引用描述当次核对情况，正文中的三入口原型不作为当前设计。最新导航、布局与参数见 [五入口 UI/UX V3](../iOS风格安卓UI与UX动效设计_V3.md)，资料分类见 [文档索引](../README.md)。

核对日期：2026-10-24。目标产品：个人安卓 App“日常”，日常待办＋随手记录；目标设备 OPPO Find X9 Pro。

用户最新确认的目标是 iOS 系统的完整设计风格，包括组件形状、排版、导航、设置、弹层、状态反馈和 Liquid Glass 材质。此前的全组件玻璃预览属于自定义设计样板，尚不能称为已经按 Apple 官方原件复现的界面。

## 1. 先理解几个名称

| 名称 | 实际作用 | 在本项目中的状态 |
| --- | --- | --- |
| Apple Design Resources 的 UI Kit | Figma／Sketch 设计资源，用来制作界面设计稿 | 已通过用户文件连接官方库，检索目录并读取部分远程组件属性 |
| Apple UIKit／SwiftUI | Apple 平台运行时界面框架 | 不作为本安卓项目的直接依赖 |
| 本项目的 Glass UI | 自己封装的 Compose 按钮、输入框、导航、列表、开关等组件 | 已有基础材质示例；完整原生组件库待实现 |
| Backdrop | 第三方 Compose 背景效果库，提供背景模糊、透镜等绘制能力 | 待兼容验证与接入；不是 Apple 官方库 |

设计风格以公开设计规范和明确的视觉目标为依据；Compose 和 Backdrop 负责把设计做成安卓端可操作的组件。

## 2. 已核实的官方入口

- [Apple Design Resources](https://developer.apple.com/design/resources/)：官方 UI Kit、图标模板及其他设计资源。核对时，页面列出 iOS 27／iPadOS 27 UI Kit 的 Figma 与 Sketch 入口，以及 macOS 27 UI Kit。手机方案应优先查看 iOS／iPadOS，不混用桌面组件的尺寸。
- [Apple Human Interface Guidelines：Materials](https://developer.apple.com/design/human-interface-guidelines/materials)：材质及功能层、内容层的使用规则。
- [Apple：Liquid Glass 概览](https://developer.apple.com/documentation/technologyoverviews/liquid-glass)：设计和开发说明入口。
- [Apple：Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/)：官方材质与动态效果演示。
- [Apple Design Resources 许可](https://developer.apple.com/support/downloads/terms/apple-design-resources/Apple-Design-Resources-License-20230621-English.pdf)：官方原件的适用范围。

已从 Apple 官网的 UI Kit 链接核实其指向：[iOS and iPadOS 27 — Figma Community](https://www.figma.com/community/file/1651309003795292092/ios-and-ipados-27)。这里的数字是 Community 资源 ID，不能直接当作 Figma 设计文件的 fileKey。后续已通过用户提供的设计文件读取其订阅库和部分组件属性，详见第 7 节；尚未核对原资源文件的完整页面结构。官网将来更新时，以官网当时的入口为准。

## 3. 如何获取官方 Figma 设计稿

以下是官方资源的访问与复制流程；实际用途须符合所下载资源随附的许可。

1. 打开 Apple Design Resources，定位到 iOS and iPadOS。
2. 在 UI Kit 下选择 Figma。通过 Apple 官网进入，可以确认资源来源，避免把同名社区仿制品当成 Apple 原件。
3. 查看资源介绍和许可。需要复制时，登录自己的 Figma 账号，使用 Open in Figma；按当前界面选择账号、复制到草稿。按钮可能显示为 Make a copy、Duplicate 或相关文案。
4. 进入文件后，从 Pages、Assets 与组件实例查看材质、按钮、开关、导航、输入和弹层。可搜索 Material、Materials、Controls、Tab Bar 等关键词；这些是查找建议，不代表已经核实当前文件一定使用这些页面名称。
5. 对照浅色、深色和交互状态，检查尺寸、内边距、圆角、颜色、透明度、图层顺序、阴影、模糊以及组件变量。优先查看原组件的属性，不根据缩略图估算数值。

Figma 官方说明：Community 资源可以通过 Open in Figma 复制到草稿，要求 Figma 账号；副本不会自动接收原文件的后续更新。[复制 Community 文件](https://help.figma.com/hc/en-us/articles/360038510873-Duplicate-Community-files)

## 4. 截图中的 Copy as code 能做到什么

Figma 支持选择对象，再通过 Copy/Paste as → Copy as code 复制 CSS、iOS 或 Android 样式片段。具体可见选项以文件权限和当前界面为准。[Figma 复制对象与代码](https://help.figma.com/hc/en-us/articles/4409078832791-Copy-and-paste-objects)

这些片段用于辅助理解对象的外观属性，不是完整 App，也不等同于 Apple 系统的 Liquid Glass 渲染实现。它们不能直接提供完整的导航、输入、状态管理、背景采样、动态折射和系统适配。CSS 也不能直接作为原生 Compose 组件执行。

因此，截图里的流程适合解释“从设计稿检查样式”的基本思路；把复制出的 CSS 当作苹果系统玻璃效果的完整源码，会产生误解。

## 5. 官方原件在安卓上的使用边界

已查阅的 Apple Design Resources 许可第 2A／2B 条把授权用途限定为只运行于 Apple 系统的软件界面原型，并明确排除非 Apple 操作系统的软件原型，也不允许把这些设计资源嵌入软件产品。San Francisco 字体另有独立许可。具体资源若附不同条款，应以其随附许可为准。[官方许可 PDF](https://developer.apple.com/support/downloads/terms/apple-design-resources/Apple-Design-Resources-License-20230621-English.pdf)

本项目目标为安卓，因此不把官方 Figma 原组件、导出图层、字体或素材直接搬入安卓工程。安卓方案依据公开的设计原则建立独立组件和绘制实现，字体使用安卓系统字体，图标使用自绘或允许该用途的资源。

## 6. 安卓方案如何接近 iOS 系统风格

整套界面统一处理，而不是只改变背景透明度：

| 范围 | 安卓端要实现的设计 |
| --- | --- |
| 页面组织 | 明确标题与分组、单列清单、统一间距与内容层级 |
| 底部导航 | 保留用户已确认的三个固定等宽入口，上图标下文字，位置与点击目标稳定 |
| 按钮、筛选、开关 | 一致的圆角与尺寸关系，清楚的按压、选中、禁用和焦点状态 |
| 搜索与输入 | 一致的形状、内边距与文字层级，正确处理中文输入、光标、选择与键盘 |
| 设置与记录 | 内容有清楚分组和阅读层次，使用较稳定的材质，避免文字受背景扰动 |
| 弹层和菜单 | 连贯的圆角、遮罩、来源关系及关闭行为 |
| 动态材质 | 在需要的功能层实现背景柔化、边缘高光及经过验证的轻折射 |

Apple 的公开规范区分 Liquid Glass 功能层与标准材质内容层，强调不要在内容层滥用或层层叠加 Liquid Glass。[Materials](https://developer.apple.com/design/human-interface-guidelines/materials)

因此，“全部组件统一 iOS 风格”可以覆盖整套组件；严格按 Apple 规范设计时，各组件应使用符合用途的材质，而非同样强度的透明和折射。保留用户的全组件统一要求，具体材质层级以清晰阅读和稳定操作为目标。当前预览保留，下一次视觉调整需要明确呈现与当前自定义玻璃样板的变化。

## 7. 原生实现与连接状态

原生实现继续采用 Kotlin＋Jetpack Compose，在公开基础 API 和 Material 3 基础能力上封装产品组件；Backdrop 作为可评估的背景效果后端，不作为视觉设计来源，也不承诺与 Apple 系统逐像素一致。[Backdrop 效果说明](https://kyant.gitbook.io/backdrop/api/backdrop-effects)

已实际读取用户提供的 [Untitled 设计文件](https://www.figma.com/design/COI2sXyW7DSYZbrydfq79E/Untitled?node-id=0-1)。文件目前只有 `Page 1`（节点 `0:1`），画布子节点为 0；但它已启用 `iOS and iPadOS 27` 官方库。画布为空不等于组件库没有连接。

使用 Figma 组件库检索后，已确认存在 `Button - Liquid Glass - Text`、`Button - Liquid Glass - Symbol`、`Tab Bar - iPhone`、`Toolbar - Bottom - iPhone`、`Button - Content Area` 等条目。随后通过远程组件加载接口读取了以下三个组件集的属性，没有创建画布实例；读取前后的画布子节点均为 0。

| 已读取组件 | 读取到的变体与参数 |
| --- | --- |
| Button - Liquid Glass - Text | 24 个变体；Small／Medium／Large，Glass／Glass Prominent，启用／禁用，破坏性操作／普通操作。样本高度分别为 28／34／50；默认 Large 样本为 85×50，左右内边距 20、上下 16 |
| Button - Liquid Glass - Symbol | 8 个变体；Glass／Glass Prominent，启用／禁用，破坏性操作／普通操作。已读取默认样本为 50×50 |
| Tab Bar - iPhone | 24 个变体；2／3／4／5 个入口，普通／最小化状态，Default／Search Role／Prominent Tab 类型。默认的 2 入口、非最小化样本外框为 402×95，内部按钮区为 180×54 |

这些数值是当前 Figma 变体的设计坐标，宽度也受示例文字和容器影响，不是所有控件必须采用的固定尺寸，不能直接当成安卓像素或无需适配的 dp。当前已读取到部分外层布局和嵌套实例信息，尚未取得完整背景材质、全部嵌套图层和动态渲染实现。

当前连接与组件检索验证已经完成，用户无需为此重新登录、公开文件或重新复制整套资源。如需在 Figma 画布上直观看到组件，可打开左侧 Assets／资源，选择 `iOS and iPadOS 27`，将上述组件拖到画布，再在右侧切换变体；只查看库与属性不要求先完成这一步。获取和使用资源仍须遵守第 5 节所述许可。

已完成：公开资源核对、文件访问验证、已启用库核对、组件目录检索及三个组件集的部分属性读取。尚未完成：完整材质与嵌套图层核对、原生组件实现、增强效果接入及 OPPO 真机验证。
