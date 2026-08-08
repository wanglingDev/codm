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

    private WindowManager wm;
    private View          root;
    private WindowManager.LayoutParams params;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        MemPatcher.init(this);  // extract patcher binary once
    }

    @Override public int onStartCommand(Intent i, int f, int id) {
        startFg();
        new Handler(Looper.getMainLooper()).post(this::show);
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (root != null) try { wm.removeView(root); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // ── foreground notification ───────────────────────────────────────────────
    private void startFg() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                CH, "GAERIS", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, CH)
            .setContentTitle("GAERIS running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(1, n);
    }

    // ── floating window ───────────────────────────────────────────────────────
    private void show() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // themed context so Switch widget resolves its theme attrs
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

    // ── UI ────────────────────────────────────────────────────────────────────
    private View buildUI(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(0xEE0D0D0D, dp(ctx, 16)));

        // titlebar
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(ctx,12), dp(ctx,8), dp(ctx,12), dp(ctx,8));
        bar.setBackground(round(0xFF161616, dp(ctx, 16)));

        TextView title = new TextView(ctx);
        title.setText("⬡  GAERIS  v1.0");
        title.setTextColor(0xFFFF3B3B);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, wrapW0());

        TextView pid = new TextView(ctx);
        pid.setId(0x7F);
        pid.setText("PID ?");
        pid.setTextColor(0xFF555555);
        pid.setTextSize(10);
        pid.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(pid);

        TextView close = new TextView(ctx);
        close.setText("  ✕  ");
        close.setTextColor(0xFF777777);
        close.setTextSize(15);
        close.setOnClickListener(v -> stopSelf());
        bar.addView(close);

        root.addView(bar, new LinearLayout.LayoutParams(dp(ctx, 300),
            LinearLayout.LayoutParams.WRAP_CONTENT));
        bar.setOnTouchListener(new Dragger());

        // refresh PID display every 3 s
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            final TextView tv = pid;
            @Override public void run() {
                new Thread(() -> {
                    String p = MemPatcher.getPid();
                    tv.post(() -> {
                        tv.setText(p != null ? " PID "+p : " ─");
                        tv.setTextColor(p!=null?0xFF4CAF50:0xFF884444);
                    });
                }).start();
                pid.postDelayed(this, 3000);
            }
        });

        // divider
        View div = new View(ctx); div.setBackgroundColor(0x33FF3B3B);
        root.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // feature list
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(ctx,8), dp(ctx,4), dp(ctx,8), dp(ctx,8));

        for (int i = 0; i < Feature.ALL.length; i++) {
            Feature f = Feature.ALL[i];

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx,6), dp(ctx,7), dp(ctx,6), dp(ctx,7));

            // text block
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(wrapW0());

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
            dot.setTextColor(0xFF333333);
            dot.setTextSize(12);
            dot.setPadding(dp(ctx,4), 0, dp(ctx,4), 0);
            row.addView(dot);

            // toggle switch
            Switch sw = new Switch(ctx);
            sw.setChecked(false);
            sw.setOnCheckedChangeListener((v, on) -> {
                sw.setEnabled(false);
                dot.setTextColor(0xFFFFAA00);
                new Thread(() -> {
                    boolean ok;
                    if (on) {
                        // save originals before patching
                        for (int j = 0; j < f.patches.length; j++) {
                            f.origBytes[j] = MemPatcher.readOriginal(
                                f.patches[j].lib, f.patches[j].rva,
                                f.patches[j].bytes.length);
                        }
                        ok = MemPatcher.patchAll(f.patches);
                    } else {
                        // restore original bytes
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
                        if (!success) sw.setChecked(!on); // revert
                    });
                }).start();
            });
            row.addView(sw);

            list.addView(row);

            if (i < Feature.ALL.length - 1) {
                View sep = new View(ctx); sep.setBackgroundColor(0x15FFFFFF);
                list.addView(sep, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(dp(ctx, 300), dp(ctx, 360)));

        return root;
    }

    // ── drag ──────────────────────────────────────────────────────────────────
    class Dragger implements View.OnTouchListener {
        float sx, sy; int ix, iy;
        public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction()==MotionEvent.ACTION_DOWN){
                sx=e.getRawX();sy=e.getRawY();ix=params.x;iy=params.y;
            } else if (e.getAction()==MotionEvent.ACTION_MOVE){
                params.x=ix+(int)(e.getRawX()-sx);
                params.y=iy+(int)(e.getRawY()-sy);
                wm.updateViewLayout(root, params);
            }
            return true;
        }
    }

    // ── util ──────────────────────────────────────────────────────────────────
    private static GradientDrawable round(int color, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(r); return d;
    }
    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
    private static LinearLayout.LayoutParams wrapW0() {
        return new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }
}
