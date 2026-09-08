package eu.stefanbraun612.smartendertomcontrol.mixin;

import eu.stefanbraun612.smartendertomcontrol.SmartEndertomConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the vanilla per-tick "want to pick up a block" roll with a
 * configurable one. Also replaces vanilla's ENDERMAN_HOLDABLE tag check
 * entirely with the config's own holdableBlocks list while
 * endermanBlockChange=true, including a second, harder roll for entries
 * flagged "problematic" (cactus and the like by default) - see
 * docs/GUIDE.md.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {
	@Shadow
	@Final
	private EnderMan enderman;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void smartendertomcontrol$canUse(CallbackInfoReturnable<Boolean> cir) {
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.endermanBlockChange) {
			return;
		}
		if (enderman.getCarriedBlock() != null) {
			cir.setReturnValue(false);
			return;
		}
		ServerLevel level = (ServerLevel) enderman.level();
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			cir.setReturnValue(false);
			return;
		}
		cir.setReturnValue(enderman.getRandom().nextDouble() < config.endermanPickupChance);
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean smartendertomcontrol$isHoldable(BlockState state, TagKey<Block> tag) {
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.endermanBlockChange) {
			return state.is(tag);
		}
		String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		SmartEndertomConfig.HoldableBlockEntry entry = config.findHoldableBlock(blockId);
		if (entry == null) {
			return false;
		}
		if (!entry.problematic || !config.gateProblematicBlocks) {
			return true;
		}
		double chance = entry.chance >= 0 ? entry.chance : config.problematicBlockChance;
		return enderman.getRandom().nextDouble() < chance;
	}
}
