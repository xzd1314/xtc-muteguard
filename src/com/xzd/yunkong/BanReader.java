package com.xzd.yunkong;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

/**
 * 读取微聊本地库中的禁言记录。
 *
 * 微聊库：/data/data/com.xtc.weichat/databases/weichat.db
 * 表：db_illegal_record(startTime, endTime, sanctionState, illegalCount, ...)
 *
 * 因为跨 uid，先用 root 复制到本应用 cache，再用 SQLiteDatabase 只读打开。
 */
public final class BanReader {

    private static final String SRC = "/data/data/com.xtc.weichat/databases/weichat.db";

    private BanReader() {}

    public static BanInfo read(Context ctx) {
        File cache = new File(ctx.getCacheDir(), "weichat_probe.db");
        File wal = new File(ctx.getCacheDir(), "weichat_probe.db-wal");
        File shm = new File(ctx.getCacheDir(), "weichat_probe.db-shm");

        String cmd = "cp " + SRC + " " + cache.getAbsolutePath() + " 2>/dev/null; "
                + "cp " + SRC + "-wal " + wal.getAbsolutePath() + " 2>/dev/null; "
                + "cp " + SRC + "-shm " + shm.getAbsolutePath() + " 2>/dev/null; "
                + "chmod 644 " + cache.getAbsolutePath() + " "
                + wal.getAbsolutePath() + " " + shm.getAbsolutePath() + " 2>/dev/null";

        RootUtil.exec(cmd);

        if (!cache.exists() || cache.length() == 0) {
            return null;
        }

        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(cache.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery(
                    "SELECT startTime, endTime, sanctionState FROM db_illegal_record LIMIT 1", null);
            if (c.moveToFirst()) {
                return new BanInfo(c.getLong(0), c.getLong(1), c.getInt(2));
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) {
                try { c.close(); } catch (Throwable ignored) {}
            }
            if (db != null) {
                try { db.close(); } catch (Throwable ignored) {}
            }
        }
    }
}
