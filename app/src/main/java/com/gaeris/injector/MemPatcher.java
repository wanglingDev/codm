package com.gaeris.injector;

import android.util.Log;
import java.io.*;

/**
 * Root memory patcher via /proc/PID/mem.
 * Uses "dd" executed through su — no NDK required, works on arm64.
 */
public class MemPatcher {
    private static final String TAG  = "GAERIS";
    private static final String GAME = "com.garena.game.codm";

    // ARM64 patch constants
    public static final byte[] NOP4 = {0x1F, 0x20, 0x03, (byte)0xD5};
    public static final byte[] NOP8 = {0x1F, 0x20, 0x03, (byte)0xD5,
                                       0x1F, 0x20, 0x03, (byte)0xD5};
    public static final byte[] RET0 = {0x00, 0x00, (byte)0x80, 0x52,  // MOV W0, #0
                                       (byte)0xC0, 0x03, 0x5F, (byte)0xD6}; // RET
    public static final byte[] RET1 = {0x20, 0x00, (byte)0x80, 0x52,  // MOV W0, #1
                                       (byte)0xC0, 0x03, 0x5F, (byte)0xD6}; // RET

    private static String cachedPid   = null;
    private static long   cachedBase  = -1;
    private static String cachedLib   = null;

    // ── get game PID ─────────────────────────────────────────────────────────
    public static String getPid() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su","-c","pgrep -f "+GAME});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            return (line != null) ? line.trim() : null;
        } catch (Exception e) { Log.e(TAG, "getPid: " + e); return null; }
    }

    // ── get library base address from /proc/PID/maps ──────────────────────
    public static long getLibBase(String pid, String libName) {
        if (libName.equals(cachedLib) && pid.equals(cachedPid) && cachedBase != -1)
            return cachedBase;
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"su","-c","grep "+libName+" /proc/"+pid+"/maps"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            if (line == null) return -1;
            long base = Long.parseLong(line.split("-")[0], 16);
            cachedPid  = pid;
            cachedLib  = libName;
            cachedBase = base;
            Log.i(TAG, libName + " base: 0x" + Long.toHexString(base));
            return base;
        } catch (Exception e) { Log.e(TAG, "getLibBase: " + e); return -1; }
    }

    // ── write bytes to game memory ────────────────────────────────────────
    public static boolean patch(String libName, long rva, byte[] bytes) {
        String pid = getPid();
        if (pid == null) { Log.e(TAG, "Game not running"); return false; }

        long base = getLibBase(pid, libName);
        if (base < 0) { Log.e(TAG, "Library not found: " + libName); return false; }

        long addr = base + rva;
        Log.i(TAG, "Patching " + libName + "+0x" + Long.toHexString(rva)
            + " @ 0x" + Long.toHexString(addr));

        try {
            // Write patch bytes to a temp file in /data/local/tmp (world-writable)
            File tmp = new File("/data/local/tmp/gaeris_patch.bin");
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(bytes);
            fos.close();

            // Use dd to write at exact byte offset into /proc/PID/mem
            String cmd = String.format(
                "dd if=%s of=/proc/%s/mem bs=1 seek=%d count=%d conv=notrunc 2>/dev/null",
                tmp.getAbsolutePath(), pid, addr, bytes.length);

            Process su = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            int ret = su.waitFor();
            tmp.delete();

            if (ret == 0) Log.i(TAG, "Patch OK");
            else          Log.e(TAG, "dd returned " + ret);
            return ret == 0;

        } catch (Exception e) { Log.e(TAG, "patch: " + e); return false; }
    }

    // ── convenience: multiple patches in one call ─────────────────────────
    public static boolean patchAll(Patch[] patches) {
        boolean ok = true;
        for (Patch p : patches) ok &= patch(p.lib, p.rva, p.bytes);
        return ok;
    }

    public static class Patch {
        public final String lib;
        public final long   rva;
        public final byte[] bytes;
        public Patch(String lib, long rva, byte[] bytes) {
            this.lib=lib; this.rva=rva; this.bytes=bytes;
        }
    }
}
