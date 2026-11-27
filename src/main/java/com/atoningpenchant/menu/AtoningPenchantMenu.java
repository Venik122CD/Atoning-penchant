package com.atoningpenchant.menu;

import com.atoningpenchant.AtoningPenchant;
import com.atoningpenchant.compat.AtoningCosts;
import com.atoningpenchant.compat.PenchantBridge;
import com.atoningpenchant.network.UnlockedEnchantmentsPayload;
import com.teamabnormals.caverns_and_chasms.common.level.SpinelBoom;
import com.teamabnormals.caverns_and_chasms.common.network.SpinelBoomPayload;
import com.teamabnormals.caverns_and_chasms.core.other.CCCriteriaTriggers;
import com.teamabnormals.caverns_and_chasms.core.other.tags.CCItemTags;
import com.teamabnormals.caverns_and_chasms.core.registry.CCBlocks;
import com.teamabnormals.caverns_and_chasms.core.registry.CCSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Меню Атонинг-стола с механикой стола Penchant.
 *
 * <p>Слоты слева направо: шпинель, предмет, лазурит/книга. Всё, что умеет стол Penchant (список чар,
 * требования по книгам, чары из резных полок, снятие чар на книгу при наличии наждачного камня),
 * работает так же. Отличается оплата: вместо опыта - лазурит, шпинель и прочность предмета, как в C&C, а
 * «кинжал» выбирает один из трёх уровней сложности (см. {@link AtoningCosts}).</p>
 *
 * <p>Кнопка списка передаёт на сервер число {@code idЧары * 3 + индексУровня}, отдельный пакет не нужен.</p>
 */
public class AtoningPenchantMenu extends AbstractContainerMenu {
    public static final int ITEM_SLOT = 0;
    public static final int INGREDIENT_SLOT = 1;
    public static final int SPINEL_SLOT = 2;
    private static final int PLAYER_INV_START = 3;
    private static final int PLAYER_INV_END = PLAYER_INV_START + 36;

    private final Container inputSlots = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            AtoningPenchantMenu.this.slotsChanged(this);
        }
    };
    private final DataSlot bookCount = DataSlot.standalone();
    private final DataSlot hasDisenchanter = DataSlot.standalone();
    private final ContainerLevelAccess access;
    private final Player player;
    private final Registry<Enchantment> enchantments;
    private final RandomSource random = RandomSource.create();

    private Set<Holder<Enchantment>> bookshelfUnlocked = Set.of();
    private Set<Holder<Enchantment>> unlocked = Set.of();
    private Set<Holder<Enchantment>> available = Set.of();
    private List<Holder<Enchantment>> displayed = List.of();
    private Runnable changeListener = () -> {};

    /** Выбранный кинжалом уровень сложности (только клиент): 0 - лёгкий, 1 - нормальный, 2 - сложный. */
    private int selectedTier = 0;

    /** Клиентский конструктор. */
    public AtoningPenchantMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    /** Серверный конструктор. */
    public AtoningPenchantMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(AtoningPenchant.MENU.get(), containerId);
        this.access = access;
        this.player = inventory.player;
        this.enchantments = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

        // слева направо на экране: шпинель, предмет, лазурит
        addSlot(new Slot(inputSlots, ITEM_SLOT, 45, 58) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(inputSlots, INGREDIENT_SLOT, 77, 58) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isLapis(stack) || canDisenchant() && isDisenchantingIngredient(stack);
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return isDisenchantingIngredient(stack) ? 1 : super.getMaxStackSize(stack);
            }
        });
        addSlot(new Slot(inputSlots, SPINEL_SLOT, 13, 58) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isSpinel(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 23 + col * 18, 90 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 23 + col * 18, 148));
        }

        addDataSlot(bookCount);
        addDataSlot(hasDisenchanter);

        // клиент: до прихода пакета известны только чары из in_enchanting_table
        setUnlockedEnchantments(Set.of());
        // сервер: считаем всё по книжным полкам вокруг стола
        access.execute((level, pos) -> {
            bookCount.set(countBooks(level, pos));
            hasDisenchanter.set(player.hasInfiniteMaterials() || PenchantBridge.hasDisenchanter(level, pos) ? 1 : 0);
            bookshelfUnlocked = findUnlocked(level, pos);
            setUnlockedEnchantments(player.hasInfiniteMaterials() ? allEnchantments() : bookshelfUnlocked);
        });
    }

    public static MenuProvider provider(Component name, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (id, inventory, p) -> new AtoningPenchantMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                name
        );
    }

    // ------------------------------------------------------------------ типы предметов

    public static boolean isLapis(ItemStack stack) {
        return stack.is(Tags.Items.ENCHANTING_FUELS);
    }

    public static boolean isSpinel(ItemStack stack) {
        return stack.is(CCItemTags.ATONING_FUELS);
    }

    public static boolean isDisenchantingIngredient(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
    }

    // ------------------------------------------------------------------ доступные чары

    /** Сервер: сразу после открытия меню отправить клиенту открытые чары и выдать достижение Penchant. */
    public void sendEnchantments() {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new UnlockedEnchantmentsPayload(unlocked));
            PenchantBridge.triggerOpenTable(serverPlayer, getBookCount(), bookshelfUnlocked);
        }
    }

    public void setUnlockedEnchantments(Set<Holder<Enchantment>> unlocked) {
        this.unlocked = Set.copyOf(unlocked);
        Set<Holder<Enchantment>> all = new HashSet<>(unlocked);
        enchantments.getTag(EnchantmentTags.IN_ENCHANTING_TABLE).ifPresent(set -> set.forEach(all::add));
        this.available = all;
        refreshDisplayed();
    }

    private Set<Holder<Enchantment>> allEnchantments() {
        Set<Holder<Enchantment>> all = new HashSet<>();
        enchantments.holders().forEach(all::add);
        return all;
    }

    /** Чары из книг, лежащих в резных полках вокруг стола (кроме тех, что и так доступны). */
    private static Set<Holder<Enchantment>> findUnlocked(Level level, BlockPos pos) {
        Set<Holder<Enchantment>> result = new HashSet<>();
        for (BlockPos offset : PenchantBridge.getBookshelfOffsets()) {
            if (!EnchantingTableBlock.isValidBookShelf(level, pos, offset)) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos.offset(offset));
            if (blockEntity instanceof ChiseledBookShelfBlockEntity shelf) {
                for (int i = 0; i < shelf.getContainerSize(); i++) {
                    ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(shelf.getItem(i));
                    for (Holder<Enchantment> holder : stored.keySet()) {
                        if (!holder.is(EnchantmentTags.IN_ENCHANTING_TABLE)) result.add(holder);
                    }
                }
            }
        }
        return result;
    }

    private static int countBooks(Level level, BlockPos pos) {
        int sum = 0;
        for (BlockPos offset : PenchantBridge.getBookshelfOffsets()) {
            if (!EnchantingTableBlock.isValidBookShelf(level, pos, offset)) continue;
            BlockState state = level.getBlockState(pos.offset(offset));
            if (state.is(BlockTags.ENCHANTMENT_POWER_PROVIDER)) sum += PenchantBridge.getBookCount(state);
        }
        return sum;
    }

    // ------------------------------------------------------------------ состояние для экрана

    public List<Holder<Enchantment>> getDisplayedEnchantments() {
        return displayed;
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    public ItemStack getEnchantingStack() {
        return inputSlots.getItem(ITEM_SLOT);
    }

    public ItemStack getIngredientStack() {
        return inputSlots.getItem(INGREDIENT_SLOT);
    }

    public ItemStack getSpinelStack() {
        return inputSlots.getItem(SPINEL_SLOT);
    }

    public int getLapisCount() {
        ItemStack stack = getIngredientStack();
        return isLapis(stack) ? stack.getCount() : 0;
    }

    public int getSpinelCount() {
        ItemStack stack = getSpinelStack();
        return isSpinel(stack) ? stack.getCount() : 0;
    }

    public int getBookCount() {
        return bookCount.get();
    }

    public boolean hasDisenchanter() {
        return hasDisenchanter.get() != 0;
    }

    public boolean canDisenchant() {
        if (!hasDisenchanter()) return false;
        ItemStack stack = getEnchantingStack();
        return stack.isEmpty() || !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty();
    }

    public boolean isEnchanting() {
        ItemStack ingredient = getIngredientStack();
        return isLapis(ingredient) || ingredient.isEmpty();
    }

    public boolean isDisenchanting() {
        return canDisenchant() && isDisenchantingIngredient(getIngredientStack());
    }

    public boolean isAvailable(Holder<Enchantment> enchantment) {
        return available.contains(enchantment);
    }

    public boolean hasEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(enchantment) > 0;
    }

    public int getSelectedTier() {
        return selectedTier;
    }

    /** Клик по кинжалу: лёгкий -> нормальный -> сложный -> лёгкий. */
    public void cycleTier() {
        selectedTier = (selectedTier + 1) % AtoningCosts.TIER_COUNT;
    }

    /** Кнопка списка: чара + выбранный уровень сложности. */
    public int getButtonId(Holder<Enchantment> enchantment, int tierIndex) {
        return enchantments.asHolderIdMap().getId(enchantment) * AtoningCosts.TIER_COUNT + tierIndex;
    }

    /** Требование Penchant по книгам (A). */
    public int getBookRequirement(Holder<Enchantment> enchantment) {
        return PenchantBridge.getDefinition(player.registryAccess(), enchantment).bookRequirement();
    }

    /** Сколько книг нужно на выбранном кинжалом уровне: A, A + 25% или A + 50%. */
    public int getRequiredBooks(Holder<Enchantment> enchantment, int tierIndex) {
        return AtoningCosts.requiredBooks(getBookRequirement(enchantment), AtoningCosts.effectiveTier(enchantment, tierIndex));
    }

    public List<Holder<Enchantment>> getIncompatible(ItemStack stack, Holder<Enchantment> enchantment) {
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream()
                .filter(other -> !enchantment.equals(other) && !Enchantment.areCompatible(enchantment, other))
                .toList();
    }

    // ------------------------------------------------------------------ список для отображения

    private Stream<Holder<Enchantment>> streamOrdered() {
        Stream<Holder<Enchantment>> all = enchantments.holders().map(holder -> (Holder<Enchantment>) holder);
        Optional<HolderSet.Named<Enchantment>> order = enchantments.getTag(EnchantmentTags.TOOLTIP_ORDER);
        if (order.isPresent()) {
            HolderSet.Named<Enchantment> set = order.get();
            return Stream.concat(set.stream(), all.filter(holder -> !set.contains(holder)));
        }
        return all;
    }

    private boolean canApply(ItemStack stack, Holder<Enchantment> enchantment) {
        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        return (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) || stack.supportsEnchantment(enchantment))
                && current.getLevel(enchantment) <= 0
                && EnchantmentHelper.isEnchantmentCompatible(current.keySet(), enchantment);
    }

    private void refreshDisplayed() {
        ItemStack stack = getEnchantingStack();
        boolean creative = player.hasInfiniteMaterials();
        if (stack.isEmpty()) {
            displayed = List.of();
        } else if (isEnchanting()) {
            if (!creative && !stack.isDamageableItem()) {
                // как и в C&C: платить прочностью можно только предметом с прочностью
                displayed = List.of();
            } else {
                displayed = streamOrdered()
                        .filter(enchantment -> !enchantment.is(PenchantBridge.DISABLED)
                                && (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) || stack.supportsEnchantment(enchantment))
                                && (!enchantment.is(EnchantmentTags.CURSE)
                                || available.contains(enchantment)
                                || hasEnchantment(stack, enchantment)))
                        .sorted(Comparator.comparingInt(enchantment ->
                                !available.contains(enchantment) && !hasEnchantment(stack, enchantment) ? 2
                                        : enchantment.is(EnchantmentTags.CURSE) ? 1
                                        : 0))
                        .toList();
            }
        } else if (isDisenchanting()) {
            displayed = streamOrdered().filter(enchantment -> hasEnchantment(stack, enchantment)).toList();
        } else {
            displayed = List.of();
        }
        changeListener.run();
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == inputSlots) refreshDisplayed();
    }

    // ------------------------------------------------------------------ накладывание и снятие чар

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId < 0) return false;
        Holder<Enchantment> enchantment = enchantments.asHolderIdMap().byId(buttonId / AtoningCosts.TIER_COUNT);
        if (enchantment == null) return false;
        int requestedTier = buttonId % AtoningCosts.TIER_COUNT;

        if (isEnchanting()) return handleEnchant(player, enchantment, requestedTier);
        if (isDisenchanting()) return handleDisenchant(player, enchantment);
        return false;
    }

    private boolean handleEnchant(Player player, Holder<Enchantment> enchantment, int requestedTier) {
        ItemStack stack = getEnchantingStack();
        if (stack.isEmpty() || !displayed.contains(enchantment)) return false;

        boolean creative = player.hasInfiniteMaterials();
        int tier = AtoningCosts.effectiveTier(enchantment, requestedTier);
        int fuel = AtoningCosts.fuelCost(enchantment, tier);

        if (!canApply(stack, enchantment)) return false;
        if (!creative) {
            if (!available.contains(enchantment)) return false;
            if (!stack.isDamageableItem()) return false;
            if (getBookCount() < getRequiredBooks(enchantment, tier)) return false;
            if (getLapisCount() < fuel || getSpinelCount() < fuel) return false;
        }

        access.execute((level, pos) -> {
            boolean broken = false;

            if (!creative) {
                // как в C&C: сначала платим прочностью (предмет может сломаться), потом топливом
                stack.hurtAndBreak(AtoningCosts.rollLoss(random, stack, tier), player,
                        LivingEntity.getSlotForHand(player.getUsedItemHand()));
                broken = stack.isEmpty();

                getIngredientStack().shrink(fuel);
                getSpinelStack().shrink(fuel);
                if (getIngredientStack().isEmpty()) inputSlots.setItem(INGREDIENT_SLOT, ItemStack.EMPTY);
                if (getSpinelStack().isEmpty()) inputSlots.setItem(SPINEL_SLOT, ItemStack.EMPTY);
            }

            ItemStack result = ItemStack.EMPTY;
            if (broken) {
                inputSlots.setItem(ITEM_SLOT, ItemStack.EMPTY);
            } else {
                AtoningCosts.Outcome outcome = AtoningCosts.rollOutcome(random, level.registryAccess(), enchantment, tier, stack.getMaxDamage());
                result = updateEnchantments(stack, mutable -> mutable.set(enchantment, outcome.level()));
                inputSlots.setItem(ITEM_SLOT, result);
                PenchantBridge.setProgress(level.registryAccess(), result, enchantment, outcome.progress());
            }

            player.awardStat(Stats.ENCHANT_ITEM);
            if (player instanceof ServerPlayer serverPlayer) {
                // сломанный предмет = пустой стек: это условие достижения "Atone-Deaf"
                CCCriteriaTriggers.ATONED_ITEM.get().trigger(serverPlayer, result, tier + 1);
            }

            level.playSound(null, pos, CCSoundEvents.ATONING_TABLE_USE.get(), SoundSource.BLOCKS,
                    1.0F, level.random.nextFloat() * 0.1F + 0.9F);
            inputSlots.setChanged();
        });
        return true;
    }

    /**
     * Снятие чары на книгу. Теперь это дорого: 6 шпинели, предмет теряет 25-50% прочности, а сам Атонинг-стол
     * взрывается (как Bejeweled Anvil в C&C). Игроку остаются зачарованная книга и потрёпанный предмет.
     */
    private boolean handleDisenchant(Player player, Holder<Enchantment> enchantment) {
        ItemStack stack = getEnchantingStack();
        ItemStack ingredient = getIngredientStack();
        if (stack.isEmpty() || !displayed.contains(enchantment)) return false;

        boolean creative = player.hasInfiniteMaterials();
        int spinelCost = AtoningCosts.EXTRACT_SPINEL_COST;

        if (!creative
                && (!hasEnchantment(stack, enchantment)
                || getBookCount() < getBookRequirement(enchantment)
                || getSpinelCount() < spinelCost
                || !EnchantmentHelper.isEnchantmentCompatible(
                EnchantmentHelper.getEnchantmentsForCrafting(ingredient).keySet(), enchantment))) {
            return false;
        }

        access.execute((level, pos) -> {
            int enchantmentLevel = EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(enchantment);

            ItemStack newStack = updateEnchantments(stack, mutable -> mutable.set(enchantment, 0));
            if (!creative) {
                getSpinelStack().shrink(spinelCost);
                if (getSpinelStack().isEmpty()) inputSlots.setItem(SPINEL_SLOT, ItemStack.EMPTY);

                if (newStack.isDamageableItem()) {
                    newStack.setDamageValue(newStack.getDamageValue() + AtoningCosts.rollExtractLoss(random, newStack));
                }
            }
            inputSlots.setItem(ITEM_SLOT, newStack);
            inputSlots.setItem(INGREDIENT_SLOT, updateEnchantments(ingredient, mutable -> mutable.set(enchantment, enchantmentLevel)));

            if (player instanceof ServerPlayer serverPlayer) {
                PenchantBridge.triggerExtractEnchantment(serverPlayer, newStack, enchantment);
            }

            explodeTable(level, pos);

            // стола больше нет: закрываем окно, предметы из слотов вернутся игроку (removed -> clearContainer)
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.closeContainer();
        });
        return true;
    }

    /** Взрыв стола - один в один как у Bejeweled Anvil в C&C (SpinelBoom, те же частицы и звук). */
    private static void explodeTable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(CCBlocks.ATONING_TABLE.get())) {
            level.removeBlock(pos, false);
            level.levelEvent(1029, pos, 0);

            if (level instanceof ServerLevel serverLevel) {
                SpinelBoom boom = new SpinelBoom(level, null, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 2.0F);
                if (!EventHooks.onExplosionStart(level, boom)) {
                    boom.explode();
                    boom.finalizeExplosion(true);
                    PacketDistributor.sendToPlayersInDimension(serverLevel, new SpinelBoomPayload(
                            pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F, 2.0F, boom.getToBlow()));
                }
            }
        } else {
            level.levelEvent(1030, pos, 0);
        }
    }

    /** Меняет чары предмета или книги (пустая книга <-> зачарованная), как это делает стол Penchant. */
    private static ItemStack updateEnchantments(ItemStack stack, Consumer<ItemEnchantments.Mutable> updater) {
        DataComponentType<ItemEnchantments> type = stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK)
                ? DataComponents.STORED_ENCHANTMENTS
                : DataComponents.ENCHANTMENTS;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(type, ItemEnchantments.EMPTY));
        updater.accept(mutable);
        ItemEnchantments updated = mutable.toImmutable();
        stack.set(type, updated);
        if (updated.isEmpty()) {
            if (stack.is(Items.ENCHANTED_BOOK)) return stack.transmuteCopy(Items.BOOK);
        } else if (stack.is(Items.BOOK)) {
            return stack.transmuteCopy(Items.ENCHANTED_BOOK);
        }
        return stack;
    }

    // ------------------------------------------------------------------ обычная механика меню

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index < PLAYER_INV_START) {
            if (!moveItemStackTo(original, PLAYER_INV_START, PLAYER_INV_END, true)) return ItemStack.EMPTY;
        } else if (isSpinel(original)) {
            if (!moveItemStackTo(original, SPINEL_SLOT, SPINEL_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (isLapis(original) || !getEnchantingStack().isEmpty() && canDisenchant() && isDisenchantingIngredient(original)) {
            if (!moveItemStackTo(original, INGREDIENT_SLOT, INGREDIENT_SLOT + 1, false)) return ItemStack.EMPTY;
        } else {
            Slot itemSlot = slots.get(ITEM_SLOT);
            if (itemSlot.hasItem() || !itemSlot.mayPlace(original)) return ItemStack.EMPTY;
            itemSlot.setByPlayer(original.copyWithCount(1));
            original.shrink(1);
        }

        if (original.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (original.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, original);
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, inputSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, CCBlocks.ATONING_TABLE.get());
    }
}
