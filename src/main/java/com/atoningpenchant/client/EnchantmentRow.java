package com.atoningpenchant.client;

import com.atoningpenchant.compat.AtoningCosts;
import com.atoningpenchant.menu.AtoningPenchantMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;

/**
 * Кнопка списка чар - по устройству то же, что кнопка стола Penchant: спрайт (обычный / неактивный /
 * подсвеченный), название слева, цена справа, одинаковые цвета. Цвет кнопки зависит от выбранного кинжалом
 * уровня (зелёная / оранжевая / красная), а в цене показывается нужное число книг.
 */
public class EnchantmentRow extends AbstractButton {
    public static final int WIDTH = 91;
    public static final int HEIGHT = 12;

    public static final int DISABLED_COLOR = 0xFF685E4A;
    public static final int INSUFFICIENT_COLOR = 0xFFFF5555;
    public static final int BOOK_COLOR = 0xFFFFAA00;

    private static final int[] TIER_COLORS = {0xFF55FF55, 0xFFFFAA00, 0xFFFF5555};

    private final AtoningPenchantMenu menu;
    private final int buttonId;
    private final int tier;
    private final Component text;
    private final Component costText;
    private final boolean isCurse;

    public EnchantmentRow(int x, int y, AtoningPenchantMenu menu, Holder<Enchantment> enchantment) {
        super(x, y, WIDTH, HEIGHT, enchantment.value().description());
        this.menu = menu;
        this.isCurse = enchantment.is(EnchantmentTags.CURSE);
        boolean disenchanting = menu.isDisenchanting();

        Player player = Minecraft.getInstance().player;
        boolean creative = player != null && player.hasInfiniteMaterials();
        ItemStack stack = menu.getEnchantingStack();

        boolean unlocked = menu.isAvailable(enchantment);
        boolean alreadyHas = menu.hasEnchantment(stack, enchantment);
        boolean canUse = disenchanting || !alreadyHas;
        List<Holder<Enchantment>> incompatible = unlocked && !disenchanting ? menu.getIncompatible(stack, enchantment) : List.of();

        // ---- уровень сложности (для снятия чар не применяется)
        this.tier = disenchanting ? 1 : AtoningCosts.effectiveTier(enchantment, menu.getSelectedTier());
        this.buttonId = menu.getButtonId(enchantment, disenchanting ? 0 : menu.getSelectedTier());

        int bookRequirement = disenchanting ? menu.getBookRequirement(enchantment) : menu.getRequiredBooks(enchantment, tier);
        boolean hasEnoughBooks = creative || menu.getBookCount() >= bookRequirement;
        int fuel = AtoningCosts.FUEL_COST;
        boolean hasSpinel = creative || menu.getSpinelCount() >= fuel;
        boolean hasLapis = creative || menu.getLapisCount() >= fuel;
        boolean showBookCost = unlocked;

        // ---- название (ещё не открытые чары пишутся «галактическим» шрифтом, как в столе зачарований)
        MutableComponent title = enchantment.value().description().copy();
        if (!unlocked && canUse && !disenchanting) title.withStyle(style -> style.withFont(Minecraft.ALT_FONT));
        this.text = title;

        // ---- цена справа: значок несовместимости и нужное число книг (как у Penchant)
        List<Component> costTexts = new ArrayList<>(2);
        if (!incompatible.isEmpty()) {
            costTexts.add(Component.translatable("widget.atoning_penchant.row.incompatible").withColor(INSUFFICIENT_COLOR));
        }
        if (showBookCost) {
            costTexts.add(Component.literal(Integer.toString(bookRequirement))
                    .withColor(!canUse ? DISABLED_COLOR : !hasEnoughBooks ? INSUFFICIENT_COLOR : BOOK_COLOR));
        }
        this.costText = ComponentUtils.formatList(costTexts, Component.literal(" "));

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
                tooltip.append("\n").append(Component.translatable("widget.atoning_penchant.row.tooltip.incompatible",
                        ComponentUtils.formatList(incompatible, holder -> holder.value().description())).withStyle(ChatFormatting.RED));
            }

            tooltip.append("\n").append(Component.translatable("widget.atoning_penchant.row.tooltip.books", bookRequirement)
                    .withColor(hasEnoughBooks ? BOOK_COLOR : INSUFFICIENT_COLOR));

            if (!disenchanting) {
                // лазурит и шпинель: серым, если хватает, красным, если нет (как цены в C&C)
                tooltip.append("\n").append(fuelLine("widget.atoning_penchant.row.tooltip.spinel", fuel, hasSpinel));
                tooltip.append("\n").append(fuelLine("widget.atoning_penchant.row.tooltip.lapis", fuel, hasLapis));

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
                ? hasEnoughBooks
                : unlocked && incompatible.isEmpty() && hasEnoughBooks && hasSpinel && hasLapis);
    }

    private static Component fuelLine(String key, int amount, boolean enough) {
        return Component.translatable(key, amount).withStyle(enough ? ChatFormatting.GRAY : ChatFormatting.RED);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(AtoningSprites.BUTTONS[tier].get(active, isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight());

        Font font = Minecraft.getInstance().font;

        // цена справа, как у Penchant
        int costWidth = font.width(costText);
        if (costWidth > 0) {
            graphics.drawString(font, costText, getX() + width - 2 - costWidth, getY() + 2, 0xFF404040, true);
        }

        // название слева; если не влезает - обрезаем и ставим "..."
        int available = width - 2 - 2 - (costWidth > 0 ? costWidth + 3 : 0);
        Component shown = fit(font, text, available);
        graphics.drawString(font, shown, getX() + 2, getY() + 2,
                active && isHoveredOrFocused() ? 0xFFFCFC7E : isCurse ? 0xFF891D13 : 0xFF332E25, false);
    }

    /** Сокращает название до максимума букв, которые влезают, и добавляет "...". */
    private static Component fit(Font font, Component name, int maxWidth) {
        if (font.width(name) <= maxWidth) return name;

        String full = name.getString();
        int ellipsisWidth = font.width(Component.literal("...").withStyle(name.getStyle()));
        int end = full.length();
        while (end > 0 && font.width(Component.literal(full.substring(0, end)).withStyle(name.getStyle())) + ellipsisWidth > maxWidth) {
            end--;
        }
        return Component.literal(full.substring(0, end).stripTrailing() + "...").withStyle(name.getStyle());
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
