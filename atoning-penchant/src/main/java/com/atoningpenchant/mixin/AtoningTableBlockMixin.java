package com.atoningpenchant.mixin;

import com.atoningpenchant.compat.PenchantBridge;
import com.atoningpenchant.menu.AtoningPenchantMenu;
import com.teamabnormals.caverns_and_chasms.common.block.AtoningTableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Подменяет меню Атонинг-стола на наше (список чар в стиле Penchant).
 * Сам блок, его блок-сущность, рецепт, частицы и звуки остаются родными - от C&C.
 */
@Mixin(AtoningTableBlock.class)
public abstract class AtoningTableBlockMixin {
    @Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
    private void atoningpenchant$useReworkedMenu(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> cir) {
        if (!PenchantBridge.isReworkedTableEnabled()) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Nameable nameable) {
            cir.setReturnValue(AtoningPenchantMenu.provider(nameable.getDisplayName(), level, pos));
        }
    }
}
