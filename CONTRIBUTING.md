# 🤝 贡献指南

感谢你愿意为 **SPlayer for Android** 出一份力！本指南说明本仓库的 Issue / PR / Commit 规范。

> 本项目基于 [SPlayer](https://github.com/imsyy/SPlayer) 移植。**仅与 Android 端相关** 的改动请提到本仓库；跨平台 / 桌面端 / Web 端的共通问题请去上游仓库。

---

## 1. 提交 Issue

请使用对应的 Issue 模板（点击 **New Issue** 会出现选择页）：

| 模板 | 用途 |
| --- | --- |
| 🐞 **Bug 反馈** | 使用过程中遇到的缺陷、闪退、异常行为 |
| ✨ **功能建议** | 新功能 / 改进想法 |
| 💬 **QQ 交流群** | 群号 `602750631` — 使用疑问与讨论，不替代 Issue |

### 基本要求

- **一个 Issue 一个问题**，不要把多个问题塞进同一个 Issue
- 标题写清楚模块 + 现象，例如 `[Bug] 桌面歌词锁定状态下仍显示背景`
- 提交前**先搜索历史 Issue**（包括已关闭的）
- 请保留模板中的字段结构，不要删段落再提交
- **Bug** 必须给出：版本号、设备型号、Android 版本、ABI、可复现步骤
- 闪退类问题请尽量附上 `adb logcat` 日志

### 什么情况会被直接关闭

- 标题只有「有 bug」「闪退了」「不能用」之类无效信息
- 没有版本号 / 设备 / 复现步骤
- 属于上游 SPlayer（桌面端 / Web 端）的问题
- 属于网易云 API 本身的接口风控 / 登录限制问题
- 反复重复提交同一问题

---

## 2. 提交 Pull Request

### 2.1 分支

- 目标分支：`master`
- 从最新的 `master` 新建分支，命名建议：
  - `feat/<模块>-<简述>`  — 新功能
  - `fix/<模块>-<简述>`   — Bug 修复
  - `refactor/<模块>-<简述>`
  - `docs/<简述>`
- 分支名用小写与短横线，不要用中文

### 2.2 提交信息（Conventional Commits）

本仓库遵循 **Conventional Commits**，结构：

```
<type>(<scope>): <简要描述>

[可选正文：解释为什么这么改、影响范围、注意事项]

[可选 footer：BREAKING CHANGE / Closes #123]
```

`<type>` 取值：

| type | 含义 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `perf` | 性能优化 |
| `refactor` | 重构，不改变外部行为 |
| `style` | 仅代码格式 / 样式，不影响逻辑 |
| `docs` | 仅文档 |
| `test` | 测试相关 |
| `chore` | 构建 / 依赖 / 脚本 |
| `ci` | GitHub Actions 等 CI 配置 |
| `revert` | 回滚之前的提交 |

`<scope>` 建议取模块名，常见示例：`DesktopLyric`、`Player`、`Lyric`、`Search`、`Setting`、`Notification`、`Capacitor`、`Android`、`CI`。

**示例**：

```
feat(DesktopLyric): 新增逐字描边开关

fix(Lyric): 拖动吸附到错误行的问题
Closes #42

chore(deps): bump capacitor to 8.x
```

### 2.3 PR 要求

- 标题与首条 commit 一致，遵循 Conventional Commits 格式
- 填写 PR 模板中的所有必填项（变更类型、说明、关联 Issue、自检清单）
- **UI 类改动**必须附上改动前后对比截图或录屏
- 提交前本地跑通：
  ```bash
  pnpm lint
  pnpm typecheck
  pnpm build:web
  ```
- 至少在一台真机上构建并验证关键路径（特别是改动涉及播放 / 歌词 / 通知栏 / 桌面歌词时）
- 一个 PR 只做一件事，重构与功能尽量分开
- 不要提交体积过大的资源（截图 > 2 MB 请压缩）
- 不要改动 `.github/workflows/**` 中的签名 / Secrets 逻辑，除非你清楚后果

### 2.4 Review 流程

1. 提交 PR → CI 自动构建 → 通过后进入 Review
2. 评审者可能会要求修改，请在同一分支继续 push 新 commit（**不要 force-push 破坏历史**）
3. 合并策略：默认 **Squash merge**，合并后的提交标题会沿用 PR 标题，请保持规范

---

## 3. 本地开发

环境要求与构建命令见 [README](./README.md#-快速开始)。常用命令：

```bash
pnpm install                    # 安装依赖
pnpm dev                        # Web 开发服务器（仅 UI 调试）
pnpm build:android              # 构建 Android 产物
cd android && ./gradlew assembleDebug   # 出 Debug APK
```

Android 原生代码位于 `android/`，Capacitor 插件配置在 `capacitor.config.ts`。

---

## 4. 行为准则

- 友善、聚焦技术讨论
- 不要在 Issue / PR 中贴与问题无关的广告、引战内容
- 中文或英文都可，但请**前后统一**

---

再次感谢你的贡献 ❤️
