package com.riverfishing.item;

import com.riverfishing.component.ComponentSlot;

/** Implemented by reel/line/rig/hook items so the assembly menu can validate slots. */
public interface RodComponentItem {
    ComponentSlot componentSlot();
}
