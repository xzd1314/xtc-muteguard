package com.xzd.yunkong;

import android.content.Context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境信息采集：Magisk 版本 / Xposed 版本 / 模块激活状态 / 微聊版本。
 * 全部通过一次 su 会话批量读取，避免多次 fork su 造成卡顿。
 */
public class SystemInfo {

    private static final String SEP = "@@@MG@@@";
    private static final String CONF = "/data/user_de/0/com.solohsu.android.edxp.manager/conf/enabled_modules.list";

    public String magiskVersion = "—";
    public String xposedVersion = "—";
    public String moduleState = "未启用";
    public String weichatVersion = "—";

    /** 模块是否在 EdXposed 启用列表中 */
    public boolean moduleEnabled = false;
    /** 是否在 logcat 中观测到 hook 日志（代表确实注入成功） */
    public boolean moduleInjected = false;

    public static SystemInfo load(Context ctx) {
        SystemInfo si = new SystemInfo();
        String pkg = ctx.getPackageName();

        String cmd = "magisk -v 2>/dev/null; echo '" + SEP + "'; "
                + "grep '^version=' /data/adb/modules/riru_edxposed/module.prop 2>/dev/null; echo '" + SEP + "'; "
                + "grep -c '" + pkg + "' " + CONF + " 2>/dev/null || echo 0; echo '" + SEP + "'; "
                + "logcat -d -t 3000 2>/dev/null | grep -c 'MuteGuard'; echo '" + SEP + "'; "
                + "dumpsys package com.xtc.weichat 2>/dev/null | grep versionName | head -1";

        String out = RootUtil.exec(cmd);
        if (out == null) {
            si.moduleState = "未知";
            return si;
        }

        String[] parts = out.split(Pattern.quote(SEP), -1);

        // 1. Magisk 版本，形如 25.2:MAGISK:R
        if (parts.length > 0) {
            String v = parts[0].trim();
            int c = v.indexOf(':');
            si.magiskVersion = c > 0 ? v.substring(0, c) : (v.isEmpty() ? "—" : v);
        }

        // 2. Xposed 版本，形如 version=v0.4.6.0_beta(4476)-eiubloli (YAHFA)
        if (parts.length > 1) {
            String v = parts[1].trim();
            Matcher m = Pattern.compile("v?(\\d+\\.\\d+\\.\\d+\\.\\d+)").matcher(v);
            if (m.find()) {
                si.xposedVersion = m.group(1);
            } else if (v.contains("=")) {
                si.xposedVersion = v.substring(v.indexOf('=') + 1).trim();
            }
        }

        // 3. 是否在启用列表
        if (parts.length > 2) {
            String v = parts[2].trim();
            si.moduleEnabled = !v.isEmpty() && !"0".equals(v);
        }

        // 4. logcat 中是否有 hook 日志
        if (parts.length > 3) {
            String v = parts[3].trim();
            si.moduleInjected = !v.isEmpty() && !"0".equals(v);
        }

        // 5. 微聊版本，形如 versionName=5.5.23.Develop_AW_Weichat_J617_G825d36f
        if (parts.length > 4) {
            String v = parts[4].trim();
            if (v.contains("=")) {
                v = v.substring(v.indexOf('=') + 1).trim();
            }
            String[] seg = v.split("\\.");
            si.weichatVersion = seg.length >= 3 ? seg[0] + "." + seg[1] + "." + seg[2]
                    : (v.isEmpty() ? "—" : v);
        }

        if (!si.moduleEnabled) {
            si.moduleState = "未启用";
        } else if (si.moduleInjected) {
            si.moduleState = "已激活";
        } else {
            si.moduleState = "已启用";
        }
        return si;
    }
}
