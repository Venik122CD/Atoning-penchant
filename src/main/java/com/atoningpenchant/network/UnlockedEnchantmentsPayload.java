package com.atoningpenchant.network;

import com.atoningpenchant.AtoningPenchant;
import com.atoningpenchant.menu.AtoningPenchantMenu;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/** Сервер -> клиент: какие чары «открыты» книгами в резных полках рядом со столом. */
public record UnlockedEnchantmentsPayload(Set<Holder<Enchantment>> unlocked) implements CustomPacketPayload {
    public static final Type<UnlockedEnchantmentsPayload> TYPE =
            new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AtoningPenchant.MOD_ID, "unlocked_enchantments"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockedEnchantmentsPayload> CODEC =
            ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT)
                    .<Set<Holder<Enchantment>>>apply(ByteBufCodecs.collection(HashSet::new))
                    .map(UnlockedEnchantmentsPayload::new, UnlockedEnchantmentsPayload::unlocked);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Выполняется на клиенте в основном потоке. */
    public static void handle(UnlockedEnchantmentsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof AtoningPenchantMenu menu) {
            menu.setUnlockedEnchantments(payload.unlocked());
        }
    }
}
