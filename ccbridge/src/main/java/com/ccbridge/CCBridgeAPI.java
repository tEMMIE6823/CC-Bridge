package com.ccbridge;

import com.mojang.logging.LogUtils;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaFunction;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CCBridgeAPI implements ILuaAPI {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final IComputerSystem computer;
    private final Map<String, ComputerLinkable> linkables = new ConcurrentHashMap<>();


    private static final Map<ServerLevel, List<Runnable>> pendingOps = new ConcurrentHashMap<>();

    private static void enqueue(ServerLevel level, Runnable task) {
        if (level == null) return;
        pendingOps.computeIfAbsent(level, k -> new CopyOnWriteArrayList<>()).add(task);
    }


    private static final Map<ServerLevel, List<PulseTask>> pendingPulseTasks = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, List<AutoOffTask>> pendingAutoOffTasks = new ConcurrentHashMap<>();

    static void tick() {
        for (Map.Entry<ServerLevel, List<Runnable>> entry : pendingOps.entrySet()) {
            ServerLevel level = entry.getKey();
            if (level == null) continue;
            List<Runnable> tasks = entry.getValue();
            if (tasks.isEmpty()) continue;
            List<Runnable> toRun = new ArrayList<>(tasks);
            tasks.clear();
            for (Runnable task : toRun) {
                try { task.run(); } catch (Exception e) {
                    LOGGER.error("CCBridge deferred task failed", e);
                }
            }
        }
        pendingOps.values().removeIf(List::isEmpty);
        for (Map.Entry<ServerLevel, List<PulseTask>> entry : pendingPulseTasks.entrySet()) {
            ServerLevel level = entry.getKey();
            if (level == null) continue;
            List<PulseTask> tasks = entry.getValue();
            tasks.removeIf(task -> {
                task.ticksLeft--;
                if (task.ticksLeft <= 0) {
                    try {
                        task.advance(level);
                    } catch (Exception e) {
                        LOGGER.error("CCBridge pulse tick failed", e);
                    }
                    return task.done;
                }
                return false;
            });
        }
        pendingPulseTasks.values().removeIf(List::isEmpty);
        for (Map.Entry<ServerLevel, List<AutoOffTask>> entry : pendingAutoOffTasks.entrySet()) {
            ServerLevel level = entry.getKey();
            if (level == null) continue;
            List<AutoOffTask> tasks = entry.getValue();
            tasks.removeIf(task -> {
                task.ticksLeft--;
                if (task.ticksLeft <= 0) {
                    try {
                        task.execute(level);
                    } catch (Exception e) {
                        LOGGER.error("CCBridge auto-off tick failed", e);
                    }
                    return true;
                }
                return false;
            });
        }
        pendingAutoOffTasks.values().removeIf(List::isEmpty);
    }


    private static int findMaxStrength(Set<IRedstoneLinkable> network) {
        int max = 0;
        for (IRedstoneLinkable l : network) {
            if (l.isAlive()) {
                max = Math.max(max, l.getTransmittedStrength());
                if (max >= 15) return 15;
            }
        }
        return max;
    }

    private static void updateNet(Set<IRedstoneLinkable> network,
                                         IRedstoneLinkable source, int maxStrength) {
        for (IRedstoneLinkable other : network) {
            if (other == source) continue;
            if (other.isListening()) {
                other.setReceivedStrength(maxStrength);
            }
        }
    }

    private static void signalNet(Level level, IRedstoneLinkable source) {
        Set<IRedstoneLinkable> network =
            Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(level, source);
        int maxStrength = findMaxStrength(network);
        updateNet(network, source, maxStrength);
    }


    public CCBridgeAPI(IComputerSystem computer) {
        this.computer = computer;
    }

    @Override
    public String[] getNames() {
        return new String[] { "ccbridge" };
    }

    @Override
    public String getModuleName() {
        return "CCBridge";
    }

    @LuaFunction({ "GetFrequency", "getf" })
    public final int getFrequency(String freq1, String freq2) {
        if (freq1 == null || freq1.trim().isEmpty()) freq1 = "minecraft:air";
        if (freq2 == null || freq2.trim().isEmpty()) freq2 = "minecraft:air";
        ServerLevel level = computer.getLevel();
        if (level == null || level.isClientSide) return 0;
        try {
            ItemStack stack1 = singleStack(parseItem(freq1));
            ItemStack stack2 = singleStack(parseItem(freq2));
            if (stack1 == ItemStack.EMPTY || stack2 == ItemStack.EMPTY) return 0;
            return querySignal(level, stack1, stack2);
        } catch (Exception e) {
            LOGGER.error("CCBridge GetFrequency failed", e);
            return 0;
        }
    }

    @LuaFunction({ "SendFrequency", "sendf" })
    public final void sendFrequency(String freq1, String freq2, boolean on, double durationSec) {
        sendFreq(freq1, freq2, on, durationSec);
    }

    private void sendFreq(String freq1, String freq2, boolean on, double durationSec) {
        if (freq1 == null || freq1.trim().isEmpty()) freq1 = "minecraft:air";
        if (freq2 == null || freq2.trim().isEmpty()) freq2 = "minecraft:air";
        ServerLevel level = computer.getLevel();
        if (level == null || level.isClientSide) return;
        int strength = on ? 15 : 0;
        try {
            ItemStack stack1 = singleStack(parseItem(freq1));
            ItemStack stack2 = singleStack(parseItem(freq2));
            if (stack1 == ItemStack.EMPTY || stack2 == ItemStack.EMPTY) return;
            String key = freqKey(stack1, stack2);
            BlockPos pos = computer.getPosition() != null
                ? computer.getPosition() : BlockPos.ZERO;
            String finalFreq1 = freq1;
            String finalFreq2 = freq2;

            enqueue(level, () -> {
                try {
                    if (strength == 0) {
                        ComputerLinkable existing = linkables.remove(key);
                        if (existing != null) {
                            existing.setStrength(0, level);
                            signalNet(level, existing);
                            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, existing);
                        }
                        return;
                    }

                    ComputerLinkable linkable = linkables.get(key);
                    if (linkable == null) {
                        linkable = new ComputerLinkable(stack1, stack2, pos, strength);
                        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, linkable);
                        linkables.put(key, linkable);
                    } else {
                        linkable.setStrength(strength, level);
                    }

                    signalNet(level, linkable);

                    if (durationSec > 0) {
                        int offTicks = Math.max(1, (int)(durationSec * 20.0));
                        AutoOffTask task = new AutoOffTask(level, key, linkables);
                        task.ticksLeft = offTicks;
                        scheduleAutoOff(task);
                    }
                } catch (Exception e) {
                    LOGGER.error("CCBridge SendFrequency deferred task failed", e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("CCBridge SendFrequency failed", e);
        }
    }

    @LuaFunction({ "PulseFrequency", "pulsef" })
    public final void pulseFrequency(String freq1, String freq2,
                                      boolean on, int pulseCount, double durationSec) {
        if (freq1 == null || freq1.trim().isEmpty()) freq1 = "minecraft:air";
        if (freq2 == null || freq2.trim().isEmpty()) freq2 = "minecraft:air";
        ServerLevel level = computer.getLevel();
        if (level == null || level.isClientSide) return;
        if (!on) return;
        int strength = 15;
        int pulses = Math.max(1, pulseCount);
        int pulseTicks = Math.max(1, (int)(durationSec * 20.0));
        try {
            ItemStack stack1 = singleStack(parseItem(freq1));
            ItemStack stack2 = singleStack(parseItem(freq2));
            if (stack1 == ItemStack.EMPTY || stack2 == ItemStack.EMPTY) return;
            String key = freqKey(stack1, stack2);
            BlockPos pos = computer.getPosition() != null
                ? computer.getPosition() : BlockPos.ZERO;

            enqueue(level, () -> {
                try {
                    ComputerLinkable linkable = linkables.get(key);
                    if (linkable == null) {
                        linkable = new ComputerLinkable(stack1, stack2, pos, strength);
                        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, linkable);
                        linkables.put(key, linkable);
                    }

                    linkable.setStrength(strength, level);
                    signalNet(level, linkable);

                    PulseTask task = new PulseTask(level, key, linkables,
                        stack1, stack2, strength, pulseTicks, pulseTicks, pulses, pos);
                    schedulePulse(task);
                } catch (Exception e) {
                    LOGGER.error("CCBridge PulseFrequency deferred task failed", e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("CCBridge PulseFrequency failed", e);
        }
    }


    private static void scheduleAutoOff(AutoOffTask task) {
        pendingAutoOffTasks.computeIfAbsent(task.level, k -> new CopyOnWriteArrayList<>()).add(task);
    }

    private static void schedulePulse(PulseTask task) {
        pendingPulseTasks.computeIfAbsent(task.level, k -> new CopyOnWriteArrayList<>()).add(task);
    }

    private static String freqKey(ItemStack a, ItemStack b) {
        return (a.isEmpty() ? "__empty__" : a.getItem().toString())
            + "|" + (b.isEmpty() ? "__empty__" : b.getItem().toString());
    }

    private static ItemStack parseItem(String id) {
        if (id == null || id.trim().isEmpty()) return ItemStack.EMPTY;
        ResourceLocation loc = ResourceLocation.tryParse(id.trim());
        if (loc == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(loc);
        if (item == null) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    private static ItemStack singleStack(ItemStack stack) {
        if (stack == null) return ItemStack.EMPTY;
        if (stack == ItemStack.EMPTY) return ItemStack.EMPTY;
        ItemStack fresh = new ItemStack(stack.getItem());
        fresh.setCount(1);
        return fresh;
    }

    private static int querySignal(Level level, ItemStack stack1, ItemStack stack2) {
        Couple<Frequency> couple = Couple.create(
            Frequency.of(stack1), Frequency.of(stack2));
        var networks = Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(level);
        if (networks == null) return 0;
        var linkables = networks.get(couple);
        if (linkables == null || linkables.isEmpty()) return 0;
        int max = 0;
        for (IRedstoneLinkable l : linkables) {
            max = Math.max(max, l.getTransmittedStrength());
            if (max >= 15) return 15;
        }
        return max;
    }


    private static class AutoOffTask {
        final ServerLevel level;
        final String key;
        final Map<String, ComputerLinkable> links;
        int ticksLeft;

        AutoOffTask(ServerLevel level, String key, Map<String, ComputerLinkable> links) {
            this.level = level;
            this.key = key;
            this.links = links;
        }

        void execute(ServerLevel level) {
            ComputerLinkable existing = links.remove(key);
            if (existing != null) {
                existing.setStrength(0, level);
                signalNet(level, existing);
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, existing);
            }
        }
    }


    private static class PulseTask {
        final ServerLevel level;
        final String key;
        final Map<String, ComputerLinkable> links;
        final int strength;
        final int pulseTicks, gapTicks;
        int pulsesLeft;
        boolean phase;
        boolean done = false;
        int ticksLeft;

        PulseTask(ServerLevel level, String key, Map<String, ComputerLinkable> links,
                  ItemStack stack1, ItemStack stack2, int strength,
                  int pulseTicks, int gapTicks, int pulseCount, BlockPos pos) {
            this.level = level;
            this.key = key;
            this.links = links;
            this.strength = strength;
            this.pulseTicks = pulseTicks;
            this.gapTicks = gapTicks;
            this.pulsesLeft = pulseCount;
            this.phase = true;
            this.ticksLeft = pulseTicks;
        }

        void advance(ServerLevel level) {
            if (phase) {
                ComputerLinkable linkable = links.get(key);
                if (linkable != null) {
                    linkable.setStrength(0, level);
                    signalNet(level, linkable);
                }
                pulsesLeft--;
                if (pulsesLeft <= 0) {
                    ComputerLinkable existing = links.remove(key);
                    if (existing != null) {
                        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, existing);
                    }
                    done = true;
                    return;
                }
                phase = false;
                ticksLeft = gapTicks;
            } else {
                ComputerLinkable linkable = links.get(key);
                if (linkable != null) {
                    linkable.setStrength(strength, level);
                    signalNet(level, linkable);
                }
                phase = true;
                ticksLeft = pulseTicks;
            }
        }
    }
}
