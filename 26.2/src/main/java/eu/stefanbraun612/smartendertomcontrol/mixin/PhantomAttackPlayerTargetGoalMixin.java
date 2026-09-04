package eu.stefanbraun612.smartendertomcontrol.mixin;

import eu.stefanbraun612.smartendertomcontrol.PhantomEligibility;
import eu.stefanbraun612.smartendertomcontrol.SmartEndertomConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;

/**
 * Targets the private inner class Phantom$PhantomAttackPlayerTargetGoal.
 * Extends Goal (its real superclass) purely so the inherited protected
 * static helpers (reducedTickDelay, getServerLevel) are callable from
 * source - Mixin discards this fake inheritance when merging into the
 * real target class.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomAttackPlayerTargetGoal")
public abstract class PhantomAttackPlayerTargetGoalMixin extends Goal {

	@Shadow(aliases = "this$0")
	@Final
	private Phantom this$0;

	@Shadow
	@Final
	private TargetingConditions attackTargeting;

	@Shadow
	private int nextScanTick;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void smartendertomcontrol$canUse(CallbackInfoReturnable<Boolean> cir) {
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.neutralUntilEligible) {
			return;
		}

		if (this.nextScanTick > 0) {
			--this.nextScanTick;
			cir.setReturnValue(false);
			return;
		}
		this.nextScanTick = reducedTickDelay(60);

		ServerLevel level = getServerLevel(this.this$0.level());
		List<Player> players = level.getNearbyPlayers(
				this.attackTargeting, this.this$0, this.this$0.getBoundingBox().inflate(16.0, 64.0, 16.0));
		if (!players.isEmpty()) {
			Comparator<Player> byY = Comparator.comparingDouble(Player::getY);
			players.sort(byY.reversed());
			for (Player player : players) {
				if (!(player instanceof ServerPlayer serverPlayer)) {
					continue;
				}
				if (!PhantomEligibility.isEligible(serverPlayer, config)) {
					continue;
				}
				if (!((PhantomCanAttackInvoker) this.this$0)
						.smartendertomcontrol$canAttack(level, player, TargetingConditions.DEFAULT)) {
					continue;
				}
				this.this$0.setTarget(player);
				cir.setReturnValue(true);
				return;
			}
		}
		cir.setReturnValue(false);
	}

	@Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
	private void smartendertomcontrol$canContinueToUse(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) {
			return;
		}
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.neutralUntilEligible || !config.dropTargetOnceIneligible) {
			return;
		}

		LivingEntity target = this.this$0.getTarget();
		if (target == null || target == this.this$0.getLastHurtByMob()) {
			// Don't auto-drop a target that's actively retaliating against its attacker -
			// otherwise retaliateWhenAttacked + dropTargetOnceIneligible would cancel each
			// other out (fight back for one tick, then immediately disengage).
			return;
		}
		if (target instanceof ServerPlayer serverPlayer && !PhantomEligibility.isEligible(serverPlayer, config)) {
			cir.setReturnValue(false);
		}
	}
}
