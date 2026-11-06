package com.byxiaopeng.dytiao;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 抖音 (com.ss.android.ugc.aweme) 41.0.0 小程序 / 小游戏激励视频：跳过广告 + 照常发奖。
 *
 * 逆向结论（41.0.0 / versionCode 410001）：
 *
 *   宿主把 com.ss.android.excitingvideo.sdk.ExcitingVideoFragment 塞进小游戏 Activity，
 *   Fragment 的 public 字段 o 是 IRewardCompleteListener（41.0.0 起该接口变成 abstract class）。
 *   小游戏侧的实现是 X.16aT（extends IRewardCompleteListener），字段：
 *       LIZ : X.0SCI   —— GameAdModel，LIZIZ(X.close(), json) 把事件回传小程序
 *       LIZJ = playTime, LIZLLL = effectTime, LJ = playCount, LJFF = completeCount
 *
 *   onRewardComplete(type, params) 里只有 params.watchTime >= params.inspireTime 才会
 *   completeCount++；onDestroy() 再把 {X.count(): completeCount} 发给小程序。
 *   小程序收到 close 时 count>=1 就按“已看完”处理（见 X.0S5t#newAdStateChangeListener）。
 *
 * 模块做法：
 *   1. ExcitingVideoFragment 一 onResume（广告已展示）就构造 RewardCompleteParams(999,1)
 *      回调 onRewardComplete(2, params) —— 等价于“看完了”；
 *   2. 直接拿 X.16aT.LIZ（GameAdModel）把 close 事件补成 {X.count():1,X.is_ended():true,...} 发给小程序；
 *   3. 关闭广告 Fragment + 兜底 finish 广告 Activity；
 *   4. getWatchTime() 兜底抬到 getInspireTime()，防别的入口按观看时长判奖。
 */
public class AdSkipModule implements IXposedHookLoadPackage {

    private static final String TAG = X.tag();
    private static final String PKG = X.pkg();

    static final String CLS_EV_FRAGMENT =
            X.cls_ev();
    static final String CLS_LISTENER =
            X.cls_listener();
    static final String CLS_PARAMS =
            X.cls_params();
    static final String CLS_REWARD_FRAGMENT =
            X.cls_reward_fragment();
    static final String CLS_DRAW_FRAGMENT =
            X.cls_draw_fragment();

    /** 已经安排过跳过的 Fragment，避免 onResume 多次触发 */
    private static final Map<Object, Boolean> sScheduled =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    /** 已经处理过的监听器，避免重复发 close */
    private static final Map<Object, Boolean> sClosed =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    /** 已经下过钩子的桥接类 */
    private static final Set<Class<?>> sBridgeHooked =
            Collections.synchronizedSet(new HashSet<Class<?>>());

    private static ClassLoader sClassLoader;

    static void postDelayed(Runnable action, long delayMs) {
        try {
            Looper main = Looper.getMainLooper();
            if (main == null) {
                action.run();
                return;
            }
            new Handler(main).postDelayed(action, delayMs);
        } catch (Throwable t) {
            log("postDelayed failed, run inline: " + t);
            action.run();
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !PKG.equals(lpparam.packageName)) {
            return;
        }
        sClassLoader = lpparam.classLoader;
        log("module loaded in process=" + lpparam.processName);

        if (Config.PATCH_WATCH_TIME) {
            hookWatchTime();
        }
        if (Config.SKIP_REWARD_VIDEO) {
            hookExcitingVideoFragment();
            hookRewardFragment(CLS_REWARD_FRAGMENT, X.zm());
            if (Config.SKIP_DRAW_REWARD) {
                hookRewardFragment(CLS_DRAW_FRAGMENT, X.zm());
            }
        }
        if (Config.INJECT_SETTINGS_ENTRY) {
            try {
                DouyinSettingsInjector.install(sClassLoader);
            } catch (Throwable t) {
                log("settings injector failed: " + t);
            }
        }
        if (Config.HIDE_IN_PAGE_ADS) {
            try {
                InPageAdHooks.install(sClassLoader);
            } catch (Throwable t) {
                log("in-page hooks failed: " + t);
            }
        }
    }

    // ---------------------------------------------------------------- hooks

    private void hookExcitingVideoFragment() {
        Class<?> clazz = XposedHelpers.findClassIfExists(CLS_EV_FRAGMENT, sClassLoader);
        if (clazz == null) {
            log("ev fragment not found: " + CLS_EV_FRAGMENT);
            return;
        }
        try {
            XposedBridge.hookAllMethods(clazz, X.on_resume(), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!Prefs.enabled()) {
                        return;
                    }
                    final Object fragment = param.thisObject;
                    if (fragment == null || sScheduled.put(fragment, Boolean.TRUE) != null) {
                        return;
                    }
                    log("ev fragment resumed -> complete in " + Config.SKIP_DELAY_MS + "ms");
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                completeExcitingVideo(fragment);
                            } catch (Throwable t) {
                                log("completeExcitingVideo crashed: " + t);
                            }
                        }
                    }, Config.SKIP_DELAY_MS);
                }
            });
            log("hooked " + CLS_EV_FRAGMENT + "#onResume");
        } catch (Throwable t) {
            log("hook ev fragment failed: " + t);
        }
    }

    /** 直接结算：onRewardComplete(2, 假 params) + 补发 close(count=1) + 关闭 Fragment */
    private void completeExcitingVideo(Object fragment) {
        if (!Prefs.enabled()) {
            log("skip disabled by user, do nothing");
            return;
        }
        Object listener = null;
        try {
            listener = XposedHelpers.getObjectField(fragment, X.listener_field());
        } catch (Throwable t) {
            log("read listener field failed: " + t);
        }
        if (listener == null) {
            listener = findListenerByType(fragment);
        }
        if (listener == null) {
            log("ev fragment listener is null, give up");
            return;
        }
        final Object l = listener;
        ensureListenerHooked(l);

        // 1) 官方语义的“看完了”：watchTime=999 >= inspireTime=1，completeCount 会 +1
        try {
            Class<?> paramsClass = XposedHelpers.findClass(CLS_PARAMS, sClassLoader);
            Object params = XposedHelpers.newInstance(paramsClass, Config.FAKE_WATCH_TIME, 1);
            XposedHelpers.callMethod(l, X.on_reward_complete(), 2, params);
            log("ev: onRewardComplete(2) sent");
            try {
                XposedHelpers.callMethod(l, X.on_reward_receiving(), 2, params);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            log("ev: onRewardComplete failed " + t);
        }

        // 2) 直接把 close 事件补好发给小程序（不等 onDestroy 的 context 校验）
        sendClose(l);

        // 3) 关掉广告
        try {
            XposedHelpers.callMethod(l, X.on_close());
            log("ev: onClose sent");
        } catch (Throwable t) {
            log("ev: onClose failed " + t);
        }
        if (Config.EV_CLOSE_FRAGMENT) {
            try {
                XposedHelpers.callMethod(fragment, X.close_fragment());
                log("ev: closeFragment called");
            } catch (Throwable t) {
                log("ev: closeFragment failed " + t);
            }
        }
        if (Config.FORCE_FINISH_DELAY_MS > 0) {
            final Object frag = fragment;
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    Activity activity = activityOf(frag);
                    if (activity != null && !activity.isFinishing()) {
                        log("force finish " + activity.getClass().getName());
                        activity.finish();
                    }
                }
            }, Config.FORCE_FINISH_DELAY_MS);
        }
    }

    /**
     * 把 close 事件直接发给小程序：
     *    listener.LIZ (X.0SCI / GameAdModel).LIZIZ(X.close(), {X.count():1,...})
     */
    private void sendClose(Object listener) {
        if (sClosed.put(listener, Boolean.TRUE) != null) {
            return;
        }
        try {
            Object bridge = findBridge(listener);
            if (bridge == null) {
                log("close bridge not found on " + listener.getClass().getName());
                return;
            }
            hookBridge(bridge.getClass());
            JSONObject json = buildClosePayload();
            Method m = findMethod(bridge.getClass(), String.class, JSONObject.class);
            if (m == null) {
                log("close method not found on " + bridge.getClass().getName());
                return;
            }
            m.setAccessible(true);
            m.invoke(bridge, X.close(), json);
            log("close event sent: " + json);
        } catch (Throwable t) {
            log("sendClose failed: " + t);
        }
    }

    private JSONObject buildClosePayload() {
        JSONObject json = new JSONObject();
        try {
            json.put(X.count(), 1);
            json.put(X.is_ended(), true);
            json.put(X.watched_time(), Config.FAKE_WATCH_TIME);
            json.put(X.effective_time(), 1);
            json.put(X.duration(), 1);
        } catch (Throwable ignored) {
        }
        return json;
    }

    /** listener 里那个“能把 (String, JSONObject) 发出去”的字段就是 GameAdModel */
    private Object findBridge(Object listener) {
        Class<?> c = listener.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Class<?> t = f.getType();
                if (findMethod(t, String.class, JSONObject.class) == null) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(listener);
                    if (v != null) {
                        return v;
                    }
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> c, Class<?>... types) {
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            for (Method m : cur.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != types.length) {
                    continue;
                }
                boolean ok = true;
                for (int i = 0; i < ps.length; i++) {
                    if (!ps[i].isAssignableFrom(types[i])) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    /** 给 GameAdModel 的 close 出口下钩子，任何路径发 close 都补 count/isEnded */
    private void hookBridge(final Class<?> bridgeClass) {
        if (!sBridgeHooked.add(bridgeClass)) {
            return;
        }
        try {
            final Method m = findMethod(bridgeClass, String.class, JSONObject.class);
            if (m == null) {
                return;
            }
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object[] args = param.args;
                    if (args == null || args.length < 2) {
                        return;
                    }
                    if (!X.close().equals(args[0]) || !(args[1] instanceof JSONObject)) {
                        return;
                    }
                    JSONObject json = (JSONObject) args[1];
                    try {
                        if (json.optInt(X.count(), 0) < 1) {
                            json.put(X.count(), 1);
                        }
                        json.put(X.is_ended(), true);
                        if (json.optInt(X.watched_time(), 0) < Config.FAKE_WATCH_TIME) {
                            json.put(X.watched_time(), Config.FAKE_WATCH_TIME);
                        }
                        json.put(X.effective_time(), 1);
                        log("close payload patched: " + json);
                    } catch (Throwable t) {
                        log("patch close payload failed: " + t);
                    }
                }
            });
            log("hooked close bridge " + bridgeClass.getName());
        } catch (Throwable t) {
            log("hook bridge failed: " + t);
        }
    }

    /** 监听器自己的 onDestroy 有可能被 Fragment 再次调用，这里只做日志记录 */
    private void ensureListenerHooked(Object listener) {
        try {
            XposedBridge.hookAllMethods(listener.getClass(), X.on_destroy(), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    log("listener.onDestroy called");
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private Object findListenerByType(Object fragment) {
        Class<?> listenerClass = XposedHelpers.findClassIfExists(CLS_LISTENER, sClassLoader);
        if (listenerClass == null) {
            return null;
        }
        for (Field f : fragment.getClass().getDeclaredFields()) {
            if (!listenerClass.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                f.setAccessible(true);
                Object v = f.get(fragment);
                if (v != null) {
                    return v;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void hookRewardFragment(final String className, final String completeMethod) {
        final Class<?> clazz = XposedHelpers.findClassIfExists(className, sClassLoader);
        if (clazz == null) {
            log("class not found: " + className);
            return;
        }
        try {
            XposedBridge.hookAllMethods(clazz, X.on_resume(), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!Prefs.enabled()) {
                        return;
                    }
                    final Object fragment = param.thisObject;
                    if (fragment == null) {
                        return;
                    }
                    if (sScheduled.put(fragment, Boolean.TRUE) != null) {
                        return;
                    }
                    log("ad fragment resumed: " + fragment.getClass().getName()
                            + " -> skip in " + Config.SKIP_DELAY_MS + "ms");
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            performSkip(fragment, completeMethod);
                        }
                    }, Config.SKIP_DELAY_MS);
                }
            });
            log("hooked " + className + "#onResume");
        } catch (Throwable t) {
            log("hook fragment failed: " + className + " " + t);
        }
    }

    private void performSkip(Object fragment, String completeMethod) {
        try {
            Object listener = findListenerByType(fragment);
            if (listener != null) {
                Class<?> paramsClass = XposedHelpers.findClass(CLS_PARAMS, sClassLoader);
                Object params = XposedHelpers.newInstance(paramsClass, Config.FAKE_WATCH_TIME, 1);
                XposedHelpers.callMethod(listener, X.on_reward_complete(), 2, params);
                XposedHelpers.callMethod(listener, X.on_close());
                log("fallback fake reward delivered via " + listener.getClass().getName());
            } else {
                XposedHelpers.callMethod(fragment, completeMethod);
                log("fallback: called " + completeMethod + "() on "
                        + fragment.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            log("fallback skip failed: " + t);
        }
        final Object frag = fragment;
        postDelayed(new Runnable() {
            @Override
            public void run() {
                Activity activity = activityOf(frag);
                if (activity != null && !activity.isFinishing()) {
                    log("force finish " + activity.getClass().getName());
                    activity.finish();
                }
            }
        }, 2500L);
    }

    private Activity activityOf(Object fragment) {
        try {
            Object a = XposedHelpers.callMethod(fragment, X.get_activity());
            return a instanceof Activity ? (Activity) a : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 抖音下游判断是否发奖：watchTime >= inspireTime，提前结束时补到激励时长 */
    private void hookWatchTime() {
        final Class<?> params = XposedHelpers.findClassIfExists(CLS_PARAMS, sClassLoader);
        if (params == null) {
            log("params class not found: " + CLS_PARAMS);
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(params, X.get_watch_time(), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object self = param.thisObject;
                        int watch = (Integer) param.getResult();
                        int inspire = (Integer) XposedHelpers.callMethod(self, X.get_inspire_time());
                        if (watch < inspire) {
                            param.setResult(inspire);
                            log("watchTime patched: " + watch + " -> " + inspire);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            log("hooked " + CLS_PARAMS + "#getWatchTime");
        } catch (Throwable t) {
            log("hook getWatchTime failed: " + t);
        }
    }

    // ---------------------------------------------------------------- util

    static void log(String msg) {
        if (!Config.VERBOSE) {
            return;
        }
        XposedBridge.log(TAG + ": " + msg);
    }
}
