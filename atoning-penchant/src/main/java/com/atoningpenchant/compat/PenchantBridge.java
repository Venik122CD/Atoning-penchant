package com.atoningpenchant.compat;

import com.atoningpenchant.AtoningPenchant;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * «Мост» к Penchant.
 *
 * <p>Penchant - Fabric-мод (на NeoForge он работает через Sinytra Connector), поэтому мы НЕ компилируемся
 * против его классов. Всё, что нужно от Penchant, берётся из данных (теги, динамический реестр определений)
 * или через рефлексию с безопасными запасными значениями. Так мод переживёт разные версии Penchant.</p>
 */
public final class PenchantBridge {
    public static final String PENCHANT_ID = "penchant";

    /** Чары, отключённые Penchant (например, Прочность и Починка в durability_rework). */
    public static final TagKey<Enchantment> DISABLED =
            TagKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(PENCHANT_ID, "disabled"));

    /** Чары, которые Penchant не прокачивает (всегда 1 уровень). */
    public static final TagKey<Enchantment> NO_LEVELING =
            TagKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(PENCHANT_ID, "no_leveling"));

    /** Блоки-«дизенчантеры» (наждачный камень), которые открывают снятие чар. */
    public static final TagKey<Block> DISENCHANTER =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(PENCHANT_ID, "disenchanter"));

    private static final ResourceLocation PROGRESS_COMPONENT =
            ResourceLocation.fromNamespaceAndPath(PENCHANT_ID, "enchantment_progress");

    private static final ResourceKey<Registry<Object>> DEFINITION_REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(PENCHANT_ID, "definition"));

    /** Цена зачарования по версии Penchant. */
    public record Definition(int bookRequirement, Enchantment.Cost progressCostFactor) {}

    private PenchantBridge() {}

    public static boolean isPenchantLoaded() {
        return ModList.get().isLoaded(PENCHANT_ID);
    }

    // ---------------------------------------------------------------- флаг переработанного стола

    private static boolean flagFailureLogged = false;

    /**
     * Включён ли у Penchant переработанный стол (датапак table_rework). Если игрок его отключил, Атонинг-стол
     * остаётся классическим. Если проверить не удалось - считаем, что включён.
     */
    public static boolean isReworkedTableEnabled() {
        if (!isPenchantLoaded()) return false;
        try {
            Class<?> flagClass = Class.forName("archives.tater.penchant.registry.PenchantFlag");
            Object flag = flagClass.getField("REWORKED_TABLE_MENU").get(null);
            return (boolean) flagClass.getMethod("isEnabled").invoke(flag);
        } catch (Throwable t) {
            if (!flagFailureLogged) {
                flagFailureLogged = true;
                AtoningPenchant.LOGGER.warn("Could not read Penchant's reworked table flag, assuming it is enabled", t);
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- расстановка книжных полок

    private static Method bookshelfOffsetsMethod;
    private static boolean bookshelfOffsetsResolved = false;

    /** Смещения, на которых полки считаются (у Penchant они могут быть «мягче», чем в ваниле). */
    @SuppressWarnings("unchecked")
    public static List<BlockPos> getBookshelfOffsets() {
        if (!bookshelfOffsetsResolved) {
            bookshelfOffsetsResolved = true;
            try {
                bookshelfOffsetsMethod = Class.forName("archives.tater.penchant.util.PenchantmentHelper")
                        .getMethod("getBookshelfOffsets");
            } catch (Throwable t) {
                bookshelfOffsetsMethod = null;
            }
        }
        if (bookshelfOffsetsMethod != null) {
            try {
                return (List<BlockPos>) bookshelfOffsetsMethod.invoke(null);
            } catch (Throwable t) {
                bookshelfOffsetsMethod = null;
            }
        }
        return EnchantingTableBlock.BOOKSHELF_OFFSETS;
    }

    /** Сколько «книг» даёт блок: резная полка - по числу книг, кафедра - 1/0, обычная полка - 3. */
    public static int getBookCount(BlockState state) {
        if (state.hasProperty(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED)) {
            return (int) ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.stream().filter(state::getValue).count();
        }
        if (state.hasProperty(LecternBlock.HAS_BOOK)) {
            return state.getValue(LecternBlock.HAS_BOOK) ? 1 : 0;
        }
        return 3;
    }

    // ---------------------------------------------------------------- определения зачарований

    private static Method bookRequirementMethod;
    private static Method progressCostFactorMethod;

    /**
     * Цена зачарования по Penchant. Сначала пробуем прочитать его определение (динамический реестр
     * penchant:definition), иначе считаем так же, как это делает сам Penchant по умолчанию.
     */
    public static Definition getDefinition(RegistryAccess access, Holder<Enchantment> enchantment) {
        Definition fromPenchant = readPenchantDefinition(access, enchantment);
        if (fromPenchant != null) return fromPenchant;

        Enchantment value = enchantment.value();
        return new Definition(Math.max(2 * value.getMinCost(1) - 5, 0), value.definition().maxCost());
    }

    private static Definition readPenchantDefinition(RegistryAccess access, Holder<Enchantment> enchantment) {
        try {
            Optional<Registry<Object>> registry = access.registry(DEFINITION_REGISTRY);
            if (registry.isEmpty()) return null;
            Optional<ResourceKey<Enchantment>> enchantmentKey = enchantment.unwrapKey();
            if (enchantmentKey.isEmpty()) return null;

            ResourceKey<Object> key = ResourceKey.create(DEFINITION_REGISTRY, enchantmentKey.get().location());
            Object definition = registry.get().get(key);
            if (definition == null) return null;

            if (bookRequirementMethod == null) {
                bookRequirementMethod = definition.getClass().getMethod("bookRequirement");
                progressCostFactorMethod = definition.getClass().getMethod("progressCostFactor");
            }
            return new Definition(
                    (int) bookRequirementMethod.invoke(definition),
                    (Enchantment.Cost) progressCostFactorMethod.invoke(definition)
            );
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- прокачка чар (Penchant)

    /** Сколько прогресса нужно, чтобы чара поднялась с currentLevel на следующий (формула Penchant). */
    public static int getMaxProgress(RegistryAccess access, Holder<Enchantment> enchantment, int currentLevel, int maxDamage) {
        int factor = getDefinition(access, enchantment).progressCostFactor().calculate(currentLevel + 1);
        return Math.max(1, Mth.clamp(maxDamage / 100, 1, 8) * factor);
    }

    /**
     * Записывает прогресс чары в компонент penchant:enchantment_progress. Делаем это через кодек компонента
     * (а не через класс Penchant), чтобы не зависеть от его внутренностей.
     */
    @SuppressWarnings("unchecked")
    public static void setProgress(RegistryAccess access, ItemStack stack, Holder<Enchantment> enchantment, int progress) {
        if (progress <= 0 || stack.isEmpty()) return;
        try {
            DataComponentType<Object> type = (DataComponentType<Object>) (DataComponentType<?>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(PROGRESS_COMPONENT);
            Optional<ResourceKey<Enchantment>> key = enchantment.unwrapKey();
            if (type == null || key.isEmpty()) return;

            Codec<Object> codec = type.codecOrThrow();
            RegistryOps<Tag> ops = access.createSerializationContext(NbtOps.INSTANCE);

            CompoundTag tag = new CompoundTag();
            Object existing = stack.get(type);
            if (existing != null) {
                Tag encoded = codec.encodeStart(ops, existing).result().orElse(null);
                if (encoded instanceof CompoundTag compound) tag = compound.copy();
            }
            tag.putInt(key.get().location().toString(), progress);

            Optional<Object> parsed = codec.parse(ops, tag).result();
            if (parsed.isPresent()) stack.set(type, parsed.get());
        } catch (Throwable t) {
            AtoningPenchant.LOGGER.warn("Could not set Penchant enchantment progress", t);
        }
    }

    // ---------------------------------------------------------------- дизенчантер и достижения

    public static boolean hasDisenchanter(Level level, BlockPos pos) {
        return getBookshelfOffsets().stream()
                .anyMatch(offset -> level.getBlockState(pos.offset(offset)).is(DISENCHANTER));
    }

    /** Достижения Penchant (открыл стол / снял чару). Если не получилось - молча пропускаем. */
    public static void triggerOpenTable(ServerPlayer player, int bookCount, Set<Holder<Enchantment>> unlocked) {
        try {
            Object trigger = Class.forName("archives.tater.penchant.registry.PenchantAdvancements")
                    .getField("OPEN_TABLE").get(null);
            trigger.getClass().getMethod("trigger", ServerPlayer.class, int.class, Set.class)
                    .invoke(trigger, player, bookCount, unlocked);
        } catch (Throwable ignored) {
        }
    }

    public static void triggerExtractEnchantment(ServerPlayer player, ItemStack stack, Holder<Enchantment> enchantment) {
        try {
            Object trigger = Class.forName("archives.tater.penchant.registry.PenchantAdvancements")
                    .getField("EXTRACT_ENCHANTMENT").get(null);
            trigger.getClass().getMethod("trigger", ServerPlayer.class, ItemStack.class, Holder.class)
                    .invoke(trigger, player, stack, enchantment);
        } catch (Throwable ignored) {
        }
    }
}
