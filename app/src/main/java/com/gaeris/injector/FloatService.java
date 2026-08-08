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
    private static final String TAG  = "GAERIS";
    private static final String CH   = "gaeris_ch";
    private static final String GAME = "com.garena.game.codm";

    private WindowManager              wm;
    private WindowManager.LayoutParams paramsMenu;   // full menu (focusable)
    private WindowManager.LayoutParams paramsBubble; // mini bubble (pass-through)

    private View menuRoot;   // full menu view
    private View bubbleRoot; // mini ● button shown when menu is hidden

    private boolean menuVisible = true;

    // ── inject state ─────────────────────────────────────────────────────────
    private volatile boolean injected      = false;
    private volatile String  injectedPid   = null;
    private volatile TextView tvInjectStatus = null; // live status line in overlay

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        MemPatcher.init(this);
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        startFg();
        // Android 15+ requires overlay to be VISIBLE before/during foreground
        // service lifecycle. Show immediately, then inject in background.
        showOverlay();
        new Thread(this::injectThenLaunch).start();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        removeAllViews();
        super.onDestroy();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  INJECT → status updates live in overlay (shown before this runs)
    // ════════════════════════════════════════════════════════════════════════
    private void injectThenLaunch() {
        sleep(800); // let overlay render on main thread first

        // 1) Wait for CODM (launched by MainActivity before service start)
        String pid = MemPatcher.getPid();
        if (pid == null) {
            updateStatus("Waiting for CODM…", 0xFFFFAA00);
            Log.i(TAG, "Waiting for CODM to start...");
            for (int t = 0; t < 60 && pid == null; t++) {
                sleep(1000);
                pid = MemPatcher.getPid();
            }
        }

        if (pid == null) {
            updateStatus("✖  CODM not found (60s)", 0xFFFF3B3B);
            return;
        }

        updateStatus("Injecting… PID " + pid, 0xFFFFAA00);
        Log.i(TAG, "Game PID=" + pid + " — injecting");

        // 2) Inject all features
        boolean anyOk = false;
        for (Feature f : Feature.ALL) {
            for (int j = 0; j < f.patches.length; j++) {
                f.origBytes[j] = MemPatcher.readOriginal(
                    f.patches[j].lib, f.patches[j].rva,
                    f.patches[j].bytes.length);
            }
            boolean ok = MemPatcher.patchAll(f.patches);
            f.active = ok;
            if (ok) anyOk = true;
            Log.i(TAG, (ok ? "✔ " : "✖ ") + f.name);
        }

        injected    = anyOk;
        injectedPid = anyOk ? pid : null;

        if (anyOk) {
            updateStatus("✔  Injected — PID " + pid, 0xFF4CAF50);
            refreshSwitches();
        } else {
            updateStatus("✖  Inject failed — check root", 0xFFFF3B3B);
        }
    }

    // Update the status TextView in the overlay (safe from any thread)
    private void updateStatus(String msg, int color) {
        Log.i(TAG, "Status: " + msg);
        TextView tv = tvInjectStatus;
        if (tv != null) tv.post(() -> {
            tv.setText(msg);
            tv.setTextColor(color);
        });
    }

    private void launchCODM() {
        // Primary: standard launch intent
        Intent intent = getPackageManager().getLaunchIntentForPackage(GAME);
        if (intent == null) {
            // Fallback: explicit Garena main activity (some Garena variants
            // hide the launcher intent from getLaunchIntentForPackage)
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(GAME, GAME + ".UnityPlayerActivity");
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                      | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
            Log.i(TAG, "CODM launched");
        } catch (Exception e) {
            Log.e(TAG, "launchCODM failed: " + e.getMessage());
            updateStatus("✖  Cannot launch CODM", 0xFFFF3B3B);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  OVERLAY — full menu + mini bubble
    // ════════════════════════════════════════════════════════════════════════
    private void showOverlay() {
        new Handler(Looper.getMainLooper()).post(() -> {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            buildMenuParams();
            buildBubbleParams();
            menuRoot   = buildMenuView();
            bubbleRoot = buildBubbleView();
            wm.addView(menuRoot,   paramsMenu);
            wm.addView(bubbleRoot, paramsBubble);
            setMenuVisible(true);
        });
    }

    // ── WindowManager params ─────────────────────────────────────────────────
    private void buildMenuParams() {
        paramsMenu = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            // NO FLAG_NOT_FOCUSABLE → touch events reach Switch/buttons
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);
        paramsMenu.gravity = Gravity.TOP | Gravity.START;
        paramsMenu.x = 40; paramsMenu.y = 120;
        paramsMenu.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
    }

    private void buildBubbleParams() {
        paramsBubble = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        paramsBubble.gravity = Gravity.TOP | Gravity.START;
        paramsBubble.x = 16; paramsBubble.y = 120;
    }

    // ── toggle visibility ────────────────────────────────────────────────────
    private void setMenuVisible(boolean visible) {
        menuVisible = visible;
        if (menuRoot == null || bubbleRoot == null) return;
        menuRoot.setVisibility(visible ? View.VISIBLE : View.GONE);
        bubbleRoot.setVisibility(visible ? View.GONE : View.VISIBLE);

        // Update focusability: when menu hidden, pass touches through
        if (visible) {
            paramsMenu.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            paramsMenu.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        try { wm.updateViewLayout(menuRoot, paramsMenu); } catch (Exception ignored) {}
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BUILD MENU VIEW
    // ════════════════════════════════════════════════════════════════════════
    private View buildMenuView() {
        Context ctx = new ContextThemeWrapper(this, android.R.style.Theme_Material);
        int W = dp(ctx, 300);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(0xF00D0D0D, dp(ctx, 14)));

        // ── Title bar (draggable) ─────────────────────────────────────────
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(ctx,12), dp(ctx,8), dp(ctx,8), dp(ctx,8));
        bar.setBackground(round(0xFF161616, dp(ctx, 14)));

        TextView title = new TextView(ctx);
        title.setText("⬡  GAERIS");
        title.setTextColor(0xFFFF3B3B);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, titleLp);

        // PID badge
        TextView pidTv = new TextView(ctx);
        pidTv.setText(injectedPid != null ? "PID " + injectedPid : "─");
        pidTv.setTextColor(injectedPid != null ? 0xFF4CAF50 : 0xFF555555);
        pidTv.setTextSize(10);
        pidTv.setPadding(dp(ctx,4), 0, dp(ctx,8), 0);
        bar.addView(pidTv);

        // X → toggle (not close!)
        TextView xBtn = new TextView(ctx);
        xBtn.setText(" ✕ ");
        xBtn.setTextColor(0xFF777777);
        xBtn.setTextSize(16);
        xBtn.setOnClickListener(v -> setMenuVisible(false));
        bar.addView(xBtn);

        root.addView(bar, new LinearLayout.LayoutParams(W,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        bar.setOnTouchListener(new Dragger(root));

        // ── Divider ────────────────────────────────────────────────────────
        View div = new View(ctx);
        div.setBackgroundColor(0x33FF3B3B);
        root.addView(div, new LinearLayout.LayoutParams(W, 1));

        // ── Status bar ──────────────────────────────────────────────────────
        LinearLayout statusRow = new LinearLayout(ctx);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(ctx,12), dp(ctx,5), dp(ctx,12), dp(ctx,5));
        statusRow.setBackgroundColor(0xFF0A0A0A);

        TextView statusTv = new TextView(ctx);
        statusTv.setText(injected
            ? "✔  Injected — PID " + injectedPid
            : "✖  Waiting…");
        statusTv.setTextColor(injected ? 0xFF4CAF50 : 0xFFFFAA00);
        statusTv.setTextSize(10);
        tvInjectStatus = statusTv; // live reference for updateStatus()
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusRow.addView(statusTv, sLp);

        // Re-inject button
        TextView reinjectBtn = new TextView(ctx);
        reinjectBtn.setText("⟳");
        reinjectBtn.setTextColor(0xFF0099FF);
        reinjectBtn.setTextSize(18);
        reinjectBtn.setPadding(dp(ctx,8), 0, 0, 0);
        reinjectBtn.setOnClickListener(v -> {
            statusTv.setText("Injecting...");
            statusTv.setTextColor(0xFFFFAA00);
            new Thread(() -> {
                String pid = MemPatcher.getPid();
                if (pid == null) { showToast("CODM not running"); return; }
                boolean ok = false;
                for (Feature f : Feature.ALL) {
                    for (int j = 0; j < f.patches.length; j++) {
                        f.origBytes[j] = MemPatcher.readOriginal(
                            f.patches[j].lib, f.patches[j].rva,
                            f.patches[j].bytes.length);
                    }
                    boolean r = MemPatcher.patchAll(f.patches);
                    f.active = r;
                    if (r) ok = true;
                }
                final boolean done = ok;
                injected    = ok;
                injectedPid = ok ? pid : null;
                statusTv.post(() -> {
                    statusTv.setText(done ? "✔  Injected — PID " + pid : "✖  Failed");
                    statusTv.setTextColor(done ? 0xFF4CAF50 : 0xFFFF3B3B);
                    pidTv.setText(done ? "PID " + pid : "─");
                    pidTv.setTextColor(done ? 0xFF4CAF50 : 0xFF555555);
                    refreshSwitches();
                });
            }).start();
        });
        statusRow.addView(reinjectBtn);

        root.addView(statusRow, new LinearLayout.LayoutParams(W,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Feature list ───────────────────────────────────────────────────
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(ctx,8), dp(ctx,4), dp(ctx,8), dp(ctx,8));

        for (int i = 0; i < Feature.ALL.length; i++) {
            Feature f = Feature.ALL[i];

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx,6), dp(ctx,9), dp(ctx,6), dp(ctx,9));

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

            TextView dot = new TextView(ctx);
            dot.setText("●");
            dot.setTextColor(f.active ? 0xFF4CAF50 : 0xFF333333);
            dot.setTextSize(11);
            dot.setPadding(dp(ctx,4), 0, dp(ctx,4), 0);
            row.addView(dot);

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
                        f.active = success && on;
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
        root.addView(scroll, new LinearLayout.LayoutParams(W, dp(ctx, 350)));

        return root;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BUILD BUBBLE VIEW (mini toggle shown when menu is hidden)
    // ════════════════════════════════════════════════════════════════════════
    private View buildBubbleView() {
        Context ctx = new ContextThemeWrapper(this, android.R.style.Theme_Material);

        TextView bubble = new TextView(ctx);
        bubble.setText("⬡");
        bubble.setTextColor(0xFFFF3B3B);
        bubble.setTextSize(28);
        bubble.setPadding(dp(ctx,8), dp(ctx,4), dp(ctx,8), dp(ctx,4));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xDD161616);
        bg.setStroke(dp(ctx,2), 0xFFFF3B3B);
        bubble.setBackground(bg);

        bubble.setOnClickListener(v -> setMenuVisible(true));

        // Drag for bubble too
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ix, iy; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY();
                        ix = paramsBubble.x; iy = paramsBubble.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        paramsBubble.x = ix + (int)(e.getRawX() - sx);
                        paramsBubble.y = iy + (int)(e.getRawY() - sy);
                        if (Math.abs(e.getRawX()-sx)>8 || Math.abs(e.getRawY()-sy)>8)
                            moved = true;
                        try { wm.updateViewLayout(bubbleRoot, paramsBubble); }
                        catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) setMenuVisible(true);
                        return true;
                }
                return false;
            }
        });

        return bubble;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════════
    private void refreshSwitches() {
        for (Feature f : Feature.ALL) {
            if (f.uiSwitch != null) f.uiSwitch.post(() -> {
                f.uiSwitch.setChecked(f.active);
                if (f.uiDot != null)
                    f.uiDot.setTextColor(f.active ? 0xFF4CAF50 : 0xFF333333);
            });
        }
    }

    private void removeAllViews() {
        try { if (menuRoot   != null) wm.removeView(menuRoot);   } catch (Exception ignored) {}
        try { if (bubbleRoot != null) wm.removeView(bubbleRoot); } catch (Exception ignored) {}
    }

    private void showToast(String msg) {
        new Handler(Looper.getMainLooper()).post(
            () -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (Exception ignored) {}
    }

    private void startFg() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                CH, "GAERIS", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, CH)
            .setContentTitle("GAERIS — injecting")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1, n);
    }

    // ── Drag titlebar ──────────────────────────────────────────────────────
    class Dragger implements View.OnTouchListener {
        private final View target;
        float sx, sy; int ix, iy;
        Dragger(View target) { this.target = target; }
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sx = e.getRawX(); sy = e.getRawY();
                    ix = paramsMenu.x; iy = paramsMenu.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    paramsMenu.x = ix + (int)(e.getRawX() - sx);
                    paramsMenu.y = iy + (int)(e.getRawY() - sy);
                    try { wm.updateViewLayout(target, paramsMenu); }
                    catch (Exception ignored) {}
                    return true;
            }
            return false;
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
