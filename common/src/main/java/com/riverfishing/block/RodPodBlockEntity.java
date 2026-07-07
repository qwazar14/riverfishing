package com.riverfishing.block;

import com.riverfishing.component.RigType;
import com.riverfishing.component.RodClass;
import com.riverfishing.fishing.FishingManager;
import com.riverfishing.fishing.FishingSession;
import com.riverfishing.item.AlarmItem;
import com.riverfishing.item.AlarmType;
import com.riverfishing.item.RodItem;
import com.riverfishing.registry.ModBlockEntities;
import com.riverfishing.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Stores docked rods, their in-water lines, and per-slot bite alarms for a {@link RodPodBlock}. */
public class RodPodBlockEntity extends BlockEntity {
    private final int slotCount;
    private NonNullList<ItemStack> rods;
    private PodLine[] lines;
    private AlarmType[] alarms;

    public RodPodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROD_POD.get(), pos, state);
        this.slotCount = state.getBlock() instanceof RodPodBlock p ? p.slotCount() : 1;
        this.rods = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        this.lines = new PodLine[slotCount];
        this.alarms = new AlarmType[slotCount];
        Arrays.fill(this.alarms, AlarmType.NONE);
    }

    private AlarmType alarmAt(int slot) {
        AlarmType a = alarms[slot];
        return a == null ? AlarmType.NONE : a;
    }

    public void serverTick(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getGameTime();
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            PodLine line = lines[i];
            if (line == null || !line.active) continue;
            AlarmType alarm = alarmAt(i);

            if (!line.bitten) {
                // §catch-the-moment: in the last ~10 s the fish nibbles the bait — subtle stirs that
                // ramp up toward the real take (no state change, purely a cue).
                long toBite = line.biteAtTick - now;
                if (toBite > 0 && toBite < 200
                        && serverLevel.getRandom().nextDouble() < 0.02 * (1.0 - toBite / 200.0)) {
                    nibble(serverLevel, alarm, line.target);
                }
                if (now >= line.biteAtTick) {
                    line.bitten = true;
                    line.phantom = false;
                    // §pod-self-hook: 40% of real bites hook themselves against the rod's weight —
                    // the fish stays on for up to 30 s; otherwise a wide ~8–15 s reaction window
                    // (§bite-window: a long cast should be forgiving, alarm or not).
                    line.selfHooked = serverLevel.getRandom().nextDouble() < 0.40;
                    line.windowEnd = now + (line.selfHooked ? 600 : 160 + serverLevel.getRandom().nextInt(140));
                    fireAlarm(serverLevel, alarm, true, line.target);
                    changed = true;
                } else if (alarm != AlarmType.NONE
                        && serverLevel.getRandom().nextDouble() < alarm.phantomPerTick() * com.riverfishing.config.RiverFishingConfig.phantomMultiplier()) {
                    line.bitten = true;
                    line.phantom = true;
                    line.selfHooked = false;
                    line.windowEnd = now + 40 + serverLevel.getRandom().nextInt(30); // shorter false alarm
                    fireAlarm(serverLevel, alarm, false, line.target);
                    changed = true;
                }
            } else if (now > line.windowEnd) {
                if (line.phantom) {
                    line.bitten = false; // false alarm ignored: signal resets, line keeps waiting
                    line.phantom = false;
                } else {
                    line.active = false; // missed a real bite: bait gone, line goes slack
                }
                changed = true;
            } else if (alarm != AlarmType.NONE && !line.phantom) {
                // §alarm-particles: the alarm keeps flashing/ringing for the WHOLE bite window,
                // so a self-hooked fish is impossible to miss from across the camp.
                alarmPulse(serverLevel, alarm, now);
            }
        }
        if (changed) sync(); // push to clients: the renderer draws taut / biting / slack lines
    }

    /**
     * Visual line state for the renderer (§pod-line): 0 = no line, 1 = waiting (TAUT), 2 = bite
     * window open (taut + twitching), 3 = missed real bite (SLACK sag — reel in and re-cast).
     */
    public int lineStateAt(int slot) {
        if (slot < 0 || slot >= lines.length || lines[slot] == null || rods.get(slot).isEmpty()) return 0;
        PodLine line = lines[slot];
        if (!line.active) return 3;
        if (line.bitten && !line.phantom) return 2;
        return 1;
    }

    public InteractionResult onUse(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        ItemStack held = sp.getItemInHand(hand);

        if (held.getItem() instanceof AlarmItem alarmItem) {
            // Alarms mount on the pod itself (rod optional): occupied slots first, then any free one.
            int slot = firstOccupiedWithoutAlarm();
            if (slot < 0) slot = firstSlotWithoutAlarm();
            if (slot < 0) {
                actionbar(sp, "message.riverfishing.alarm_needs_rod", ChatFormatting.YELLOW);
                return InteractionResult.CONSUME;
            }
            alarms[slot] = alarmItem.alarmType();
            held.shrink(1);
            sync();
            actionbar(sp, "message.riverfishing.alarm_attached", ChatFormatting.GREEN);
            return InteractionResult.CONSUME;
        }

        if (held.getItem() instanceof RodItem rod) {
            if (rod.rodType().rodClass() != RodClass.BOTTOM) {
                actionbar(sp, "message.riverfishing.pod_wrong_rod", ChatFormatting.YELLOW);
                return InteractionResult.CONSUME;
            }
            int slot = firstEmpty();
            if (slot < 0) {
                actionbar(sp, "message.riverfishing.pod_full", ChatFormatting.YELLOW);
                return InteractionResult.CONSUME;
            }
            FishingSession session = FishingManager.detachBottomSession(sp);
            if (session == null) {
                actionbar(sp, "message.riverfishing.pod_cast_first", ChatFormatting.YELLOW);
                return InteractionResult.CONSUME;
            }
            rods.set(slot, held.copy());
            lines[slot] = PodLine.fromSession(session);
            sp.setItemInHand(hand, ItemStack.EMPTY);
            sync();
            actionbar(sp, "message.riverfishing.pod_docked", ChatFormatting.GREEN);
            return InteractionResult.CONSUME;
        }

        if (held.isEmpty()) {
            int slot = undockSlot(now);
            if (slot < 0) return InteractionResult.PASS;
            ItemStack rod = rods.get(slot);
            PodLine line = lines[slot];
            rods.set(slot, ItemStack.EMPTY);
            lines[slot] = null;
            sync();
            giveRodToMainHand(sp, rod);
            if (line != null && line.bitten && line.active && now <= line.windowEnd && !line.phantom) {
                if (line.selfHooked) {
                    actionbar(sp, "message.riverfishing.pod_self_hooked", ChatFormatting.AQUA);
                }
                FishingManager.startPodFight(sp, line.target, line.species,
                        line.lineStrainKg, line.dragKg, line.hasLeader, line.rigType);
            } else if (line != null && line.phantom) {
                actionbar(sp, "message.riverfishing.pod_phantom", ChatFormatting.GRAY);
            } else {
                actionbar(sp, "message.riverfishing.pod_taken", ChatFormatting.GRAY);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private int firstEmpty() {
        for (int i = 0; i < rods.size(); i++) {
            if (rods.get(i).isEmpty()) return i;
        }
        return -1;
    }

    private int firstOccupiedWithoutAlarm() {
        for (int i = 0; i < rods.size(); i++) {
            if (!rods.get(i).isEmpty() && alarmAt(i) == AlarmType.NONE) return i;
        }
        return -1;
    }

    private int firstSlotWithoutAlarm() {
        for (int i = 0; i < rods.size(); i++) {
            if (alarmAt(i) == AlarmType.NONE) return i;
        }
        return -1;
    }

    /** Alarm mounted on a slot, for the renderer (synced via the update tag). */
    public AlarmType alarmTypeAt(int slot) {
        return slot >= 0 && slot < alarms.length ? alarmAt(slot) : AlarmType.NONE;
    }

    /** Prefer a biting rod (so grabbing reacts to the alarm); otherwise the first occupied slot. */
    private int undockSlot(long now) {
        int firstOccupied = -1;
        for (int i = 0; i < rods.size(); i++) {
            if (rods.get(i).isEmpty()) continue;
            if (firstOccupied < 0) firstOccupied = i;
            PodLine line = lines[i];
            if (line != null && line.bitten && line.active && now <= line.windowEnd) {
                return i;
            }
        }
        return firstOccupied;
    }

    private void giveRodToMainHand(ServerPlayer sp, ItemStack rod) {
        ItemStack current = sp.getMainHandItem();
        if (!current.isEmpty()) {
            if (!sp.getInventory().add(current)) sp.drop(current, false);
        }
        sp.setItemInHand(InteractionHand.MAIN_HAND, rod);
    }

    private void fireAlarm(ServerLevel level, AlarmType type, boolean real, BlockPos target) {
        BlockPos pos = getBlockPos();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.7, cz = pos.getZ() + 0.5;
        if (type == AlarmType.BELL) {
            level.playSound(null, pos, com.riverfishing.registry.ModSounds.ALARM_BELL.get(), SoundSource.BLOCKS, (float) type.soundVolume(), 1.0f);
            level.sendParticles(ParticleTypes.NOTE, cx, cy + 0.3, cz, 5, 0.25, 0.2, 0.25, 0.0);
        } else if (type == AlarmType.DIGITAL) {
            // §catch-the-moment: NO text — just the beep + particles. Reading them is the game.
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, (float) type.soundVolume(), 2.0f);
            level.sendParticles(DustParticleOptions.REDSTONE, cx, cy + 0.3, cz, 6, 0.2, 0.2, 0.2, 0.0);
        }
        // §silent-bite: without an alarm the bite makes NO sound — only water movement at the rig.
        if (target != null) {
            level.sendParticles(ParticleTypes.SPLASH,
                    target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, 10, 0.25, 0.1, 0.25, 0.15);
        }
    }

    /**
     * Repeating light + sound while a real bite window is open (§catch-the-moment): the bell TREMBLES
     * — showering note symbols and ringing; the digital alarm flashes redstone and BEEPS fast so you
     * can't miss the take from across the camp.
     */
    private void alarmPulse(ServerLevel level, AlarmType type, long now) {
        BlockPos pos = getBlockPos();
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.7, cz = pos.getZ() + 0.5;
        if (type == AlarmType.BELL) {
            if (now % 5 == 0) {
                level.sendParticles(ParticleTypes.NOTE, cx, cy + 0.35, cz, 2, 0.2, 0.15, 0.2, 0.0);
            }
            if (now % 20 == 0) {
                level.playSound(null, pos, com.riverfishing.registry.ModSounds.ALARM_BELL.get(),
                        SoundSource.BLOCKS, (float) type.soundVolume(), 1.0f);
            }
        } else { // DIGITAL — fast, insistent beep
            if (now % 4 == 0) {
                level.sendParticles(DustParticleOptions.REDSTONE, cx, cy + 0.35, cz, 2, 0.12, 0.12, 0.12, 0.0);
            }
            if (now % 8 == 0) {
                level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.BLOCKS, (float) type.soundVolume(), 2.0f);
            }
        }
    }

    /**
     * §catch-the-moment: before the real take, the fish NIBBLES the bait — faint water ticks at the
     * rig and a barely-there stir of the alarm, ramping up as the bite nears. No text; spotting the
     * shift from a nibble to a real bite is the whole thrill.
     */
    private void nibble(ServerLevel level, AlarmType type, BlockPos target) {
        BlockPos pos = getBlockPos();
        if (target != null) {
            level.sendParticles(ParticleTypes.FISHING,
                    target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, 2, 0.1, 0.0, 0.1, 0.0);
        }
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.7, cz = pos.getZ() + 0.5;
        if (type == AlarmType.BELL) {
            level.sendParticles(ParticleTypes.NOTE, cx, cy + 0.3, cz, 1, 0.05, 0.05, 0.05, 0.0);
            level.playSound(null, pos, com.riverfishing.registry.ModSounds.ALARM_BELL.get(),
                    SoundSource.BLOCKS, 0.25f, 1.5f);
        } else if (type == AlarmType.DIGITAL) {
            level.sendParticles(DustParticleOptions.REDSTONE, cx, cy + 0.3, cz, 1, 0.05, 0.05, 0.05, 0.0);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS, 0.2f, 2.3f);
        }
    }

    private void actionbar(ServerPlayer sp, String key, ChatFormatting color) {
        sp.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    // ---- drops on break ----

    public NonNullList<ItemStack> getRodsForDrop() {
        return rods;
    }

    public List<ItemStack> getAlarmsForDrop() {
        List<ItemStack> drops = new ArrayList<>();
        for (AlarmType a : alarms) {
            if (a != null && a != AlarmType.NONE) {
                ItemStack item = new ItemStack(ModItems.alarmItem(a));
                if (!item.isEmpty()) drops.add(item);
            }
        }
        return drops;
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, rods);
        ListTag list = new ListTag();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null) {
                CompoundTag c = lines[i].toNbt();
                c.putInt("Slot", i);
                list.add(c);
            }
        }
        tag.put("Lines", list);
        int[] alarmOrds = new int[slotCount];
        for (int i = 0; i < slotCount; i++) {
            alarmOrds[i] = alarmAt(i).ordinal();
        }
        tag.putIntArray("Alarms", alarmOrds);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.rods = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, rods);
        this.lines = new PodLine[slotCount];
        ListTag list = tag.getList("Lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int slot = c.getInt("Slot");
            if (slot >= 0 && slot < slotCount) {
                lines[slot] = PodLine.fromNbt(c);
            }
        }
        this.alarms = new AlarmType[slotCount];
        Arrays.fill(this.alarms, AlarmType.NONE);
        int[] alarmOrds = tag.getIntArray("Alarms");
        AlarmType[] all = AlarmType.values();
        for (int i = 0; i < slotCount && i < alarmOrds.length; i++) {
            int ord = alarmOrds[i];
            if (ord >= 0 && ord < all.length) alarms[i] = all[ord];
        }
    }

    /** Mark dirty and push a block update so the client renderer sees docked rods change. */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
                            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            load(pkt.getTag());
        }
    }

    /** A cast line resting on the pod. */
    private static final class PodLine {
        BlockPos target;
        ResourceLocation species;
        long biteAtTick;
        boolean bitten;
        boolean phantom;
        boolean selfHooked; // §pod-self-hook: the fish set the hook itself — a long 30 s grab window
        long windowEnd;
        double lineStrainKg;
        double dragKg;
        boolean hasLeader;
        RigType rigType;
        boolean active = true;

        static PodLine fromSession(FishingSession s) {
            PodLine line = new PodLine();
            line.target = s.target;
            line.species = s.species;
            line.biteAtTick = s.biteAtTick;
            line.bitten = s.bitten;
            line.windowEnd = s.biteWindowEnd;
            line.lineStrainKg = s.lineStrainKg;
            line.dragKg = s.dragKg;
            line.hasLeader = s.hasLeader;
            line.rigType = s.rigType;
            return line;
        }

        CompoundTag toNbt() {
            CompoundTag c = new CompoundTag();
            c.putLong("Target", target.asLong());
            c.putString("Species", species.toString());
            c.putLong("BiteAt", biteAtTick);
            c.putBoolean("Bitten", bitten);
            c.putBoolean("Phantom", phantom);
            c.putBoolean("SelfHooked", selfHooked);
            c.putLong("WindowEnd", windowEnd);
            c.putDouble("Strain", lineStrainKg);
            c.putDouble("Drag", dragKg);
            c.putBoolean("Leader", hasLeader);
            if (rigType != null) c.putString("Rig", rigType.name());
            c.putBoolean("Active", active);
            return c;
        }

        static PodLine fromNbt(CompoundTag c) {
            PodLine line = new PodLine();
            line.target = BlockPos.of(c.getLong("Target"));
            line.species = ResourceLocation.tryParse(c.getString("Species"));
            line.biteAtTick = c.getLong("BiteAt");
            line.bitten = c.getBoolean("Bitten");
            line.phantom = c.getBoolean("Phantom");
            line.selfHooked = c.getBoolean("SelfHooked");
            line.windowEnd = c.getLong("WindowEnd");
            line.lineStrainKg = c.getDouble("Strain");
            line.dragKg = c.getDouble("Drag");
            line.hasLeader = c.getBoolean("Leader");
            if (c.contains("Rig")) {
                try {
                    line.rigType = RigType.valueOf(c.getString("Rig"));
                } catch (IllegalArgumentException ignored) {
                }
            }
            line.active = !c.contains("Active") || c.getBoolean("Active");
            return line;
        }
    }
}
