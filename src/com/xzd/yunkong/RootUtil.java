package com.xzd.yunkong;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * root 命令执行。
 *
 * 注意：小天才 Z9 的 Magisk su 不支持 `su -c "cmd"`，必须走 stdin 管道，
 * 等价于 shell 里的 `echo 'cmd' | su`。
 */
public final class RootUtil {

    private static final int TIMEOUT_MS = 8000;

    private RootUtil() {}

    public static boolean hasRoot() {
        String out = exec("id");
        return out != null && out.contains("uid=0");
    }

    /** 通过管道把命令喂给 su，返回 stdout；失败返回 null */
    public static String exec(String cmd) {
        Process p = null;
        final StringBuilder sb = new StringBuilder();
        try {
            p = Runtime.getRuntime().exec("su");
            final Process fp = p;

            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(fp.getInputStream(), "UTF-8"));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        br.close();
                    } catch (Throwable ignored) {
                    }
                }
            });
            reader.setDaemon(true);
            reader.start();

            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.write((cmd + "\n").getBytes("UTF-8"));
            os.write("exit\n".getBytes("UTF-8"));
            os.flush();
            os.close();

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < TIMEOUT_MS) {
                try {
                    p.exitValue();
                    break;
                } catch (IllegalThreadStateException e) {
                    Thread.sleep(30);
                }
            }
            reader.join(600);
            return sb.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) {}
            }
        }
    }
}
