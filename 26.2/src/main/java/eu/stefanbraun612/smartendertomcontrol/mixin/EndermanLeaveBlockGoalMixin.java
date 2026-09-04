package eu.stefanbraun612.smartendertomcontrol.mixin;

import eu.stefanbraun612.smartendertomcontrol.SmartEndertomConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces the vanilla per-tick "want to place the carried block" roll with a configurable one - see docs/GUIDE.md. */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class EndermanLeaveBlockGoalMixin {
	@Shadow
	@Final
	private EnderMan enderman;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void smartendertomcontrol$canUse(CallbackInfoReturnable<Boolean> cir) {
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.endermanBlockChange) {
			return;
		}
		if (enderman.getCarriedBlock() == null) {
			cir.setReturnValue(false);
			return;
		}
		ServerLevel level = (ServerLevel) enderman.level();
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			cir.setReturnValue(false);
			return;
		}
		cir.setReturnValue(enderman.getRandom().nextDouble() < config.endermanPlaceChance);
	}
}
