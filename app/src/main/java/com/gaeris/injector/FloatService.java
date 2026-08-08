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
    private View          rootView;
    private WindowManager.LayoutParams params;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification n = new NotificationCompat.Builder(this, CH)
            .setContentTitle("GAERIS Active")
            .setContentText("Floating menu is running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        // Android 14+ requires the type argument
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }

        // Post to main looper so WindowManager is ready
        new Handler(Looper.getMainLooper()).post(this::showFloatMenu);
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (rootView != null && wm != null) {
            try { wm.removeView(rootView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private void showFloatMenu() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Use a themed context so Switch and other widgets render correctly
        Context themed = new ContextThemeWrapper(this,
            android.R.style.Theme_Material);

        rootView = buildUI(themed);

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40;
        params.y = 120;

        try {
            wm.addView(rootView, params);
        } catch (Exception e) {
            Log.e(TAG, "addView failed: " + e);
        }
    }

    private View buildUI(Context ctx) {
        int PAD = dp(ctx, 10);

        // root container
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(0xEE0D0D0D, dp(ctx, 16)));

        // ── title bar (drag handle) ───────────────────────────────────────
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(PAD, dp(ctx, 8), PAD, dp(ctx, 8));
        bar.setBackground(rounded(0xFF1A1A1A, dp(ctx, 16)));

        TextView title = new TextView(ctx);
        title.setText("⬡  GAERIS  v1.0");
        title.setTextColor(0xFFFF3B3B);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeBtn = new TextView(ctx);
        closeBtn.setText("  ✕  ");
        closeBtn.setTextColor(0xFF888888);
        closeBtn.setTextSize(15);
        closeBtn.setOnClickListener(v -> stopSelf());
        bar.addView(closeBtn);

        root.addView(bar, new LinearLayout.LayoutParams(
            dp(ctx, 290), LinearLayout.LayoutParams.WRAP_CONTENT));

        bar.setOnTouchListener(new DragListener());

        // ── red divider ───────────────────────────────────────────────────
        View div = new View(ctx);
        div.setBackgroundColor(0x55FF3B3B);
        root.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // ── feature switches ──────────────────────────────────────────────
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(PAD, dp(ctx, 4), PAD, dp(ctx, 8));

        for (int i = 0; i < Feature.ALL.length; i++) {
            final Feature f   = Feature.ALL[i];
            final int     idx = i;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(ctx, 6), dp(ctx, 8), dp(ctx, 6), dp(ctx, 8));

            // text
            LinearLayout textCol = new LinearLayout(ctx);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tName = new TextView(ctx);
            tName.setText(f.name);
            tName.setTextColor(0xFFEEEEEE);
            tName.setTextSize(13);
            tName.setTypeface(Typeface.DEFAULT_BOLD);
            textCol.addView(tName);

            TextView tDesc = new TextView(ctx);
            tDesc.setText(f.desc);
            tDesc.setTextColor(0xFF666666);
            tDesc.setTextSize(10);
            textCol.addView(tDesc);

            row.addView(textCol);

            // switch
            Switch sw = new Switch(ctx);
            sw.setChecked(false);
            sw.setOnCheckedChangeListener((v, on) ->
                new Thread(() -> MemPatcher.patchAll(f.patches)).start());
            row.addView(sw);

            list.addView(row);

            if (i < Feature.ALL.length - 1) {
                View sep = new View(ctx);
                sep.setBackgroundColor(0x1AFFFFFF);
                list.addView(sep, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
            dp(ctx, 290), dp(ctx, 320)));

        return root;
    }

    // ── drag ─────────────────────────────────────────────────────────────────
    private class DragListener implements View.OnTouchListener {
        float sx, sy; int ix, iy;
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                sx=e.getRawX(); sy=e.getRawY(); ix=params.x; iy=params.y;
            } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                params.x = ix + (int)(e.getRawX()-sx);
                params.y = iy + (int)(e.getRawY()-sy);
                wm.updateViewLayout(rootView, params);
            }
            return true;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
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
