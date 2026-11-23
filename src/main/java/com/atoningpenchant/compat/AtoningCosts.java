package com.atoningpenchant.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Правила «уровней сложности» Атонинг-стола.
 *
 * <p>Цены - ровно как в Caverns & Chasms: уровень i (1..3) стоит i лазурита + i шпинели и забирает
 * {@code min(100%, 25%*i + random*27.5%)} максимальной прочности предмета.</p>
 *
 * <p>Новое по сравнению с C&C - что получаешь: чем выше уровень, тем лучше «заряжена» чара в системе
 * прокачки Penchant. Все числа этого класса можно спокойно править.</p>
 */
public final class AtoningCosts {
    private AtoningCosts() {}

    public static final int TIER_COUNT = 3;

    // ---- сколько долей пути до максимального уровня чара получаешь сразу (0.0 .. 1.0)
    /** Нормальный уровень: от 30% до 75% пути (обычно «почти второй уровень» или уже второй). */
    private static final float NORMAL_MIN = 0.30F, NORMAL_MAX = 0.75F;
    /** Сложный уровень: с шансом 30% чара сразу максимальная, иначе от 60% до 95% пути. */
    private static final float HARD_FULL_CHANCE = 0.30F;
    private static final float HARD_MIN = 0.60F, HARD_MAX = 0.95F;

    /** Результат зачарования: уровень чары и накопленный прогресс к следующему уровню. */
    public record Outcome(int level, int progress) {}

    // ------------------------------------------------------------------ цены (как в C&C)

    /** tierIndex: 0 - лёгкий, 1 - нормальный, 2 - сложный. Сколько лазурита и сколько шпинели. */
    public static int fuelCost(int tierIndex) {
        return tierIndex + 1;
    }

    public static float minLossFraction(int tierIndex) {
        return Math.min(1.0F, 0.25F * (tierIndex + 1));
    }

    public static float maxLossFraction(int tierIndex) {
        return Math.min(1.0F, 0.25F * (tierIndex + 1) + 0.275F);
    }

    public static int minLossPercent(int tierIndex) {
        return (int) (minLossFraction(tierIndex) * 100);
    }

    public static int maxLossPercent(int tierIndex) {
        return (int) (maxLossFraction(tierIndex) * 100);
    }

    /** Формула C&C: min(1, 0.25*i + rand*0.275) * максимальная прочность. */
    public static int rollLoss(RandomSource random, ItemStack stack, int tierIndex) {
        float fraction = Math.min(1.0F, 0.25F * (tierIndex + 1) + random.nextFloat() * 0.275F);
        return (int) (fraction * stack.getMaxDamage());
    }

    /** Может ли предмет сломаться при самом плохом броске. */
    public static boolean mayBreak(ItemStack stack, int tierIndex) {
        if (!stack.isDamageableItem()) return false;
        int worst = (int) (maxLossFraction(tierIndex) * stack.getMaxDamage());
        return worst >= stack.getMaxDamage() - stack.getDamageValue();
    }

    // ------------------------------------------------------------------ что получаем

    /** Умеет ли чара прокачиваться (иначе у неё всегда 1 уровень и «сложность» ей ничего не даёт). */
    public static boolean canLevel(Holder<Enchantment> enchantment) {
        return enchantment.value().getMaxLevel() > 1 && !enchantment.is(PenchantBridge.NO_LEVELING);
    }

    /** Для не прокачиваемых чар всегда действует лёгкий уровень. */
    public static int effectiveTier(Holder<Enchantment> enchantment, int requestedTier) {
        return canLevel(enchantment) ? Math.max(0, Math.min(TIER_COUNT - 1, requestedTier)) : 0;
    }

    public static Outcome rollOutcome(RandomSource random, RegistryAccess access, Holder<Enchantment> enchantment, int tierIndex, int maxDamage) {
        int maxLevel = enchantment.value().getMaxLevel();
        if (tierIndex <= 0 || !canLevel(enchantment)) return new Outcome(1, 0);

        float fraction;
        if (tierIndex == 1) {
            fraction = NORMAL_MIN + random.nextFloat() * (NORMAL_MAX - NORMAL_MIN);
        } else if (random.nextFloat() < HARD_FULL_CHANCE) {
            fraction = 1.0F;
        } else {
            fraction = HARD_MIN + random.nextFloat() * (HARD_MAX - HARD_MIN);
        }

        // сколько прогресса нужно на каждый шаг 1->2, 2->3 ... (по правилам Penchant)
        int[] stepCost = new int[maxLevel];
        int total = 0;
        for (int level = 1; level < maxLevel; level++) {
            stepCost[level] = PenchantBridge.getMaxProgress(access, enchantment, level, maxDamage);
            total += stepCost[level];
        }
        if (total <= 0) return new Outcome(1, 0);

        int remaining = Math.round(total * fraction);
        int level = 1;
        while (level < maxLevel && remaining >= stepCost[level]) {
            remaining -= stepCost[level];
            level++;
        }
        if (level >= maxLevel) remaining = 0;
        return new Outcome(level, remaining);
    }
}
