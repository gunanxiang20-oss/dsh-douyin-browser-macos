# macOS 试验分支（1.4.4-mac.1）

这个目录是从 `dsh-douyin-browser-local@1.4.4` 复制出来的本地分支，只改浏览器运行方式，其余逻辑保持原样。

## 安装

DSH「应用 → 桌面插件」输入框粘贴：

```
https://github.com/gunanxiang20-oss/dsh-douyin-browser-macos
```

或同一仓库的 git 简写 `github:gunanxiang20-oss/dsh-douyin-browser-macos`。装完完全退出并重开 DSH。

首次启动浏览器时，如果本机没有 WebKit 引擎，`webkit-bridge.mjs` 会自动调用插件自带的 Playwright CLI 下载一次（约 90MB，`~/Library/Caches/ms-playwright`），失败时把原始报错透出。


## 状态放在哪

默认写到 `~/.dsh/douyin-browser/state`（本分支改的，上游默认写在插件包目录里）。原因：从 npm/GitHub 安装时包目录每次升级都会被整包替换，写在里面的时长、模型路由、许可会随升级一起消失。记忆库本来就在 `~/.dsh/douyin-browser/memory`，不受影响。

需要改位置时，在 `~/.dsh/douyin-browser/runtime-location.json` 里写 `stateDir`，或用环境变量 `DOUYIN_STATE_DIR`。

## 改了什么

原版用 Microsoft Edge + CDP：`browser-host.mjs` 启动 `chromium.launchPersistentContext({channel:'msedge'})`，再通过 `chromium.connectOverCDP` 连接。macOS 上没有 Edge，也没有独立的 Node 子进程时无法运行。

本分支改为在 DSH 宿主进程内用 Playwright 自带的 WebKit（Safari 引擎）启动专用浏览器：

| 文件 | 改动 |
| --- | --- |
| `webkit-bridge.mjs` | 新增。`launchPersistentContext` 启动 WebKit，并包一层 `{isConnected, contexts(), close()}`，语义等同 CDP 连接（close 只断开引用） |
| `playwright-runtime.mjs` | 额外导出 `webkit` |
| `core.mjs` | `connect()`、`openBrowser()`、`connectExistingBrowser()`、`pauseForLimits()` 改为走 `connectBrowser()`/`launchWebKit`；`endpoint()` 不再 spawn 子进程；`DOUYIN_BROWSER_PROFILE`/`DOUYIN_BROWSER_CONTROL` 可覆盖目录 |
| `runtime-paths.mjs` | 找不到独立 Node 时回退到宿主 Electron 自带 Node；默认状态目录从插件包内改到 `~/.dsh/douyin-browser/state` |
| `diagnostics.mjs` | 浏览器检查同时接受 macOS 上的 Edge 或 Playwright WebKit |
| `package.json` | 版本 `1.4.4-mac.1`，去掉 `os: ["win32"]` |

## 已知差异

- 静默观看的“最小化专用窗口”依赖 CDP（`watch-mode.mjs` 的 `Browser.setWindowBounds`），WebKit 下会失败并回报窗口未最小化；静音仍然生效。
- 私信/评论等仍按原逻辑运行，但只在真实登录后才有意义。
- 原版仍是 Windows + Edge 的正式实现，本分支不随上游更新。

## 回滚

在「应用 → 桌面插件」里删除本分支，再安装 `dsh-douyin-browser-local@1.4.4` 即可恢复原版。
