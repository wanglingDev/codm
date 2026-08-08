package com.gaeris.injector;

import android.content.Context;
import android.util.Log;
import java.io.*;

/**
 * Root memory patcher.
 *
 * Strategy: ship a small arm64 C binary (patcher_arm64) in APK assets.
 * Extract it once to app's private files dir, chmod 755, then execute
 * via "su -c ./patcher PID HEX_ADDR HEX_BYTES".
 *
 * The binary uses pwrite64(fd, bytes, n, addr64) — the only reliable
 * way to write to /proc/PID/mem at 64-bit addresses. dd fails for
 * large addresses due to internal 32-bit overflow in seek arithmetic.
 */
public class MemPatcher {
    private static final String TAG   = "GAERIS";
    private static final String GAME  = "com.garena.game.codm";
    private static final String BIN   = "patcher_arm64";

    // ARM64 patch constants
    public static final byte[] NOP4  = {0x1F,0x20,0x03,(byte)0xD5};
    public static final byte[] NOP8  = {0x1F,0x20,0x03,(byte)0xD5,
                                        0x1F,0x20,0x03,(byte)0xD5};
    public static final byte[] RET0  = {0x00,0x00,(byte)0x80,0x52,
                                        (byte)0xC0,0x03,0x5F,(byte)0xD6};
    public static final byte[] RET1  = {0x20,0x00,(byte)0x80,0x52,
                                        (byte)0xC0,0x03,0x5F,(byte)0xD6};

    private static File patcherBin   = null;
    private static String cachedPid  = null;

    // ── one-time setup ────────────────────────────────────────────────────────
    public static void init(Context ctx) {
        File f = new File(ctx.getFilesDir(), BIN);
        patcherBin = f;
        if (f.exists() && f.canExecute()) return;
        try {
            InputStream  is  = ctx.getAssets().open(BIN);
            FileOutputStream os = new FileOutputStream(f);
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            is.close(); os.close();
            f.setExecutable(true, false);
            Log.i(TAG, "patcher extracted to " + f.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "init: " + e);
        }
    }

    // ── find game PID ─────────────────────────────────────────────────────────
    public static String getPid() {
        // try pidof first (simpler), fall back to scanning /proc
        String pid = runRoot("pidof " + GAME);
        if (pid != null && !pid.isEmpty()) {
            cachedPid = pid.trim().split("\\s+")[0];
            return cachedPid;
        }
        // scan /proc/*/cmdline
        pid = runRoot(
            "for d in /proc/[0-9]*; do" +
            "  cmd=$(cat \"$d/cmdline\" 2>/dev/null | tr '\\0' ' ');" +
            "  case \"$cmd\" in *" + GAME + "*)" +
            "    echo \"${d##*/}\"; break;; esac; done");
        if (pid != null && !pid.trim().isEmpty()) {
            cachedPid = pid.trim();
            return cachedPid;
        }
        return null;
    }

    // ── get library base from maps ────────────────────────────────────────────
    public static long getLibBase(String pid, String lib) {
        String out = runRoot(
            "grep -m1 '" + lib + "' /proc/" + pid + "/maps | cut -d- -f1");
        if (out == null || out.trim().isEmpty()) return -1;
        try { return Long.parseLong(out.trim(), 16); }
        catch (NumberFormatException e) { return -1; }
    }

    // ── patch bytes at RVA ────────────────────────────────────────────────────
    public static boolean patch(String lib, long rva, byte[] bytes) {
        if (patcherBin == null || !patcherBin.exists()) {
            Log.e(TAG, "patcher binary not initialised — call MemPatcher.init() first");
            return false;
        }
        String pid = getPid();
        if (pid == null) { Log.e(TAG, "game not running"); return false; }

        long base = getLibBase(pid, lib);
        if (base < 0) { Log.e(TAG, "library not found: " + lib); return false; }

        long   addr   = base + rva;
        String hexAddr = Long.toHexString(addr).toUpperCase();
        String hexBytes = bytesToHex(bytes);

        // run: su -c "/data/data/.../files/patcher_arm64 PID ADDR BYTES"
        String cmd = patcherBin.getAbsolutePath()
            + " " + pid
            + " " + hexAddr
            + " " + hexBytes;

        String out = runRoot(cmd);
        boolean ok = out != null && out.startsWith("ok");
        Log.i(TAG, (ok?"✔ ":"✖ ") + lib + "+0x" + Long.toHexString(rva)
            + " @ 0x" + hexAddr + (ok ? "" : " → " + out));
        return ok;
    }

    public static boolean patchAll(Patch[] patches) {
        boolean ok = true;
        for (Patch p : patches) ok &= patch(p.lib, p.rva, p.bytes);
        return ok;
    }

    // ── toggle: patch ON or restore original bytes ────────────────────────────
    public static byte[] readOriginal(String lib, long rva, int len) {
        String pid = getPid();
        if (pid == null) return null;
        long base = getLibBase(pid, lib);
        if (base < 0) return null;
        long addr = base + rva;
        // read N bytes via xxd
        String hex = runRoot(String.format(
            "xxd -l %d -s 0x%X -p /proc/%s/mem 2>/dev/null",
            len, addr, pid));
        if (hex == null || hex.trim().isEmpty()) return null;
        return hexToBytes(hex.trim());
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private static String runRoot(String cmd) {
        try {
            java.lang.Process p = Runtime.getRuntime()
                .exec(new String[]{"su", "-c", cmd});
            BufferedReader out = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(
                new InputStreamReader(p.getErrorStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = out.readLine()) != null) sb.append(line).append("\n");
            String errStr = "";
            while ((line = err.readLine()) != null) errStr += line + "\n";
            p.waitFor();
            if (!errStr.isEmpty()) Log.w(TAG, "stderr: " + errStr.trim());
            return sb.toString().trim();
        } catch (Exception e) { Log.e(TAG, "runRoot: " + e); return null; }
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x & 0xFF));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("\\s+","");
        byte[] b = new byte[hex.length()/2];
        for (int i=0; i<b.length; i++)
            b[i] = (byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        return b;
    }

    // ── patch descriptor ──────────────────────────────────────────────────────
    public static class Patch {
        public final String lib;
        public final long   rva;
        public final byte[] bytes;
        public Patch(String lib, long rva, byte[] bytes) {
            this.lib=lib; this.rva=rva; this.bytes=bytes;
        }
    }
}
