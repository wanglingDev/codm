package com.gaeris.injector;

import android.app.*;
import android.content.*;
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
    private View          rootView;
    private WindowManager.LayoutParams params;
    private boolean[]     states;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification n = new NotificationCompat.Builder(this, CH)
            .setContentTitle("GAERIS Active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
        startForeground(1, n);
        showFloatMenu();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (rootView != null && wm != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    // ── floating window ───────────────────────────────────────────────────
    private void showFloatMenu() {
        wm     = (WindowManager) getSystemService(WINDOW_SERVICE);
        states = new boolean[Feature.ALL.length];

        rootView = buildUI();

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            // NOT_FOCUSABLE: game keeps keyboard focus; touch inside window works
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40; params.y = 120;

        wm.addView(rootView, params);
    }

    private View buildUI() {
        Context ctx = this;

        // ── container ────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundDrawable(rounded(0xFF0D0D0D, 24));

        // ── title bar ────────────────────────────────────────────────────
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bar.setBackgroundDrawable(rounded(0xFF1A1A1A, 24));

        TextView title = new TextView(ctx);
        title.setText("⬡  GAERIS  v1.0");
        title.setTextColor(0xFFFF3B3B);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, lp);

        TextView close = new TextView(ctx);
        close.setText("✕");
        close.setTextColor(0xFF888888);
        close.setTextSize(16);
        close.setPadding(dp(8), 0, 0, 0);
        close.setOnClickListener(v -> stopSelf());
        bar.addView(close);

        root.addView(bar);

        // drag the title bar
        bar.setOnTouchListener(new DragListener());

        // ── divider ──────────────────────────────────────────────────────
        View div = new View(ctx);
        div.setBackgroundColor(0x44FF3B3B);
        root.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // ── feature rows ─────────────────────────────────────────────────
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(6), dp(10), dp(10));

        for (int i = 0; i < Feature.ALL.length; i++) {
            final Feature f   = Feature.ALL[i];
            final int     idx = i;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(8), dp(6), dp(8), dp(6));

            // text column
            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textCol.setLayoutParams(tcLp);

            TextView tName = new TextView(ctx);
            tName.setText(f.name);
            tName.setTextColor(0xFFFFFFFF);
            tName.setTextSize(13);
            tName.setTypeface(Typeface.DEFAULT_BOLD);
            textCol.addView(tName);

            TextView tDesc = new TextView(ctx);
            tDesc.setText(f.desc);
            tDesc.setTextColor(0xFF777777);
            tDesc.setTextSize(10);
            textCol.addView(tDesc);

            row.addView(textCol);

            // switch
            Switch sw = new Switch(ctx);
            sw.setChecked(false);
            sw.setOnCheckedChangeListener((v, checked) -> {
                states[idx] = checked;
                new Thread(() -> {
                    boolean ok = MemPatcher.patchAll(f.patches);
                    Log.i(TAG, f.name + " -> " + checked + " | ok=" + ok);
                }).start();
            });
            row.addView(sw);

            list.addView(row);

            // thin separator
            if (i < Feature.ALL.length - 1) {
                View sep = new View(ctx);
                sep.setBackgroundColor(0x22FFFFFF);
                list.addView(sep, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }

        scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            dp(280), dp(340));
        root.addView(scroll, scrollLp);
        return root;
    }

    // ── drag touch listener ───────────────────────────────────────────────
    private class DragListener implements View.OnTouchListener {
        float startX, startY;
        int   initX, initY;

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX(); startY = e.getRawY();
                    initX  = params.x;    initY  = params.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = initX + (int)(e.getRawX() - startX);
                    params.y = initY + (int)(e.getRawY() - startY);
                    wm.updateViewLayout(rootView, params);
                    return true;
            }
            return false;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────
    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                CH, "GAERIS", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
}
