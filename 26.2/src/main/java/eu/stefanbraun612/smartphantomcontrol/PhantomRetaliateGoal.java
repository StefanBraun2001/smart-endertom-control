package eu.stefanbraun612.smartphantomcontrol;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Restores vanilla's usual "fight back when hit" mob behavior, which
 * Phantoms uniquely lack (no HurtByTargetGoal is registered for them, and
 * that class requires PathfinderMob anyway, which Phantom isn't). Only
 * added to a Phantom's target selector when neutralUntilEligible and
 * retaliateWhenAttacked are both enabled at the time it spawns.
 */
public final class PhantomRetaliateGoal extends Goal {
	private final Phantom phantom;

	public PhantomRetaliateGoal(Phantom phantom) {
		this.phantom = phantom;
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		if (this.phantom.getTarget() != null) {
			return false;
		}
		LivingEntity attacker = this.phantom.getLastHurtByMob();
		return attacker instanceof Player && attacker.isAlive();
	}

	@Override
	public void start() {
		this.phantom.setTarget(this.phantom.getLastHurtByMob());
		super.start();
	}
}
