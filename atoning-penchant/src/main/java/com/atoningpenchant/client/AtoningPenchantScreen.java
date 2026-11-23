package com.atoningpenchant.client;

import com.atoningpenchant.compat.AtoningCosts;
import com.atoningpenchant.menu.AtoningPenchantMenu;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Экран Атонинг-стола: окно, список, прокрутка и подсказки как у Penchant, но с книгой Атонинг-стола
 * (из C&C) и кинжалом, которым выбирается уровень сложности.
 *
 * <p>Все координаты в пикселях окна (0,0 - левый верхний угол) собраны константами ниже.</p>
 */
public class AtoningPenchantScreen extends AbstractContainerScreen<AtoningPenchantMenu> {
    private static final ResourceLocation BOOK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("caverns_and_chasms", "textures/entity/atoning_table_book.png");
    private static final ResourceLocation LAPIS_SLOT_ICON = ResourceLocation.withDefaultNamespace("item/empty_slot_lapis_lazuli");
    private static final ResourceLocation BOOK_SLOT_ICON = ResourceLocation.fromNamespaceAndPath("penchant", "item/empty_slot_book");

    private static final Component ITEM_SLOT_TOOLTIP = Component.translatable("container.atoning_penchant.slot.item");
    private static final Component INGREDIENT_SLOT_TOOLTIP = Component.translatable("container.atoning_penchant.slot.ingredient");
    private static final Component INGREDIENT_SLOT_DISENCHANT_TOOLTIP = Component.translatable("container.atoning_penchant.slot.ingredient.disenchant");
    private static final Component SPINEL_SLOT_TOOLTIP = Component.translatable("container.atoning_penchant.slot.spinel");
    private static final int TOOLTIP_WIDTH = 115;

    // ---- список чар
    private static final int ROWS = 3;
    private static final int LIST_X = 99;
    private static final int LIST_Y = 13;
    // ---- полоса прокрутки
    private static final int SCROLL_X = 192;
    private static final int TRACK_HEIGHT = 60;
    private static final int SCROLLER_WIDTH = 6;
    private static final int SCROLLER_HEIGHT = 19;
    // ---- кинжал (размер картинки 16x31)
    private static final int DAGGER_X = 45;
    private static final int DAGGER_Y = 3;
    private static final int DAGGER_WIDTH = 16;
    private static final int DAGGER_HEIGHT = 31;
    // ---- книга: центр красного квадрата на макете и размер
    private static final float BOOK_X = 53.0F;
    private static final float BOOK_Y = 40.0F;
    private static final float BOOK_SCALE = 30.0F;
    // ---- строки «книги / наждачный камень»
    private static final int INFO_X = 8;
    private static final int INFO_Y = 20;

    private final CyclingSlotBackground ingredientSlotBackground = new CyclingSlotBackground(AtoningPenchantMenu.INGREDIENT_SLOT);
    private final RandomSource random = RandomSource.create();
    private BookModel bookModel;

    private int scroll = 0;
    private boolean draggingScroller = false;
    private int daggerAge = 0;
    private boolean daggerHovered = false;

    // состояние книги (логика анимации - как в столе Penchant)
    private float flip;
    private float oFlip;
    private float flipT;
    private float flipA;
    private float open;
    private float oOpen;
    private ItemStack last = ItemStack.EMPTY;

    public AtoningPenchantScreen(AtoningPenchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 206;
        this.imageHeight = 172;
        this.inventoryLabelX = 23;
        this.inventoryLabelY = imageHeight - 94;
        menu.setChangeListener(this::rebuildWidgets);
    }

    private int maxScroll() {
        return Math.max(0, menu.getDisplayedEnchantments().size() - ROWS);
    }

    private boolean isBookOpen() {
        return !menu.getDisplayedEnchantments().isEmpty();
    }

    /** Кинжал работает только когда книга открыта и мы именно зачаровываем (а не снимаем чары). */
    private boolean isDaggerActive() {
        return isBookOpen() && menu.isEnchanting();
    }

    @Override
    protected void init() {
        super.init();
        bookModel = new BookModel(requireNonNull(minecraft).getEntityModels().bakeLayer(ModelLayers.BOOK));
        scroll = Mth.clamp(scroll, 0, maxScroll());

        List<Holder<Enchantment>> displayed = menu.getDisplayedEnchantments();
        for (int i = 0; i < ROWS; i++) {
            int index = scroll + i;
            if (index >= displayed.size()) break;
            addRenderableWidget(new EnchantmentRow(
                    leftPos + LIST_X,
                    topPos + LIST_Y + i * EnchantmentRow.HEIGHT,
                    menu,
                    displayed.get(index)
            ));
        }
    }

    // ------------------------------------------------------------------ прокрутка

    private void setScroll(int value) {
        int clamped = Mth.clamp(value, 0, maxScroll());
        if (clamped != scroll) {
            scroll = clamped;
            rebuildWidgets();
        }
    }

    private boolean overTrack(double mouseX, double mouseY) {
        double x = leftPos + SCROLL_X;
        double y = topPos + LIST_Y;
        return mouseX >= x && mouseX < x + SCROLLER_WIDTH && mouseY >= y && mouseY < y + TRACK_HEIGHT;
    }

    private boolean overList(double mouseX, double mouseY) {
        double x = leftPos + LIST_X;
        double y = topPos + LIST_Y;
        return mouseX >= x && mouseX < x + EnchantmentRow.WIDTH && mouseY >= y && mouseY < y + ROWS * EnchantmentRow.HEIGHT;
    }

    private void scrollToMouse(double mouseY) {
        int max = maxScroll();
        if (max <= 0) return;
        double relative = (mouseY - (topPos + LIST_Y) - SCROLLER_HEIGHT / 2.0) / (TRACK_HEIGHT - SCROLLER_HEIGHT);
        setScroll((int) Math.round(Mth.clamp(relative, 0.0, 1.0) * max));
    }

    // ------------------------------------------------------------------ кинжал

    /** Смещение кинжала: та же синусоида, что у кинжала-частицы над столом в C&C (y += sin(age*0.1)*0.005 за тик). */
    private float daggerBob(float partialTick) {
        if (!isDaggerActive()) return 0.0F;
        float age = daggerAge + partialTick;
        float blocks = 0.05F * (1.0F - Mth.cos(age * 0.1F));   // сумма sin(age*0.1)*0.005
        return -blocks * (16.0F / 0.425F);                       // блоки -> пиксели (размер частицы 0.425)
    }

    private boolean overDagger(double mouseX, double mouseY, float partialTick) {
        double x = leftPos + DAGGER_X;
        double y = topPos + DAGGER_Y + daggerBob(partialTick);
        return mouseX >= x && mouseX < x + DAGGER_WIDTH && mouseY >= y && mouseY < y + DAGGER_HEIGHT;
    }

    private Component tierName(int tierIndex) {
        return Component.translatable("container.atoning_penchant.tier." + tierIndex)
                .withStyle(tierIndex == 2 ? ChatFormatting.RED : tierIndex == 1 ? ChatFormatting.GOLD : ChatFormatting.GREEN);
    }

    // ------------------------------------------------------------------ мышь

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isDaggerActive() && overDagger(mouseX, mouseY, 0.0F)) {
            menu.cycleTier();
            requireNonNull(minecraft).getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            rebuildWidgets();
            return true;
        }
        if (button == 0 && maxScroll() > 0 && overTrack(mouseX, mouseY)) {
            draggingScroller = true;
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScroller) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroller = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && (overList(mouseX, mouseY) || overTrack(mouseX, mouseY))) {
            setScroll(scroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ тики и отрисовка

    @Override
    public void containerTick() {
        super.containerTick();
        ingredientSlotBackground.tick(menu.canDisenchant() ? List.of(LAPIS_SLOT_ICON, BOOK_SLOT_ICON) : List.of(LAPIS_SLOT_ICON));
        tickBook();
        if (isDaggerActive()) daggerAge++;
    }

    /** Анимация книги скопирована из стола Penchant: открыта, если есть что выбирать. */
    private void tickBook() {
        ItemStack stack = menu.getEnchantingStack();
        if (!ItemStack.matches(stack, last)) {
            last = stack;
            do {
                flipT = flipT + (random.nextInt(4) - random.nextInt(4));
            } while (flip <= flipT + 1.0F && flip >= flipT - 1.0F);
        }

        oFlip = flip;
        oOpen = open;

        open = Mth.clamp(open + (isBookOpen() ? 0.2F : -0.2F), 0.0F, 1.0F);
        float f = Mth.clamp((flipT - flip) * 0.4F, -0.2F, 0.2F);
        flipA = flipA + (f - flipA) * 0.9F;
        flip = flip + flipA;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(AtoningSprites.WINDOW, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        renderBook(graphics, partialTick);

        var font = Minecraft.getInstance().font;
        Component books = Component.empty()
                .append(PenchantFont.BOOK).append(PenchantFont.THIN_SPACE).append(Integer.toString(menu.getBookCount()));
        Component grindstone = Component.empty()
                .append(PenchantFont.GRINDSTONE).append(PenchantFont.THIN_SPACE).append(menu.hasDisenchanter() ? "✔" : "❌");
        graphics.drawString(font, books, leftPos + INFO_X, topPos + INFO_Y, 0xFF606060, false);
        graphics.drawString(font, grindstone, leftPos + INFO_X, topPos + INFO_Y + 11, 0xFF606060, false);

        ingredientSlotBackground.render(menu, graphics, partialTick, leftPos, topPos);

        int max = maxScroll();
        if (max > 0) {
            int y = topPos + LIST_Y + (TRACK_HEIGHT - SCROLLER_HEIGHT) * scroll / max;
            graphics.blitSprite(AtoningSprites.SCROLLER, leftPos + SCROLL_X, y, SCROLLER_WIDTH, SCROLLER_HEIGHT);
        }

        // кинжал над книгой: при наведении обводка становится белой
        daggerHovered = isDaggerActive() && overDagger(mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);   // поверх книги
        graphics.blitSprite(daggerHovered ? AtoningSprites.DAGGER_HIGHLIGHTED : AtoningSprites.DAGGER,
                leftPos + DAGGER_X, Math.round(topPos + DAGGER_Y + daggerBob(partialTick)), DAGGER_WIDTH, DAGGER_HEIGHT);
        graphics.pose().popPose();
    }

    /** Отрисовка сущности книги - логика как в столе Penchant, но с текстурой книги Атонинг-стола из C&C. */
    private void renderBook(GuiGraphics graphics, float partialTick) {
        float openLerp = Mth.lerp(partialTick, oOpen, open);
        float flipLerp = Mth.lerp(partialTick, oFlip, flip);
        Lighting.setupForEntityInInventory();
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + BOOK_X, topPos + BOOK_Y, 100.0F);
        graphics.pose().scale(-BOOK_SCALE, BOOK_SCALE, BOOK_SCALE);
        graphics.pose().mulPose(Axis.XP.rotationDegrees(25.0F));
        graphics.pose().translate((1.0F - openLerp) * 0.2F, (1.0F - openLerp) * 0.1F, (1.0F - openLerp) * 0.25F);
        float rotation = -(1.0F - openLerp) * 90.0F - 90.0F;
        graphics.pose().mulPose(Axis.YP.rotationDegrees(rotation));
        graphics.pose().mulPose(Axis.XP.rotationDegrees(180.0F));
        float pageA = Mth.clamp(Mth.frac(flipLerp + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float pageB = Mth.clamp(Mth.frac(flipLerp + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        bookModel.setupAnim(0.0F, pageA, pageB, openLerp);
        var consumer = graphics.bufferSource().getBuffer(bookModel.renderType(BOOK_TEXTURE));
        bookModel.renderToBuffer(graphics.pose(), consumer, 15728880, OverlayTexture.NO_OVERLAY);
        graphics.flush();
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // заголовок не рисуем (на его месте книга и кинжал), только надпись «Инвентарь»
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 4210752, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float gamePartialTick = requireNonNull(minecraft).getTimer().getGameTimeDeltaPartialTick(false);
        super.render(graphics, mouseX, mouseY, gamePartialTick);

        if (daggerHovered) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("container.atoning_penchant.dagger.tooltip"));
            lines.add(Component.translatable("container.atoning_penchant.dagger.level", tierName(menu.getSelectedTier())));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index <= AtoningPenchantMenu.SPINEL_SLOT) {
            Component tip = switch (hoveredSlot.index) {
                case AtoningPenchantMenu.ITEM_SLOT -> ITEM_SLOT_TOOLTIP;
                case AtoningPenchantMenu.INGREDIENT_SLOT -> menu.canDisenchant() ? INGREDIENT_SLOT_DISENCHANT_TOOLTIP : INGREDIENT_SLOT_TOOLTIP;
                default -> SPINEL_SLOT_TOOLTIP;
            };
            graphics.renderTooltip(font, font.split(tip, TOOLTIP_WIDTH), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }
}
