# 抖音小程序 / 小游戏激励视频广告跳过模块（LSPosed）

针对 **抖音 41.0.0（`com.ss.android.ugc.aweme` / versionCode 410001）** 逆向 +
一加 Ace 5 Pro（PKR110 / Android 16 / KernelSU + ZygiskNext + LSPosed v1.11.0）真机验证：

> 小游戏里点「看广告得奖励 / 抽奖 / 复活 / 加倍」时，**广告不播放**，
> 直接按「已看完」结算，金币 / 道具 / 奖励照常到账。

产物：`release/DouyinMiniAppAdSkip-3.0-obf.apk`（debug 签名，包名 `com.byxiaopeng.dytiao`）

---

## 一、真机实测结果

| 场景 | 操作 | 结果 |
|---|---|---|
| 抖音小游戏「打个僵尸-守中路」 | 点激励视频入口 | 广告页未出现，日志显示 800ms 后结算，奖励正常到账 ✅ |
| 小游戏各种「看广告」入口 | 反复触发 | 每次 `ExcitingVideoFragment#onResume` 都被拦截，`close{count:1,isEnded:true}` 正常回传 ✅ |
| 小程序页面内 `<ad>` 组件 | 进入带广告位的小程序 | `shouldShow` 回 false + 组件渲染返回 null（沿用 40.x 链路，41.0.0 方法仍在）✅ |
| 抖音设置页 | 打开设置 | 注入「跳广告设置」入口（开关 + 免责声明 + GitHub 链接）✅ |

日志特征（LSPosed 日志，`/data/adb/lspd/log/verbose_*.log`）：

```
DouyinAdSkip: hooked com.ss.android.excitingvideo.sdk.ExcitingVideoFragment#onResume
DouyinAdSkip: ev fragment resumed -> complete in 800ms
DouyinAdSkip: ev: onRewardComplete(2) sent
DouyinAdSkip: hooked close bridge X.0SCI
DouyinAdSkip: close payload patched: {"count":1,"isEnded":true,"watchedTime":999,...}
DouyinAdSkip: close event sent: {"count":1,...}
DouyinAdSkip: ev: closeFragment called
DouyinAdSkip: watchTime patched: 0 -> 30
```

---

## 二、逆向链路（41.0.0）

```
小程序/小游戏进程
  └─ tt.createRewardedVideoAd / 小游戏 SDK
        └─ MiniGameActivity0 里塞入
             com.ss.android.excitingvideo.sdk.ExcitingVideoFragment
               字段 o : com.ss.android.excitingvideo.IRewardCompleteListener   ← 发奖判定的核心
               小游戏侧实现 X.16aT extends IRewardCompleteListener
                 LIZ   : X.0SCI  (GameAdModel，LIZIZ("close", json) 回传事件)
                 LIZJ  = playTime   LIZLLL = effectTime
                 LJ    = playCount  LJFF   = completeCount
```

发奖判定（41.0.0 与 40.x 基本一致，只是混淆名变了）：

1. `X.16aT#onRewardComplete(type, params)`：
   `playTime = params.getWatchTime(); effectTime = params.getInspireTime();`
   **只有 `playTime >= effectTime` 才会 `completeCount++`**；
2. `X.16aT#onDestroy()`：把 `{"count": completeCount}` 通过 `X.0SCI.LIZIZ("close", json)` 发给小程序；
3. 小程序侧 `X.0S5t#newAdStateChangeListener` 收到 `close`：
   `count >= 1` 时直接按「已看完」构造
   `watchedTime=30, effectiveTime=25, errCode=0`，再经 `UNBridge.call("onAdStateChanged", map)` 交给 JS。

也就是说：**只要让 `onRewardComplete` 拿到 watchTime ≥ inspireTime，再把 close 的 count 补成 1，
广告一帧都不用播，奖励链路完整走通。**

---

## 三、模块实现

`AdSkipModule` 只作用 `com.ss.android.ugc.aweme` 的所有进程（`:minigame0/1`、`:sandboxed_process*` 等）：

| 钩子 | 作用 |
|---|---|
| `ExcitingVideoFragment#onResume` | 广告已展示 → 延时 800ms 进入结算 |
| `IRewardCompleteListener.RewardCompleteParams#getWatchTime` | 兜底：watchTime < inspireTime 时抬到 inspireTime |
| `X.16aT#onRewardComplete`（反射拿 `o` 字段后直接调用） | 传 `RewardCompleteParams(999, 1)` → completeCount++ |
| `X.0SCI#LIZIZ(String, JSONObject)` | 任何路径发出的 `close` 都补 `count≥1 / isEnded=true / watchedTime` |
| `closeFragment()` + 兜底 finish | 关掉广告 Fragment / Activity |
| `BdpMannorAdServiceImpl#dealMannorMethod` / `#renderBizComponentAndMannor` | 页面内 `<ad>` 组件不展示 |
| `DrawAdComponent#LJIIIZ` | 节点 View 兜底 GONE |
| 设置页注入 | `DouYinSettingNewVersionActivity` 插入「跳广告设置」 |

结算顺序：`onRewardComplete(2, params)` → 直接调 `X.0SCI.LIZIZ("close", payload)` →
`onClose()` → `closeFragment()`，Fragment 销毁时 SDK 自己的 `onDestroy` 再发的 close 同样会被补成 count=1。

---

## 四、加密混淆

`release/DouyinMiniAppAdSkip-3.0-obf.apk` 是混淆版：

- R8 全量混淆 + 400 条自定义短名字典（`app/obfuscation-dictionary.txt`），
  类/方法/字段全部变成 `o.A`、`o.AA` 这类随机名，只保留 Xposed 入口类；
- 类名、方法名、关键字段名放进 `X.java`，以「异或 + 十六进制」保存，运行时还原；
- 去掉 SourceFile / LineNumberTable。

`strings classes.dex` 检查：`com.ss.android.excitingvideo`、`RewardCompleteListener`、
`com.ss.android.ugc.aweme`、`isEnded`、`DouyinAdSkip` 均 **0 命中**。

---

## 五、安装

1. KernelSU（或 Magisk）+ ZygiskNext；
2. 装 LSPosed（本项目用 v1.11.0，管理器 `org.lsposed.manager`）；
3. 安装 `release/DouyinMiniAppAdSkip-3.0-obf.apk`；
4. LSPosed 里启用模块，作用域勾选「抖音」；
5. 强行停止抖音再打开；抖音「设置」页会出现「跳广告设置」入口（默认已开启）。

## 六、编译

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home
gradle :app:assembleRelease     # 需要 Gradle 8.13 + Android SDK 35
```

## 七、局限

- **版本绑定**：`X.16aT` / `X.0SCI` 是 41.0.0 的混淆名。模块里 close 桥接、监听器都是
  运行时按「字段类型 / 方法签名」反射定位的，小版本升级一般不用改；
  大版本如果 `IRewardCompleteListener` / `ExcitingVideoFragment` 结构变了才需要跟进。
- **只覆盖 TapADN/穿山甲系激励视频**：开屏广告、信息流广告不在范围。
- **风控**：广告 SDK 侧不会收到真实曝光；小游戏若做服务端校验可能不发奖
  （本次验证的游戏为客户端判定，奖励正常）。别用主号长时间刷。

## 八、免责声明

仅供学习 Android 逆向 / Xposed 开发使用。使用本模块可能违反抖音及游戏方的用户协议，
可能导致账号受限、奖励不发放、封禁等后果，由使用者自行承担。
