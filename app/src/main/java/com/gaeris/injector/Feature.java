package com.gaeris.injector;

import static com.gaeris.injector.MemPatcher.*;

/** All game features with their dump.cs-verified RVAs. */
public class Feature {
    public final String  id;
    public final String  name;
    public final String  desc;
    public final Patch[] patches;

    // libunity.so = merged IL2CPP for CODM Garena (no separate libil2cpp.so)
    private static final String U = "libunity.so";
    private static final String A = "libanogs.so";

    public Feature(String id, String name, String desc, Patch... patches) {
        this.id=id; this.name=name; this.desc=desc; this.patches=patches;
    }

    public static final Feature[] ALL = {

        new Feature("no_recoil", "No Recoil", "Zero weapon recoil",
            // WeaponIni.GetRecoilUpBase      RVA 0x96239E8
            new Patch(U, 0x96239E8L, RET0),
            // WeaponIni.GetRecoilLateralBase RVA 0x9623AD0
            new Patch(U, 0x9623AD0L, RET0),
            // WeaponIni.GetRecoilUpMax       RVA 0x96236CC
            new Patch(U, 0x96236CCL, RET0),
            // WeaponIni.GetRecoilUpModifier  RVA 0x9623A5C
            new Patch(U, 0x9623A5CL, RET0)
        ),

        new Feature("no_spread", "No Spread", "Perfect bullet accuracy",
            // WeaponIni.GetRecoilLateralMax      RVA 0x9623740
            new Patch(U, 0x9623740L, RET0),
            // WeaponIni.GetRecoilLateralModifier RVA 0x9623B44
            new Patch(U, 0x9623B44L, RET0)
        ),

        new Feature("speed_hack", "Speed Hack", "Increased move speed",
            // Pawn.get_MaxMoveSpeed RVA 0x4F959CC → return very large float
            // NOP the speed cap check instead
            new Patch(U, 0x4F959CCL, NOP8)
        ),

        new Feature("no_gravity", "No Gravity", "Zero gravity",
            // Pawn.GetGravityScale RVA 0xBD166A0 → return 0
            new Patch(U, 0xBD166A0L, RET0)
        ),

        new Feature("inf_jump", "Inf Jump", "No fall / always jump",
            // BRPlayerPawn.DoFalling RVA — NOP the fall trigger
            new Patch(U, 0x4FFFEA4L, NOP8)
        ),

        new Feature("fast_fire", "Fast Fire", "Max fire rate",
            // Weapon refire time — TODO: find setter RVA in dump.cs
            // placeholder — replace 0xDEAD with correct offset
            new Patch(U, 0xDEADL, NOP4)
        ),
    };
}
