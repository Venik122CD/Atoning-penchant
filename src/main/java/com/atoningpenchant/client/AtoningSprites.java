package com.atoningpenchant.client;

import com.atoningpenchant.AtoningPenchant;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;

/**
 * Все картинки окна. Лежат в assets/atoning_penchant/textures/gui/... - их можно просто заменить
 * своими PNG того же размера (размеры см. в README).
 */
public final class AtoningSprites {
    private AtoningSprites() {}

    public static final ResourceLocation WINDOW =
            ResourceLocation.fromNamespaceAndPath(AtoningPenchant.MOD_ID, "textures/gui/container/atoning_table.png");

    public static final ResourceLocation SCROLLER = sprite("scroller");

    /** Кнопки: 0 - лёгкая (зелёная), 1 - нормальная (оранжевая), 2 - сложная (красная). */
    public static final WidgetSprites[] BUTTONS = {
            buttons("easy"),
            buttons("normal"),
            buttons("hard")
    };

    private static WidgetSprites buttons(String name) {
        return new WidgetSprites(
                sprite("button_" + name),
                sprite("button_disabled"),
                sprite("button_" + name + "_highlighted")
        );
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(AtoningPenchant.MOD_ID, "container/atoning_table/" + name);
    }
}
