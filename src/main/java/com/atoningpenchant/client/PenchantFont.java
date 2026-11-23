package com.atoningpenchant.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Значки книги и наждачного камня из шрифта Penchant (penchant:bar) - те же, что в его столе. */
public final class PenchantFont {
    private PenchantFont() {}

    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("penchant", "bar");

    private static Style withFont(Style style) {
        return style.withFont(FONT);
    }

    public static final Component THIN_SPACE = Component.literal("\u2009").withStyle(PenchantFont::withFont);
    public static final Component BOOK = Component.literal("B").withStyle(PenchantFont::withFont);
    public static final Component GRINDSTONE = Component.literal("G").withStyle(PenchantFont::withFont);
}
