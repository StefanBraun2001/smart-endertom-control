package eu.stefanbraun612.smartendertomcontrol.mixin;

import eu.stefanbraun612.smartendertomcontrol.SmartEndertomConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
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
 * configurable one, and adds a second roll for a configurable list of
 * "problematic" blocks (cactus and the like) - see docs/GUIDE.md.
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
	private boolean smartendertomcontrol$gateProblematicBlock(BlockState state, TagKey<Block> tag) {
		if (!state.is(tag)) {
			return false;
		}
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.endermanBlockChange || !config.gateProblematicBlocks) {
			return true;
		}
		String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		if (!config.problematicBlock.contains(blockId)) {
			return true;
		}
		RandomSource random = enderman.getRandom();
		return random.nextDouble() < config.problematicBlockChance;
	}
}
