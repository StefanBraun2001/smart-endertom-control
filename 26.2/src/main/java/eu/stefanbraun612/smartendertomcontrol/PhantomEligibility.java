package eu.stefanbraun612.smartendertomcontrol;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;

/** Shared "has this player crossed the insomnia threshold" check, used by both the spawner and the targeting mixin. */
public final class PhantomEligibility {

	private PhantomEligibility() {
	}

	public static boolean isEligible(ServerPlayer player, SmartEndertomConfig.Data config) {
		ServerStatsCounter stats = player.getStats();
		int timeSinceRest = Mth.clamp(
				(int) stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
		return timeSinceRest > config.thresholdTicks;
	}
}
