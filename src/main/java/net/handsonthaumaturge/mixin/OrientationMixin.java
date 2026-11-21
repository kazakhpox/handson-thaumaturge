package net.handsonthaumaturge.mixin;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.diegesis.api.station.StationType;

import net.handsonthaumaturge.station.ResearchStation;
import net.handsonthaumaturge.station.Thaum;

/**
 * Diegesis turns a BLOCK station to the block's facing; the research table's facing runs along it, and it is worked from its long
 * side: for that table only, the facing is turned a quarter (see {@link ResearchStation#front}).
 */
@Mixin(value = StationType.Orientation.class, remap = false)
abstract class OrientationMixin {
    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private void handson$tableFromItsSide(BlockState state, Direction towardsPlayer, CallbackInfoReturnable<Direction> cir) {
        if ((Object) this == StationType.Orientation.BLOCK && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && Thaum.RESEARCH_TABLE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            cir.setReturnValue(ResearchStation.front(state));
        }
    }
}
