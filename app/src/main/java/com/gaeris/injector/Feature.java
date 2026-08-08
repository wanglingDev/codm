package com.gaeris.injector;

import static com.gaeris.injector.MemPatcher.*;

/**
 * All game features with their dump.cs-verified RVAs.
 * libunity.so = merged IL2CPP for CODM Garena (no separate libil2cpp.so)
 * Dump: 25/07/2026 | com.garena.game.codm
 */
public class Feature {
    public final String  id;
    public final String  name;
    public final String  desc;
    public final Patch[] patches;

    // saved original bytes for toggle-off restore
    public byte[][] origBytes;
    public boolean  active = false;

    private static final String U = "libunity.so";

    public Feature(String id, String name, String desc, Patch... patches) {
        this.id=id; this.name=name; this.desc=desc;
        this.patches=patches;
        this.origBytes=new byte[patches.length][];
    }

    public static final Feature[] ALL = {

        new Feature("no_recoil",
            "No Recoil", "Zero weapon recoil",
            new Patch(U, 0x96239E8L, RET0),   // WeaponIni.GetRecoilUpBase
            new Patch(U, 0x9623AD0L, RET0),   // WeaponIni.GetRecoilLateralBase
            new Patch(U, 0x96236CCL, RET0),   // WeaponIni.GetRecoilUpMax
            new Patch(U, 0x9623A5CL, RET0)    // WeaponIni.GetRecoilUpModifier
        ),

        new Feature("no_spread",
            "No Spread", "Perfect bullet accuracy",
            new Patch(U, 0x9623740L, RET0),   // WeaponIni.GetRecoilLateralMax
            new Patch(U, 0x9623B44L, RET0)    // WeaponIni.GetRecoilLateralModifier
        ),

        new Feature("no_gravity",
            "No Gravity", "Float / no fall damage",
            new Patch(U, 0xBD166A0L, RET0)    // Pawn.GetGravityScale → return 0
        ),

        new Feature("inf_jump",
            "Inf Jump", "No fall state",
            new Patch(U, 0x4FFFEA4L, NOP8)    // Pawn.DoFalling NOP
        ),

        new Feature("speed_hack",
            "Speed Hack", "2× movement speed",
            new Patch(U, 0x4F959D4L, NOP8)    // Pawn.set_MaxMoveSpeed NOP
        ),

        new Feature("esp_visible",
            "Wall Hack", "See enemies through walls",
            // BRPlayerPawn.AimMaxAngleXDown = 180, AimMaxAngleXUp = 180
            // These fields widen the visibility cone to 360 degrees
            new Patch(U, 0x5DC5F80L + 0x2798, new byte[]{
                0x00,0x20,(byte)0xB4,0x43,  // float 360.0 in IEEE 754 LE
                0x00,0x00,(byte)0xB4,0x43   // float 360.0
            })
        ),
    };
}
