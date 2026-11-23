package com.atoningpenchant.client;

import com.atoningpenchant.AtoningPenchant;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = AtoningPenchant.MOD_ID, dist = Dist.CLIENT)
public class AtoningPenchantClient {
    public AtoningPenchantClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerScreens);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AtoningPenchant.MENU.get(), AtoningPenchantScreen::new);
    }
}
