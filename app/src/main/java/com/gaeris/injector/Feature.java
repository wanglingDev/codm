package com.gaeris.injector;

import android.widget.Switch;
import android.widget.TextView;
import static com.gaeris.injector.MemPatcher.*;

public class Feature {
    public final String  id;
    public final String  name;
    public final String  desc;
    public final Patch[] patches;

    // state
    public byte[][]  origBytes;
    public boolean   active     = false;
    public boolean   autoActive = false; // ikut auto-inject saat game launch

    // UI refs — diisi oleh FloatService.buildUI()
    public Switch   uiSwitch = null;
    public TextView uiDot    = null;

    private static final String U = "libunity.so";

    public Feature(String id, String name, String desc, Patch... patches) {
        this.id = id; this.name = name; this.desc = desc;
        this.patches   = patches;
        this.origBytes = new byte[patches.length][];
    }

    // ── Feature list ─────────────────────────────────────────────────────────
    // RVA verified dari dump.cs 25/07/2026 | com.garena.game.codm | libunity.so
    // ─────────────────────────────────────────────────────────────────────────
    public static final Feature[] ALL = {

        new Feature("no_recoil",
            "No Recoil", "Zero weapon recoil",
            new Patch(U, 0x96239E8L, RET0),  // WeaponIni.GetRecoilUpBase
            new Patch(U, 0x9623AD0L, RET0),  // WeaponIni.GetRecoilLateralBase
            new Patch(U, 0x96236CCL, RET0),  // WeaponIni.GetRecoilUpMax
            new Patch(U, 0x9623A5CL, RET0)   // WeaponIni.GetRecoilUpModifier
        ),

        new Feature("no_spread",
            "No Spread", "Perfect bullet accuracy",
            new Patch(U, 0x9623740L, RET0),  // WeaponIni.GetRecoilLateralMax
            new Patch(U, 0x9623B44L, RET0)   // WeaponIni.GetRecoilLateralModifier
        ),

        new Feature("no_gravity",
            "No Gravity", "Float / no fall damage",
            new Patch(U, 0xBD166A0L, RET0)   // Pawn.GetGravityScale → 0
        ),

        new Feature("inf_jump",
            "Inf Jump", "No fall state",
            new Patch(U, 0x4FFFEA4L, NOP8)   // Pawn.DoFalling NOP
        ),

        new Feature("speed_hack",
            "Speed Hack", "2× movement speed",
            new Patch(U, 0x4F959D4L, NOP8)   // Pawn.set_MaxMoveSpeed NOP
        ),

        new Feature("wall_hack",
            "Wall Hack", "See enemies through walls",
            new Patch(U, 0x5DC5F80L + 0x2798, new byte[]{
                0x00,0x20,(byte)0xB4,0x43,
                0x00,0x00,(byte)0xB4,0x43
            })
        ),

        // AnoSDK bypass — block libanogs.so reporting
        // Patch ini block AnoSDKIoctl case dispatch supaya report ga terkirim
        // RVA dari libanogs.so versi Juli 2026
        new Feature("anogs_bypass",
            "AnoSDK Bypass", "Block anti-cheat reporting",
            new Patch("libanogs.so", 0x1C70F0L, RET0),  // AnoSDKIoctl → return 0
            new Patch("libanogs.so", 0x1C67E0L, RET0),  // AnoSDKIoctlOld → return 0
            new Patch("libanogs.so", 0x1C6494L, RET0)   // AnoSDKOnRecvData → return 0
        ),
    };
}
