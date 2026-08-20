---
title: DESIGN
status: complete
created: '2026-08-17'
source: ../ux-design-specification.md
---

# DESIGN

本文件是轻量化开放平台客户 Web 控制台的实现级视觉契约。完整设计理由以 [UX 设计说明](../ux-design-specification.md) 为准；如两者冲突，以经审批的 UX 设计说明为准并同步修订本文件。

## Pilot Scope Override

2026-08-17 批准的首发仅覆盖注册、登录/入驻状态、单一应用与首次凭证、三接口权限、静态 API 文档。下文关于复杂应用工作区、统计图表、页面调试、密钥轮换、生产准入和完整移动端矩阵的规则作为 1.x 设计储备，不阻塞首发。首发复用既有 token 和基础组件，不为延期页面预建组合组件。

## Visual Principles

1. **接入指挥台优先：** 页面首先表达当前阶段、阻塞原因和唯一主要动作。
2. **高密度但可读：** 以表格、分段区块、代码区域和稳定导航承载开发者任务，减少无意义卡片和装饰。
3. **状态可辨识：** 环境、审核、权限和风险状态同时使用文字、图标与语义色，不依赖颜色单独传达。
4. **沙箱与生产持续区分：** 环境标识在上下文条、凭证、调试和响应区域持续可见。
5. **安全操作克制明确：** 凭证、停用和轮换操作突出影响范围、可恢复性与生效时间。

## Brand And Voice Implications

- 品牌气质为可信、克制、清晰、工程化；不使用营销式大标题、装饰渐变或大型插画。
- 页面文案直接说明“当前状态—原因—下一步—支持渠道”，避免模糊的“操作失败”“请稍后再试”。
- 技术术语首次出现时给出短解释；错误信息保留错误码与 Request ID。
- 生产风险文案具体描述影响，不使用恐吓式表达；成功反馈必须引导下一步。

## Tokens

### Color

| Token | Value | Usage |
|---|---|---|
| `--color-background` | `#F7FAF9` | 页面背景 |
| `--color-foreground` | `#152521` | 主文字 |
| `--color-surface` | `#FFFFFF` | 内容表面、Card、Popover |
| `--color-primary` | `#087F6B` | 主按钮、链接、选中状态 |
| `--color-primary-foreground` | `#FFFFFF` | 主色表面文字 |
| `--color-primary-soft` | `#E9F8F4` | 选中导航、轻量强调 |
| `--color-muted` | `#EDF4F2` | 次按钮、表头、辅助区 |
| `--color-muted-foreground` | `#5C716B` | 次要文字 |
| `--color-border` | `#D0DFDB` | 边框与输入框 |
| `--color-focus-ring` | `#087F6B` | 键盘焦点 |
| `--color-sandbox` | `#087F9A` | 沙箱环境 |
| `--color-production` | `#B85C12` | 生产环境与生产风险 |
| `--color-success` | `#18794E` | 成功、通过 |
| `--color-warning` | `#A15C08` | 待审核、即将到期 |
| `--color-destructive` | `#BF3636` | 错误、停用、失效 |
| `--color-code-background` | `#17211F` | 代码与 JSON 区域 |

正文、控件和状态文字必须满足 WCAG AA。品牌主色不替代成功色，生产色不替代错误色。

### Typography

| Token | Contract |
|---|---|
| `--font-sans` | `Inter, PingFang SC, Microsoft YaHei, sans-serif` |
| `--font-mono` | `ui-monospace, SFMono-Regular, Consolas, monospace` |
| `--text-page-title` | `24px/32px`, weight `600` |
| `--text-section-title` | `18px/26px`, weight `600` |
| `--text-subtitle` | `16px/24px`, weight `600` |
| `--text-body` | `14px/22px`, weight `400` |
| `--text-table` | `13px/20px`, weight `400` |
| `--text-helper` | `12px/18px`, weight `400` |
| `--text-code` | `13px/21px`, monospace |

AppID、Request ID、接口路径、时间戳、签名示例和 JSON 使用等宽字体。正文不得低于 12px；技术标识符不得任意断词，必要时在自身容器内滚动。

### Spacing And Layout

| Token | Value / rule |
|---|---|
| `--space-unit` | `4px` |
| `--content-max-width` | `1440px` |
| `--sidebar-width` | `224px` |
| `--page-padding-desktop` | `32px` |
| `--page-padding-compact` | `24px` |
| `--section-gap` | `24px` |
| `--field-gap` | `16px` |
| `--label-control-gap` | `8px` |
| `--table-row-height` | `44px` |
| `--control-height` | `36px` |
| `--primary-control-height` | `40px` |

- 桌面使用固定侧栏与单内容工作区；应用工作区采用 12 列响应式网格。
- 统计摘要最多并排 3 个；API 文档采用导航、正文、页内目录三栏，空间不足时折叠页内目录。
- 移动端使用单列任务结构和折叠菜单，不把桌面表格简单压缩成无标签数值。
- 200% 浏览器缩放下不得丢失主要操作或产生页面级横向滚动。

### Radius, Border, Shadow

- `--radius-control: 6px`：输入框、按钮、徽标和普通内容区。
- `--radius-overlay: 8px`：Dialog、Sheet、Popover。
- `--border-default: 1px solid var(--color-border)`。
- 普通页面内容不用阴影；Popover、Dropdown、Dialog 只允许一档柔和阴影。
- 危险区域使用上边框、标题和浅色风险背景，不使用卡片套卡片。

### Motion And Feedback

- 状态过渡为 `150–200ms`，仅用于焦点、展开、选中和轻量反馈。
- 尊重 `prefers-reduced-motion`，减少非必要动画。
- 加载按钮保持原宽度；Skeleton 与真实内容轮廓一致，避免布局跳动。
- 复制、保存等短操作使用轻提示；审核、错误恢复、安全警告和阶段完成使用持久页面反馈。

## Component Appearance Rules

- 基础组件采用 shadcn/ui；不得混用第二套完整组件体系。
- 每个页面或面板最多一个主按钮。次要操作使用 outline/ghost；危险操作放入独立危险区域。
- 页面标题区包含面包屑、标题、状态和主要操作；环境状态必须包含明确文字。
- 表格使用紧凑 44px 行高、语义表头和稳定操作列；超过两个行内次要操作时收入口径统一的菜单。
- 状态 Badge 包含图标与文字。待审核、通过、驳回、停用、沙箱、生产不得只以颜色区分。
- 表单错误就近展示并与字段关联；页面级错误使用持久 Alert；API 错误使用诊断面板。
- 焦点样式为 2px `--color-focus-ring` 加 2px 外偏移；危险确认初始焦点不得落在 destructive 按钮。
- 代码和响应区域使用代码背景、等宽字体、复制操作和明确语言/格式标签。
- 空状态说明为空原因并提供唯一主要动作，不使用大型插画。

## Asset, Image, And Icon Guidance

- 使用一致的线性图标集；图标只辅助文字，不代替按钮名称、状态或环境标签。
- 品牌标记保持简单字标/几何标，不引入供应链摄影图、人物插画或装饰背景图。
- 状态图标需要稳定映射：通过、等待、驳回、错误、沙箱、生产在全站保持一致。
- 图表必须同时提供文字摘要或数据表；颜色系列满足对比度并使用形状/标签辅助区分。
- 图标按钮必须提供可访问名称，复制成功与失败通过实时区域播报。

## Implementation Notes

- 在 `apps/web/src/styles/` 定义语义 CSS 变量，并映射到 Tailwind/shadcn 主题；业务组件不得直接散落十六进制色值。
- 基础组件位于 `components/ui`；稳定复用的业务组合组件位于 `components/domain`；页面与流程组件优先归属相应 `features`。
- 业务状态枚举集中映射到 token、图标和文案，页面不得自行拼装状态色。
- Story 实现只引入当前流程需要的最小组件；简单且仅使用一次的布局不提前抽象。
- 视觉回归至少覆盖默认、悬停、焦点、禁用、加载、空、错误、成功、危险以及桌面/窄屏状态。
