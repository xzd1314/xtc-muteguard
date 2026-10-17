package com.xzd.yunkong;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 微聊禁言屏蔽模块入口。
 *
 * 禁言判定链：
 *   服务端下发 -> db_illegal_record -> IllegalRecordManger.d() -> IllegalMessageHandler -> UI 禁用
 *
 * 这里把 Java 层的判定方法统一改成 false，使 UI 与业务入口认为账号未被禁言。
 * 底层 MsgService.sendTextMsg 本身不校验禁言，因此改完即可正常发送。
 */
public class XposedInit implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "com.xtc.weichat";
    private static final String CONF_PATH = "/data/local/tmp/muteguard.conf";
    private static final String TAG = "[MuteGuard] ";

    private static final String CLS_MANGER = "com.xtc.data.safety.illegal.record.IllegalRecordManger";
    private static final String CLS_HANDLER = "com.xtc.data.safety.illegal.handler.IllegalMessageHandler";
    private static final String CLS_CONV = "com.xtc.bean.chat.DbChatConversation";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!TARGET_PKG.equals(lp.packageName)) {
            return;
        }
        if (!isEnabled()) {
            XposedBridge.log(TAG + "disabled by config, skip");
            return;
        }

        int hooked = 0;
        hooked += hookReturnFalse(lp, CLS_MANGER, "d");
        hooked += hookReturnFalse(lp, CLS_MANGER, "e");
        hooked += hookReturnFalseWithArg(lp, CLS_HANDLER, "a", CLS_CONV);
        hooked += hookReturnFalse(lp, CLS_HANDLER, "a");

        XposedBridge.log(TAG + "hooked " + hooked + " method(s) in " + TARGET_PKG);
    }

    /** 无参 boolean 方法 -> 恒 false */
    private int hookReturnFalse(XC_LoadPackage.LoadPackageParam lp, String cls, String method) {
        try {
            XposedHelpers.findAndHookMethod(cls, lp.classLoader, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (Boolean.TRUE.equals(r)) {
                        param.setResult(Boolean.FALSE);
                    }
                }
            });
            XposedBridge.log(TAG + "hook " + cls + "#" + method + " ok");
            return 1;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook " + cls + "#" + method + " failed: " + t);
            return 0;
        }
    }

    /** 单参 boolean 方法 -> 恒 false */
    private int hookReturnFalseWithArg(XC_LoadPackage.LoadPackageParam lp,
                                       String cls, String method, String argType) {
        try {
            XposedHelpers.findAndHookMethod(cls, lp.classLoader, method, argType, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object r = param.getResult();
                    if (Boolean.TRUE.equals(r)) {
                        param.setResult(Boolean.FALSE);
                    }
                }
            });
            XposedBridge.log(TAG + "hook " + cls + "#" + method + "(" + argType + ") ok");
            return 1;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook " + cls + "#" + method + " failed: " + t);
            return 0;
        }
    }

    /** 开关：读取 /data/local/tmp/muteguard.conf，缺省视为开启 */
    private static boolean isEnabled() {
        try {
            File f = new File(CONF_PATH);
            if (!f.exists()) {
                return true;
            }
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line = br.readLine();
            br.close();
            return line == null || !"0".equals(line.trim());
        } catch (Throwable t) {
            return true;
        }
    }
}
