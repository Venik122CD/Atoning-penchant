package com.atoningpenchant;

import com.atoningpenchant.menu.AtoningPenchantMenu;
import com.atoningpenchant.network.UnlockedEnchantmentsPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(AtoningPenchant.MOD_ID)
public class AtoningPenchant {
    public static final String MOD_ID = "atoning_penchant";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<AtoningPenchantMenu>> MENU =
            MENUS.register("atoning", () -> new MenuType<>(AtoningPenchantMenu::new, FeatureFlags.VANILLA_SET));

    public AtoningPenchant(IEventBus modEventBus) {
        MENUS.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onContainerOpen);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                UnlockedEnchantmentsPayload.TYPE,
                UnlockedEnchantmentsPayload.CODEC,
                UnlockedEnchantmentsPayload::handle
        );
    }

    /** Как только сервер открыл наше меню - отправляем клиенту список открытых чар. */
    private void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getContainer() instanceof AtoningPenchantMenu menu) {
            menu.sendEnchantments();
        }
    }
}
