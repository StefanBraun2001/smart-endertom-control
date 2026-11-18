package eu.stefanbraun612.smartendertomcontrol.mixin;

import eu.stefanbraun612.smartendertomcontrol.SmartEndertomConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla 2-tick pause between two XP orb pickups with a configurable one, and with
 * xpOrbCollectionRate = -1 also collects a whole merged orb stack in one touch (vanilla takes one
 * orb of the stack per touch) - see docs/GUIDE.md.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbPickupDelayMixin {
	@Shadow
	private int count;

	@Shadow
	private native int repairPlayerItems(ServerPlayer player, int amount);

	@Redirect(
			method = "playerTouch",
			at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;takeXpDelay:I", opcode = 181))
	private void smartendertomcontrol$pickupDelay(Player player, int vanillaDelay) {
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.modifyXpOrbCollection) {
			player.takeXpDelay = vanillaDelay;
			return;
		}
		int rate = config.xpOrbCollectionRate;
		player.takeXpDelay = rate < 1 ? 0 : Math.max(1, Math.round(20f / rate));
	}

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void smartendertomcontrol$collectWholeStack(Player player, CallbackInfo ci) {
		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		if (!config.modifyXpOrbCollection || config.xpOrbCollectionRate >= 1 || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		ci.cancel();
		ExperienceOrb orb = (ExperienceOrb) (Object) this;
		int stack = count;
		int leftover = 0;
		for (int i = 0; i < stack; i++) {
			leftover += repairPlayerItems(serverPlayer, orb.getValue());
		}
		player.take(orb, stack);
		if (leftover > 0) {
			player.giveExperiencePoints(leftover);
		}
		count = 0;
		orb.discard();
	}
}
