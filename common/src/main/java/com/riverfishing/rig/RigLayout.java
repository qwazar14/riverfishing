package com.riverfishing.rig;

import com.riverfishing.component.RigType;

import static com.riverfishing.rig.SlotRole.BAIT;
import static com.riverfishing.rig.SlotRole.FLOAT;
import static com.riverfishing.rig.SlotRole.GROUNDBAIT;
import static com.riverfishing.rig.SlotRole.HOOK;
import static com.riverfishing.rig.SlotRole.LEADER;
import static com.riverfishing.rig.SlotRole.LURE;

/** Per-rig internal slot layout (Module 4). Slot order = display order, left to right. */
public final class RigLayout {
    private RigLayout() {}

    public static SlotRole[] rolesFor(RigType type) {
        return switch (type) {
            case PRIMITIVE -> new SlotRole[]{HOOK, BAIT};
            case FLOAT_LIGHT -> new SlotRole[]{FLOAT, HOOK, BAIT};
            case FLOAT -> new SlotRole[]{FLOAT, HOOK, HOOK, BAIT, BAIT};
            case WINTER -> new SlotRole[]{BAIT};                       // just a mormyshka (its own hook)
            case FEEDER -> new SlotRole[]{HOOK, BAIT, GROUNDBAIT};
            case FLAT_FEEDER -> new SlotRole[]{HOOK, BAIT, GROUNDBAIT};
            case GROUND -> new SlotRole[]{HOOK, BAIT};
            case CARP -> new SlotRole[]{HOOK, BAIT, GROUNDBAIT};
            case GRUSHA -> new SlotRole[]{HOOK, HOOK, HOOK, BAIT, BAIT, BAIT, GROUNDBAIT};
            case PREDATOR -> new SlotRole[]{LEADER, LURE};
            case CATFISH -> new SlotRole[]{LEADER, HOOK, BAIT};
        };
    }
}
