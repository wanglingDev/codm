package com.gaeris.injector;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationCompat;

public class FloatService extends Service {
    private static final String TAG = "GAERIS";
    private static final String CH  = "gaeris_ch";
    private static final String GAME = "com.garena.game.codm";

    private WindowManager wm;
    private View          root;
    private WindowManager.LayoutParams params;

    // ── auto-inject state ────────────────────────────────────────────────────
    private volatile boolean autoInject      = true;   // toggle dari UI
    private volatile boolean injectedThisRun = false;  // sudah inject ke PID ini?
    private volatile String  lastInjectedPid = null;
    private Handler          watchHandler;
    private Runnable         watchRunnable;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        MemPatcher.init(this);
    }

    @Override public int onStartCommand(Intent i, int f, int id) {
        startFg();
        new Handler(Looper.getMainLooper()).post(this::show);
        startGameWatcher();
        return START_STICKY;   // restart otomatis kalau di-kill
    }

    @Override public void onDestroy() {
        stopGameWatcher();
        if (root != null) try { wm.removeView(root); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GAME WATCHER — poll PID tiap 2 detik, auto-inject kalau game launch
    // ════════════════════════════════════════════════════════════════════════
    private void startGameWatcher() {
        watchHandler = new Handler(Looper.getMainLooper());
        watchRunnable = new Runnable() {
            @Override public void run() {
                new Thread(() -> {
                    String pid = MemPatcher.getPid();

                    if (pid != null) {
                        // Game running
                        updatePidDisplay(pid);

                        // Auto-inject: hanya kalau PID baru (game baru launch)
                        if (autoInject && !pid.equals(lastInjectedPid)) {
                            Log.i(TAG, "[WATCHER] New game PID=" + pid + " — auto injecting");
                            showToast("CODM detected! Auto-injecting...");
                            doAutoInject(pid);
                            lastInjectedPid = pid;
                        }
                    } else {
                        // Game tidak running
                        updatePidDisplay(null);
                        if (lastInjectedPid != null) {
                            Log.i(TAG, "[WATCHER] Game exited, reset inject state");
                            lastInjectedPid = null;   // allow re-inject on next launch
                        }
                    }
                }).start();

                // Schedule ulang
                watchHandler.postDelayed(this, 2000);
            }
        };
        watchHandler.post(watchRunnable);
        Log.i(TAG, "[WATCHER] started");
    }

    private void stopGameWatcher() {
        if (watchHandler != null && watchRunnable != null)
            watchHandler.removeCallbacks(watchRunnable);
    }

    private void doAutoInject(String pid) {
        // Patch semua feature yang toggle ON
        int success = 0, fail = 0;
        for (Feature f : Feature.ALL) {
            if (!f.autoActive) continue;

            // Save originals
            for (int j = 0; j < f.patches.length; j++) {
                f.origBytes[j] = MemPatcher.readOriginal(
                    f.patches[j].lib, f.patches[j].rva, f.patches[j].bytes.length);
            }

            if (MemPatcher.patchAll(f.patches)) {
                f.active = true;
                success++;
            } else {
                fail++;
            }
        }

        final int s = success, fl = fail;
        showToast("Injected: " + s + " OK, " + fl + " fail");
        Log.i(TAG, "[AUTO] inject done: " + s + " OK " + fl + " fail");
        refreshSwitches();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FLOATING UI
    // ════════════════════════════════════════════════════════════════════════
    private View pidDisplay;
    private TextView tvAutoStatus;

    private void show() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Context ctx = new ContextThemeWrapper(this, android.R.style.Theme_Material);
        root = buildUI(ctx);

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40; params.y = 120;

        try { wm.addView(root, params); }
        catch (Exception e) { Log.e(TAG, "addView: " + e); }
    }

    private View buildUI(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(0xEE0D0D0D, dp(ctx, 16)));

        // ── Title bar ─────────────────────────────────────────────────────
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(ctx,12), dp(ctx,8), dp(ctx,12), dp(ctx,8));
        bar.setBackground(round(0xFF161616, dp(ctx, 16)));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(ctx);
        title.setText("⬡  GAERIS  v1.0");
        title.setTextColor(0xFFFF3B3B);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, titleLp);

        // PID badge
        TextView pidTv = new TextView(ctx);
        pidTv.setId(android.R.id.text1);
        pidTv.setText("─");
        pidTv.setTextColor(0xFF555555);
        pidTv.setTextSize(10);
        pidTv.setPadding(dp(ctx,6), 0, dp(ctx,6), 0);
        bar.addView(pidTv);
        pidDisplay = pidTv;

        // Close
        TextView close = new TextView(ctx);
        close.setText(" ✕ ");
        close.setTextColor(0xFF777777);
        close.setTextSize(15);
        close.setOnClickListener(v -> stopSelf());
        bar.addView(close);

        root.addView(bar, new LinearLayout.LayoutParams(dp(ctx, 300),
            LinearLayout.LayoutParams.WRAP_CONTENT));
        bar.setOnTouchListener(new Dragger());

        // ── Auto-inject control strip ──────────────────────────────────────
        LinearLayout autoRow = new LinearLayout(ctx);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        autoRow.setGravity(Gravity.CENTER_VERTICAL);
        autoRow.setPadding(dp(ctx,10), dp(ctx,6), dp(ctx,10), dp(ctx,6));
        autoRow.setBackgroundColor(0xFF111111);

        tvAutoStatus = new TextView(ctx);
        tvAutoStatus.setText(autoInject ? "Auto-inject: ON" : "Auto-inject: OFF");
        tvAutoStatus.setTextColor(autoInject ? 0xFF4CAF50 : 0xFF555555);
        tvAutoStatus.setTextSize(11);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        autoRow.addView(tvAutoStatus, aLp);

        Switch autoSw = new Switch(ctx);
        autoSw.setChecked(autoInject);
        autoSw.setOnCheckedChangeListener((v, on) -> {
            autoInject = on;
            tvAutoStatus.setText(on ? "Auto-inject: ON" : "Auto-inject: OFF");
            tvAutoStatus.setTextColor(on ? 0xFF4CAF50 : 0xFF555555);
            if (on) lastInjectedPid = null; // reset agar bisa re-inject
        });
        autoRow.addView(autoSw);

        root.addView(autoRow, new LinearLayout.LayoutParams(
            dp(ctx, 300), LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Launch CODM button ────────────────────────────────────────────
        LinearLayout btnLaunch = makeBtnRow(ctx, "▶  LAUNCH CODM", 0x220077FF, 0xFF0099FF);
        btnLaunch.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(GAME);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                showToast("CODM not installed");
            }
        });
        root.addView(btnLaunch, new LinearLayout.LayoutParams(
            dp(ctx, 300), LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Divider ───────────────────────────────────────────────────────
        View div = new View(ctx);
        div.setBackgroundColor(0x33FF3B3B);
        root.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // ── Feature list ─────────────────────────────────────────────────
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(ctx,8), dp(ctx,4), dp(ctx,8), dp(ctx,8));

        for (int i = 0; i < Feature.ALL.length; i++) {
            Feature f = Feature.ALL[i];

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx,6), dp(ctx,8), dp(ctx,6), dp(ctx,8));

            // text block
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            col.setLayoutParams(colLp);

            TextView tName = new TextView(ctx);
            tName.setText(f.name);
            tName.setTextColor(0xFFEEEEEE);
            tName.setTextSize(13);
            tName.setTypeface(Typeface.DEFAULT_BOLD);
            col.addView(tName);

            TextView tDesc = new TextView(ctx);
            tDesc.setText(f.desc);
            tDesc.setTextColor(0xFF555555);
            tDesc.setTextSize(10);
            col.addView(tDesc);

            row.addView(col);

            // status dot
            TextView dot = new TextView(ctx);
            dot.setText("●");
            dot.setTextColor(f.active ? 0xFF4CAF50 : 0xFF333333);
            dot.setTextSize(12);
            dot.setPadding(dp(ctx,4), 0, dp(ctx,4), 0);
            row.addView(dot);

            // main toggle switch
            Switch sw = new Switch(ctx);
            sw.setChecked(f.active);
            f.uiSwitch = sw;
            f.uiDot    = dot;

            sw.setOnCheckedChangeListener((v, on) -> {
                sw.setEnabled(false);
                dot.setTextColor(0xFFFFAA00);
                new Thread(() -> {
                    boolean ok;
                    if (on) {
                        for (int j = 0; j < f.patches.length; j++) {
                            f.origBytes[j] = MemPatcher.readOriginal(
                                f.patches[j].lib, f.patches[j].rva,
                                f.patches[j].bytes.length);
                        }
                        ok = MemPatcher.patchAll(f.patches);
                    } else {
                        ok = true;
                        for (int j = 0; j < f.patches.length; j++) {
                            if (f.origBytes[j] != null)
                                ok &= MemPatcher.patch(
                                    f.patches[j].lib, f.patches[j].rva,
                                    f.origBytes[j]);
                        }
                    }
                    final boolean success = ok;
                    sw.post(() -> {
                        sw.setEnabled(true);
                        f.active      = success && on;
                        f.autoActive  = f.active; // sync auto-inject list
                        dot.setTextColor(success
                            ? (on ? 0xFF4CAF50 : 0xFF333333)
                            : 0xFFFF3B3B);
                        if (!success) sw.setChecked(!on);
                    });
                }).start();
            });
            row.addView(sw);

            list.addView(row);

            if (i < Feature.ALL.length - 1) {
                View sep = new View(ctx);
                sep.setBackgroundColor(0x15FFFFFF);
                list.addView(sep, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(dp(ctx, 300), dp(ctx, 340)));

        // ── Inject now button ─────────────────────────────────────────────
        LinearLayout btnNow = makeBtnRow(ctx, "⚡  INJECT NOW", 0x22FF3B3B, 0xFFFF3B3B);
        btnNow.setOnClickListener(v -> {
            new Thread(() -> {
                String pid = MemPatcher.getPid();
                if (pid == null) { showToast("CODM not running!"); return; }
                doAutoInject(pid);
                lastInjectedPid = pid;
            }).start();
        });
        root.addView(btnNow, new LinearLayout.LayoutParams(
            dp(ctx, 300), LinearLayout.LayoutParams.WRAP_CONTENT));

        return root;
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private LinearLayout makeBtnRow(Context ctx, String label, int bg, int textColor) {
        LinearLayout btn = new LinearLayout(ctx);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(ctx,12), dp(ctx,10), dp(ctx,12), dp(ctx,10));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        btn.setBackground(d);
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(textColor);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        btn.addView(tv);
        return btn;
    }

    private void updatePidDisplay(String pid) {
        if (pidDisplay == null) return;
        ((TextView) pidDisplay).post(() -> {
            if (pid != null) {
                ((TextView) pidDisplay).setText("PID " + pid);
                ((TextView) pidDisplay).setTextColor(0xFF4CAF50);
            } else {
                ((TextView) pidDisplay).setText("─");
                ((TextView) pidDisplay).setTextColor(0xFF555555);
            }
        });
    }

    private void refreshSwitches() {
        for (Feature f : Feature.ALL) {
            if (f.uiSwitch != null) {
                f.uiSwitch.post(() -> {
                    f.uiSwitch.setChecked(f.active);
                    if (f.uiDot != null)
                        f.uiDot.setTextColor(f.active ? 0xFF4CAF50 : 0xFF333333);
                });
            }
        }
    }

    private void showToast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private void startFg() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                CH, "GAERIS", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, CH)
            .setContentTitle("GAERIS — watching for CODM")
            .setContentText("Auto-inject active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1, n);
    }

    class Dragger implements View.OnTouchListener {
        float sx, sy; int ix, iy;
        public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                sx = e.getRawX(); sy = e.getRawY();
                ix = params.x;   iy = params.y;
            } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                params.x = ix + (int)(e.getRawX() - sx);
                params.y = iy + (int)(e.getRawY() - sy);
                wm.updateViewLayout(root, params);
            }
            return true;
        }
    }

    private static GradientDrawable round(int color, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(r); return d;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
