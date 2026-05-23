package com.ccbridge;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;


public class ComputerLinkable implements IRedstoneLinkable {

    private final ItemStack f1;
    private final ItemStack f2;
    private final BlockPos location;
    private int strength;

    public ComputerLinkable(ItemStack freq1, ItemStack freq2, BlockPos pos, int strength) {
        this.f1 = freq1.copy();
        this.f2 = freq2.copy();
        this.location = pos;
        this.strength = clamp(strength);
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return Couple.create(
            Frequency.of(f1),
            Frequency.of(f2)
        );
    }

    @Override
    public int getTransmittedStrength() {
        return strength;
    }

    @Override
    public void setReceivedStrength(int strength) {
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public BlockPos getLocation() {
        return location;
    }

    
    public void setStrength(int strength, Level level) {
        this.strength = clamp(strength);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(15, value));
    }
}
