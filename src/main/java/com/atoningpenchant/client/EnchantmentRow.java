package com.atoningpenchant.client;

import com.atoningpenchant.compat.AtoningCosts;
import com.atoningpenchant.menu.AtoningPenchantMenu;
import com.teamabnormals.caverns_and_chasms.core.registry.CCItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;

/** Одна кнопка списка чар: цвет зависит от выбранного кинжалом уровня (зелёная / оранжевая / красная). */
public class EnchantmentRow extends AbstractButton {
    public static final int WIDTH = 92;
    public static final int HEIGHT = 20;

    private static final int INSUFFICIENT_COLOR = 0xFFFF5555;
    private static final int BOOK_COLOR = 0xFFFFAA00;
    private static final int TEXT_COLOR = 0xFF332E25;
    private static final int HOVER_COLOR = 0xFFFCFC7E;
    private static final int CURSE_COLOR = 0xFF891D13;
    private static final int[] TIER_COLORS = {0xFF55FF55, 0xFFFFAA00, 0xFFFF5555};

    private static final ItemStack LAPIS_ICON = new ItemStack(Items.LAPIS_LAZULI);

    private final AtoningPenchantMenu menu;
    private final int buttonId;
    private final int tier;
    private final Component name;
    private final boolean isCurse;
    private final boolean showCosts;
    private final boolean booksOk;
    private final int bookRequirement;
    private final int fuel;
    private final boolean spinelOk;
    private final boolean lapisOk;
    private final boolean fuelOk;
    private final boolean disenchanting;

    public EnchantmentRow(int x, int y, AtoningPenchantMenu menu, Holder<Enchantment> enchantment) {
        super(x, y, WIDTH, HEIGHT, enchantment.value().description());
        this.menu = menu;
        this.isCurse = enchantment.is(EnchantmentTags.CURSE);
        this.disenchanting = menu.isDisenchanting();

        Player player = Minecraft.getInstance().player;
        boolean creative = player != null && player.hasInfiniteMaterials();
        ItemStack stack = menu.getEnchantingStack();

        boolean unlocked = menu.isAvailable(enchantment);
        boolean alreadyHas = menu.hasEnchantment(stack, enchantment);
        List<Holder<Enchantment>> incompatible = unlocked && !disenchanting ? menu.getIncompatible(stack, enchantment) : List.of();

        this.bookRequirement = menu.getBookRequirement(enchantment);
        this.booksOk = creative || menu.getBookCount() >= bookRequirement;

        // ---- уровень сложности (для снятия чар не применяется)
        this.tier = disenchanting ? 1 : AtoningCosts.effectiveTier(enchantment, menu.getSelectedTier());
        this.buttonId = menu.getButtonId(enchantment, disenchanting ? 0 : menu.getSelectedTier());
        this.fuel = AtoningCosts.fuelCost(tier);
        this.spinelOk = creative || menu.getSpinelCount() >= fuel;
        this.lapisOk = creative || menu.getLapisCount() >= fuel;
        this.fuelOk = spinelOk && lapisOk;
        this.showCosts = unlocked || disenchanting;

        // ---- название (ещё не открытые чары пишутся «галактическим» шрифтом, как в столе зачарований)
        MutableComponent title = enchantment.value().description().copy();
        boolean canUse = disenchanting || !alreadyHas;
        if (!unlocked && canUse && !disenchanting) title.withStyle(style -> style.withFont(Minecraft.ALT_FONT));
        this.name = title;

        // ---- подсказка
        if (!canUse) {
            setTooltip(Tooltip.create(Component.empty()
                    .append(enchantment.value().description().copy().withStyle(ChatFormatting.GRAY))
                    .append("\n")
                    .append(Component.translatable("widget.atoning_penchant.row.tooltip.added").withStyle(ChatFormatting.GRAY))));
        } else if (!unlocked && !disenchanting) {
            String plain = enchantment.value().description().getString();
            setTooltip(Tooltip.create(Component.literal(plain.substring(0, plain.length() * 2 / 3).trim() + "...")
                    .withStyle(ChatFormatting.GRAY)
                    .append("\n")
                    .append(Component.translatable("widget.atoning_penchant.row.tooltip.locked").withStyle(ChatFormatting.RED))));
        } else {
            MutableComponent tooltip = disenchanting
                    ? Component.translatable("widget.atoning_penchant.row.tooltip.remove", enchantment.value().description())
                    : enchantment.value().description().copy().withStyle(isCurse ? ChatFormatting.RED : ChatFormatting.GRAY);

            if (!incompatible.isEmpty()) {
                MutableComponent names = Component.empty();
                for (int i = 0; i < incompatible.size(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(incompatible.get(i).value().description());
                }
                tooltip.append("\n").append(Component.translatable("widget.atoning_penchant.row.tooltip.incompatible", names)
                        .withStyle(ChatFormatting.RED));
            }

            tooltip.append("\n").append(Component.translatable("widget.atoning_penchant.row.tooltip.books", bookRequirement)
                    .withColor(booksOk ? BOOK_COLOR : INSUFFICIENT_COLOR));

            if (!disenchanting) {
                // цены в стиле C&C: серым, если хватает, красным, если нет
                tooltip.append("\n").append(fuelLine("widget.atoning_penchant.row.tooltip.spinel", fuel, spinelOk));
                tooltip.append("\n").append(fuelLine("widget.atoning_penchant.row.tooltip.lapis", fuel, lapisOk));

                if (stack.isDamageableItem()) {
                    tooltip.append("\n").append(Component.translatable("widget.atoning_penchant.row.tooltip.durability",
                                    AtoningCosts.minLossPercent(tier), AtoningCosts.maxLossPercent(tier))
                            .withColor(TIER_COLORS[tier]));
                    if (AtoningCosts.mayBreak(stack, tier)) {
                        tooltip.append("\n").append(Component.translatable("widget.atoning_penchant.row.tooltip.may_break")
                                .withStyle(ChatFormatting.DARK_RED));
                    }
                }
                if (AtoningCosts.canLevel(enchantment)) {
                    tooltip.append("\n").append(Component.translatable("widget.atoning_penchant.row.tooltip.leveling." + tier)
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            setTooltip(Tooltip.create(tooltip));
        }

        this.active = canUse && (disenchanting
                ? booksOk
                : unlocked && incompatible.isEmpty() && booksOk && fuelOk);
    }

    private static Component fuelLine(String key, int amount, boolean enough) {
        return Component.translatable(key, amount).withStyle(enough ? ChatFormatting.GRAY : ChatFormatting.RED);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(AtoningSprites.BUTTONS[tier].get(active, isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight());

        Font font = Minecraft.getInstance().font;
        int color = active && isHoveredOrFocused() ? HOVER_COLOR : isCurse ? CURSE_COLOR : TEXT_COLOR;

        // строка 1: название (обрезается, если не влезает рядом с иконкой меча)
        int textX = getX() + 21;
        graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(name, WIDTH - 21 - 3)),
                textX, getY() + 3, color, false);

        // строка 2: цена - книги, шпинель, лазурит
        if (showCosts) {
            int y = getY() + 11;
            MutableComponent books = Component.empty().append(PenchantFont.BOOK).append(PenchantFont.THIN_SPACE)
                    .append(Component.literal(Integer.toString(bookRequirement))
                            .withColor(booksOk ? BOOK_COLOR : INSUFFICIENT_COLOR));
            graphics.drawString(font, books, textX, y, 0xFF404040, true);

            if (!disenchanting) {
                int x = textX + font.width(books) + 6;
                x = drawCost(graphics, font, new ItemStack(CCItems.SPINEL.get()), x, y, spinelOk);
                drawCost(graphics, font, LAPIS_ICON, x + 4, y, lapisOk);
            }
        }
    }

    /** Рисует маленькую иконку предмета (8x8) и число рядом. Возвращает x после неё. */
    private int drawCost(GuiGraphics graphics, Font font, ItemStack icon, int x, int y, boolean enough) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y - 0.5F, 0);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();

        Component amount = Component.literal(Integer.toString(fuel)).withColor(enough ? 0xFF404040 : INSUFFICIENT_COLOR);
        graphics.drawString(font, amount, x + 9, y, 0xFF404040, true);
        return x + 9 + font.width(amount);
    }

    @Override
    public void onPress() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
