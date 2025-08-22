package ru.liko.warbornradios;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Warbornradios.MODID)
public class Warbornradios {
    public static final String MODID = "warbornradios";

    public Warbornradios() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        // Register command registration listener
        MinecraftForge.EVENT_BUS.addListener(WRBCommands::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // nothing yet
    }
}
