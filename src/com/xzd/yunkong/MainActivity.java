package com.xzd.yunkong;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String CONF_PATH = "/data/local/tmp/muteguard.conf";
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private LinearLayout cardStatus;
    private ImageView ivStatusIcon;
    private TextView tvStatus, tvStatusDesc;
    private TextView tvEnd, tvRemain, tvRemainLabel, tvSwitchDesc;
    private TextView tvMagisk, tvXposed, tvModule, tvWeichat;
    private Switch swEnable;
    private ImageButton btnRefresh;
    private LinearLayout btnRestart;
    private ScrollView scrollRoot;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean binding = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardStatus = findViewById(R.id.cardStatus);
        ivStatusIcon = findViewById(R.id.ivStatusIcon);
        tvStatus = findViewById(R.id.tvStatus);
        tvStatusDesc = findViewById(R.id.tvStatusDesc);
        tvEnd = findViewById(R.id.tvEnd);
        tvRemain = findViewById(R.id.tvRemain);
        tvRemainLabel = findViewById(R.id.tvRemainLabel);
        tvSwitchDesc = findViewById(R.id.tvSwitchDesc);
        tvMagisk = findViewById(R.id.tvMagisk);
        tvXposed = findViewById(R.id.tvXposed);
        tvModule = findViewById(R.id.tvModule);
        tvWeichat = findViewById(R.id.tvWeichat);
        swEnable = findViewById(R.id.swEnable);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnRestart = findViewById(R.id.btnRestart);
        scrollRoot = findViewById(R.id.scrollRoot);

        swEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (binding) return;
                applySwitch(isChecked);
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadData(true);
            }
        });

        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartWeichat();
            }
        });

        scrollToTop();
        loadData(false);
    }

    /** 内容超过一屏，强制回到顶部，避免 Switch 抢焦点导致自动滚动 */
    private void scrollToTop() {
        if (scrollRoot == null) return;
        scrollRoot.post(new Runnable() {
            @Override
            public void run() {
                scrollRoot.scrollTo(0, 0);
            }
        });
    }

    private void loadData(final boolean manual) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean root = RootUtil.hasRoot();
                final boolean enabled = readSwitch();
                final BanInfo info = root ? BanReader.read(MainActivity.this) : null;
                final SystemInfo si = root ? SystemInfo.load(MainActivity.this) : new SystemInfo();

                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        render(root, enabled, info, si);
                        if (manual) {
                            Toast.makeText(MainActivity.this,
                                    root ? R.string.action_refresh : R.string.toast_no_root,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void render(boolean root, boolean enabled, BanInfo info, SystemInfo si) {
        // ---- 禁言记录 ----
        if (info == null) {
            tvEnd.setText(R.string.label_none);
            tvRemain.setText(R.string.label_none);
        } else {
            tvEnd.setText(FMT.format(new Date(info.endTime)));
            tvRemain.setText(getString(R.string.label_days, info.remainDays()));
        }

        // ---- 环境信息 ----
        tvMagisk.setText(si.magiskVersion);
        tvXposed.setText(si.xposedVersion);
        tvModule.setText(si.moduleState);
        tvWeichat.setText(si.weichatVersion);

        // ---- 状态卡（M3 语义色）----
        boolean active = si.moduleEnabled;
        int iconColor;

        if (!root) {
            cardStatus.setBackgroundResource(R.drawable.bg_card_error);
            tvStatus.setText(R.string.status_noroot);
            tvStatusDesc.setText(R.string.toast_no_root);
            tvStatus.setTextColor(getResources().getColor(R.color.md_on_error_container));
            tvStatusDesc.setTextColor(getResources().getColor(R.color.md_on_error_container));
            ivStatusIcon.setImageResource(R.drawable.ic_block);
            iconColor = getResources().getColor(R.color.md_error);
        } else if (!active) {
            cardStatus.setBackgroundResource(R.drawable.bg_card_error);
            tvStatus.setText(R.string.status_inactive);
            tvStatusDesc.setText(R.string.status_inactive_desc);
            tvStatus.setTextColor(getResources().getColor(R.color.md_on_error_container));
            tvStatusDesc.setTextColor(getResources().getColor(R.color.md_on_error_container));
            ivStatusIcon.setImageResource(R.drawable.ic_block);
            iconColor = getResources().getColor(R.color.md_error);
        } else if (!enabled) {
            cardStatus.setBackgroundResource(R.drawable.bg_card);
            tvStatus.setText(R.string.status_off);
            tvStatusDesc.setText(R.string.status_off_desc);
            tvStatus.setTextColor(getResources().getColor(R.color.md_on_surface));
            tvStatusDesc.setTextColor(getResources().getColor(R.color.md_on_surface_variant));
            ivStatusIcon.setImageResource(R.drawable.ic_block);
            iconColor = getResources().getColor(R.color.md_outline);
        } else {
            cardStatus.setBackgroundResource(R.drawable.bg_card_primary);
            tvStatus.setText(R.string.status_blocked);
            tvStatusDesc.setText(R.string.status_blocked_desc);
            tvStatus.setTextColor(getResources().getColor(R.color.md_on_primary_container));
            tvStatusDesc.setTextColor(getResources().getColor(R.color.md_on_primary_container));
            ivStatusIcon.setImageResource(R.drawable.ic_shield);
            iconColor = getResources().getColor(R.color.md_on_primary_container);
        }
        ivStatusIcon.setColorFilter(iconColor);

        // ---- 开关：模块未启用时禁止操作（M3 禁用态 38% 透明度）----
        binding = true;
        swEnable.setChecked(enabled);
        binding = false;
        swEnable.setEnabled(active);
        swEnable.setAlpha(active ? 1f : 0.38f);
        tvSwitchDesc.setText(enabled ? R.string.switch_desc_on : R.string.switch_desc_off);

        scrollToTop();
    }

    private void applySwitch(final boolean on) {
        tvSwitchDesc.setText(on ? R.string.switch_desc_on : R.string.switch_desc_off);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = writeSwitch(on);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!ok) {
                            Toast.makeText(MainActivity.this, R.string.toast_no_root,
                                    Toast.LENGTH_SHORT).show();
                            binding = true;
                            swEnable.setChecked(!on);
                            binding = false;
                            return;
                        }
                        Toast.makeText(MainActivity.this, R.string.toast_switch_ok,
                                Toast.LENGTH_SHORT).show();
                        loadData(false);
                    }
                });
            }
        }).start();
    }

    /** 一键重启微聊：force-stop 后重新拉起 */
    private void restartWeichat() {
        Toast.makeText(this, R.string.toast_restarting, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String out = RootUtil.exec(
                        "am force-stop com.xtc.weichat; sleep 1; "
                                + "am start -n com.xtc.weichat/.module.WeiChatActivity");
                final boolean ok = out != null;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!ok) {
                            Toast.makeText(MainActivity.this, R.string.toast_restart_fail,
                                    Toast.LENGTH_SHORT).show();
                        }
                        ui.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                loadData(false);
                            }
                        }, 2500);
                    }
                });
            }
        }).start();
    }

    /** 读取开关：内容 0 表示关闭，其余（含文件不存在）视为开启 */
    private boolean readSwitch() {
        String out = RootUtil.exec("cat " + CONF_PATH + " 2>/dev/null");
        if (out == null) return true;
        String s = out.trim();
        if (s.isEmpty()) return true;
        return !"0".equals(s);
    }

    /** 写开关：内容 1/0，权限 644 供微聊进程读取 */
    private boolean writeSwitch(boolean on) {
        String val = on ? "1" : "0";
        String cmd = "echo " + val + " > " + CONF_PATH + "; chmod 644 " + CONF_PATH
                + "; ls -l " + CONF_PATH;
        String out = RootUtil.exec(cmd);
        return out != null && out.contains(CONF_PATH);
    }
}
