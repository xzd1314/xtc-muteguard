package com.xzd.yunkong;

/** 一条禁言记录 */
public class BanInfo {
    public final long startTime;
    public final long endTime;
    public final int sanctionState;

    public BanInfo(long startTime, long endTime, int sanctionState) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.sanctionState = sanctionState;
    }

    public boolean isActive() {
        long now = System.currentTimeMillis();
        return endTime > now && startTime <= now;
    }

    /** 剩余天数（向上取整，最小 0） */
    public int remainDays() {
        long diff = endTime - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) Math.ceil(diff / 86400000.0);
    }
}
