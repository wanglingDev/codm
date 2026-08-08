package com.gaeris.injector;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(0xFF0A0A0A);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(buildUI());
        checkGameStatus();
    }

    private View buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0A0A);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        // header
        TextView header = new TextView(this);
        header.setText("⬡  GAERIS  v1.0");
        header.setTextColor(0xFFFF3B3B);
        header.setTextSize(26);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.CENTER);
        root.addView(header);

        space(root, 4);

        TextView sub = new TextView(this);
        sub.setText("CODM Garena Injector  •  arm64");
        sub.setTextColor(0xFF555555);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        space(root, 24);

        // status card
        LinearLayout statusCard = card(0xFF1A1A1A, 16);
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView tvStatus = new TextView(this);
        tvStatus.setId(android.R.id.text1);
        tvStatus.setText("Checking root & game...");
        tvStatus.setTextColor(0xFF888888);
        tvStatus.setTextSize(12);
        tvStatus.setGravity(Gravity.CENTER);
        statusCard.addView(tvStatus);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(statusCard, cardLp);

        space(root, 20);

        // launch button
        LinearLayout btnLaunch = card(0xFFFF3B3B, 12);
        btnLaunch.setPadding(dp(16), dp(16), dp(16), dp(16));
        btnLaunch.setGravity(Gravity.CENTER);
        TextView tLaunch = new TextView(this);
        tLaunch.setText("LAUNCH MENU");
        tLaunch.setTextColor(0xFFFFFFFF);
        tLaunch.setTextSize(15);
        tLaunch.setTypeface(Typeface.DEFAULT_BOLD);
        tLaunch.setGravity(Gravity.CENTER);
        btnLaunch.addView(tLaunch);
        root.addView(btnLaunch, cardLp);

        space(root, 10);

        // stop button
        LinearLayout btnStop = card(0xFF1E1E1E, 12);
        btnStop.setPadding(dp(16), dp(14), dp(16), dp(14));
        btnStop.setGravity(Gravity.CENTER);
        TextView tStop = new TextView(this);
        tStop.setText("STOP MENU");
        tStop.setTextColor(0xFF666666);
        tStop.setTextSize(14);
        tStop.setGravity(Gravity.CENTER);
        btnStop.addView(tStop);
        root.addView(btnStop, cardLp);

        // button actions
        btnLaunch.setOnClickListener(v -> launchMenu(tvStatus));
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, FloatService.class));
            tvStatus.setText("Stopped.");
            tvStatus.setTextColor(0xFF888888);
        });

        space(root, 20);

        TextView footer = new TextView(this);
        footer.setText("Dump: 25/07/2026  |  com.garena.game.codm");
        footer.setTextColor(0xFF333333);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);

        return root;
    }

    private void launchMenu(TextView tvStatus) {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(i);
            tvStatus.setText("Grant overlay permission, then try again");
            tvStatus.setTextColor(0xFFFFAA00);
            return;
        }
        Intent svc = new Intent(this, FloatService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else                             startService(svc);
        tvStatus.setText("Menu launched! Switch to CODM.");
        tvStatus.setTextColor(0xFF4CAF50);
    }

    private void checkGameStatus() {
        new Thread(() -> {
            String pid = MemPatcher.getPid();
            boolean root = checkRoot();
            runOnUiThread(() -> {
                TextView tv = findViewById(android.R.id.text1);
                if (tv == null) return;
                if (!root) {
                    tv.setText("✖  Root not found — cannot patch");
                    tv.setTextColor(0xFFFF3B3B);
                } else if (pid == null) {
                    tv.setText("✔  Root OK  •  Game not running (open CODM first)");
                    tv.setTextColor(0xFFFFAA00);
                } else {
                    tv.setText("✔  Root OK  •  Game PID " + pid + " found");
                    tv.setTextColor(0xFF4CAF50);
                }
            });
        }).start();
    }

    private boolean checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su","-c","id"});
            java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String out = r.readLine();
            r.close();
            return out != null && out.contains("uid=0");
        } catch (Exception e) { return false; }
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        l.setBackgroundDrawable(d);
        return l;
    }

    private void space(LinearLayout parent, int dpVal) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)));
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }
}
