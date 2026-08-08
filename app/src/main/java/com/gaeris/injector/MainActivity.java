package com.gaeris.injector;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {

    private TextView tvStatus;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0xFF0A0A0A);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(buildUI());
        checkStatus();
    }

    private View buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0A);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        addTextView(root, "⬡  GAERIS  v1.0", 24, 0xFFFF3B3B, Typeface.BOLD, Gravity.CENTER);
        space(root, 4);
        addTextView(root, "CODM Garena  •  arm64  •  root", 12, 0xFF555555, Typeface.NORMAL, Gravity.CENTER);
        space(root, 20);

        // status card
        LinearLayout card = makeCard(0xFF1A1A1A, 12);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        tvStatus = new TextView(this);
        tvStatus.setText("Checking…");
        tvStatus.setTextColor(0xFF888888);
        tvStatus.setTextSize(12);
        tvStatus.setGravity(Gravity.CENTER);
        card.addView(tvStatus);
        root.addView(card, fillW());

        space(root, 20);

        // LAUNCH
        LinearLayout btnL = makeCard(0xFFFF3B3B, 12);
        btnL.setPadding(dp(16), dp(16), dp(16), dp(16));
        btnL.setGravity(Gravity.CENTER);
        addTextView(btnL, "LAUNCH MENU", 15, 0xFFFFFFFF, Typeface.BOLD, Gravity.CENTER);
        btnL.setOnClickListener(v -> launch());
        root.addView(btnL, fillW());

        space(root, 10);

        // STOP
        LinearLayout btnS = makeCard(0xFF1E1E1E, 12);
        btnS.setPadding(dp(16), dp(14), dp(16), dp(14));
        btnS.setGravity(Gravity.CENTER);
        addTextView(btnS, "STOP MENU", 14, 0xFF666666, Typeface.NORMAL, Gravity.CENTER);
        btnS.setOnClickListener(v -> {
            stopService(new Intent(this, FloatService.class));
            setStatus("Stopped.", 0xFF888888);
        });
        root.addView(btnS, fillW());

        space(root, 20);
        addTextView(root, "Dump: 25/07/2026  |  com.garena.game.codm",
            10, 0xFF333333, Typeface.NORMAL, Gravity.CENTER);

        return root;
    }

    private static final String GAME = "com.garena.game.codm";

    private void launch() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
            setStatus("Grant overlay permission, then tap LAUNCH again.", 0xFFFFAA00);
            return;
        }

        // 1) Start FloatService (overlay)
        Intent svc = new Intent(this, FloatService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else                             startService(svc);

        // 2) Launch CODM from Activity context (NOT from Service —
        //    Android 10+ blocks startActivity() from background services).
        //    Activity context has full permission to bring CODM to foreground.
        boolean launched = launchGame();
        setStatus(launched
            ? "✔  Opening CODM + injecting…"
            : "✔  Menu started  •  Open CODM manually", 0xFF4CAF50);
    }

    private boolean launchGame() {
        // Primary: standard launcher intent
        Intent intent = getPackageManager().getLaunchIntentForPackage(GAME);
        if (intent == null) {
            // Fallback: explicit Garena/Unity main activity
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(GAME, GAME + ".UnityPlayerActivity");
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                      | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            setStatus("✖  Cannot open CODM: " + e.getMessage(), 0xFFFF3B3B);
            return false;
        }
    }

    private void checkStatus() {
        new Thread(() -> {
            boolean root = isRooted();
            String  pid  = root ? MemPatcher.getPid() : null;
            runOnUiThread(() -> {
                if (!root)
                    setStatus("✖  No root detected", 0xFFFF3B3B);
                else if (pid == null)
                    setStatus("✔  Root OK  •  Open CODM, then tap LAUNCH", 0xFFFFAA00);
                else
                    setStatus("✔  Root OK  •  Game PID " + pid, 0xFF4CAF50);
            });
        }).start();
    }

    private boolean isRooted() {
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su","-c","id"});
            java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String out = r.readLine(); r.close();
            return out != null && out.contains("uid=0");
        } catch (Exception e) { return false; }
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private void setStatus(String msg, int color) {
        tvStatus.setText(msg); tvStatus.setTextColor(color);
    }

    private void addTextView(ViewGroup parent, String text, float sp,
                              int color, int style, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(color); tv.setTextSize(sp);
        tv.setTypeface(Typeface.defaultFromStyle(style)); tv.setGravity(gravity);
        parent.addView(tv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout makeCard(int color, int radius) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        l.setBackground(d);
        return l;
    }

    private void space(ViewGroup p, int dpVal) {
        View v = new View(this);
        p.addView(v, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)));
    }

    private LinearLayout.LayoutParams fillW() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
