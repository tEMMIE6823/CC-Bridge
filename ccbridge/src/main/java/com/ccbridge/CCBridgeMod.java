package com.ccbridge;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.lua.ILuaAPIFactory;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.IComputerSystem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("ccbridge")
public class CCBridgeMod {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_ID = "ccbridge";

    public CCBridgeMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CCBridgeCommand());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ComputerCraftAPI.registerAPIFactory(new ILuaAPIFactory() {
                @Override
                public ILuaAPI create(IComputerSystem computer) {
                    return new CCBridgeAPI(computer);
                }
            });
            LOGGER.info("CCBridge: registered global Lua API factory");
        });
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CCBridgeAPI.tick();
        }
    }
}
