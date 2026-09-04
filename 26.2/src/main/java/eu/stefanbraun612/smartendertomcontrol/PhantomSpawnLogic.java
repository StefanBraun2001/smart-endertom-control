package eu.stefanbraun612.smartendertomcontrol;

import eu.stefanbraun612.smartendertomcontrol.mixin.PhantomSpawnerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.network.chat.Component;

/**
 * Drop-in replacement for vanilla PhantomSpawner.tick(), ported from the
 * 26.2 decompiled source. Every gate (spawn_phantoms gamerule, sky/darken
 * check, sky access, local difficulty roll, placement validity) is kept
 * byte-for-byte identical to vanilla - only the insomnia threshold, the
 * spawn-chance formula's cap, and the group-size roll are tier-driven.
 */
public final class PhantomSpawnLogic {

	private static final int DAY_LENGTH_TICKS = 24000;

	private PhantomSpawnLogic() {
	}

	private static void report(ServerPlayer player, SmartEndertomConfig.Data config, String message) {
		if (config.logToConsole) {
			SmartEndertomControl.LOGGER.info(message);
		}
		if (config.logToChat) {
			player.sendSystemMessage(Component.literal(message));
		}
	}

	public static void tick(PhantomSpawnerAccessor accessor, ServerLevel level, boolean spawnEnemies) {
		if (!spawnEnemies) {
			return;
		}
		if (!level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) {
			return;
		}

		RandomSource random = level.getRandom();

		int next = accessor.smartendertomcontrol$getNextTick() - 1;
		accessor.smartendertomcontrol$setNextTick(next);
		if (next > 0) {
			return;
		}
		accessor.smartendertomcontrol$setNextTick(next + (60 + random.nextInt(60)) * 20);

		if (level.getSkyDarken() < 5 && level.dimensionType().hasSkyLight()) {
			return;
		}

		SmartEndertomConfig.Data config = SmartEndertomConfig.get();
		int threshold = config.thresholdTicks;

		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}

			BlockPos playerPos = player.blockPosition();
			if (level.dimensionType().hasSkyLight()
					&& (playerPos.getY() < level.getSeaLevel() || !level.canSeeSky(playerPos))) {
				continue;
			}

			DifficultyInstance difficulty = level.getCurrentDifficultyAt(playerPos);
			if (!difficulty.isHarderThan(random.nextFloat() * 3.0f)) {
				continue;
			}

			boolean debug = config.logToConsole || config.logToChat;

			ServerStatsCounter stats = player.getStats();
			int timeSinceRest = Mth.clamp(
					(int) stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
			if (timeSinceRest <= threshold) {
				if (debug) {
					report(player, config, String.format(
							"[EndertomTuner] %s: not yet eligible (timeSinceRest=%d, threshold=%d)",
							player.getScoreboardName(), timeSinceRest, threshold));
				}
				continue;
			}

			int nightsSinceEligible = (timeSinceRest - threshold) / DAY_LENGTH_TICKS + 1;
			SmartEndertomConfig.NightTier tier = (nightsSinceEligible - 1 < config.tiers.size())
					? config.tiers.get(nightsSinceEligible - 1)
					: null;

			double computedChance = (double) (timeSinceRest - threshold) / (double) timeSinceRest;
			boolean chanceCapApplied = tier != null && tier.chanceCap >= 0 && tier.chanceCap < computedChance;
			double chance = (tier != null && tier.chanceCap >= 0)
					? Math.min(computedChance, tier.chanceCap)
					: computedChance;

			boolean ceilingApplied = config.useSuccessCeiling && config.successCeiling < chance;
			if (config.useSuccessCeiling) {
				chance = Math.min(chance, config.successCeiling);
			}

			double roll = random.nextDouble();
			boolean rollSuccess = roll < chance;

			if (debug) {
				report(player, config, String.format(
						"[EndertomTuner] %s: night=%d threshold=%d vanillaChance=%.1f%% cap=%s capApplied=%b ceiling=%s ceilingApplied=%b finalChance=%.1f%% roll=%.1f%% -> %s",
						player.getScoreboardName(), nightsSinceEligible, threshold,
						computedChance * 100.0,
						(tier != null && tier.chanceCap >= 0) ? String.format("%.1f%%", tier.chanceCap * 100.0) : "none",
						chanceCapApplied,
						config.useSuccessCeiling ? String.format("%.1f%%", config.successCeiling * 100.0) : "off",
						ceilingApplied, chance * 100.0, roll * 100.0,
						rollSuccess ? "SUCCESS" : "fail"));
			}

			if (!rollSuccess) {
				continue;
			}

			BlockPos spawnPos = playerPos.above(20 + random.nextInt(15))
					.east(-10 + random.nextInt(21))
					.south(-10 + random.nextInt(21));
			BlockState blockState = level.getBlockState(spawnPos);
			FluidState fluidState = level.getFluidState(spawnPos);
			if (!NaturalSpawner.isValidEmptySpawnBlock(
					(BlockGetter) level, spawnPos, blockState, fluidState, EntityTypes.PHANTOM)) {
				if (debug) {
					report(player, config, "[EndertomTuner] " + player.getScoreboardName()
							+ ": roll succeeded but spawn position was invalid, no Phantom placed");
				}
				continue;
			}

			boolean groupSizeCapApplied = !config.useCustomGroupSize && tier != null && tier.maxGroupSize > 0;
			int groupSize;
			if (config.useCustomGroupSize) {
				groupSize = 1 + random.nextInt(config.maxGroupSizeC);
			} else if (groupSizeCapApplied) {
				groupSize = 1 + random.nextInt(tier.maxGroupSize);
			} else {
				groupSize = 1 + random.nextInt(difficulty.getDifficulty().getId() + 1);
			}

			if (debug) {
				report(player, config, String.format(
						"[EndertomTuner] %s: groupSize=%d (%s, vanilla difficulty-based max would be %d)",
						player.getScoreboardName(), groupSize,
						config.useCustomGroupSize ? "custom cap " + config.maxGroupSizeC
								: (groupSizeCapApplied ? "capped at " + tier.maxGroupSize : "uncapped/vanilla"),
						difficulty.getDifficulty().getId() + 1));
			}

			SpawnGroupData groupData = null;
			for (int i = 0; i < groupSize; ++i) {
				Phantom phantom = (Phantom) EntityTypes.PHANTOM.create(level, EntitySpawnReason.NATURAL);
				if (phantom == null) {
					continue;
				}
				phantom.snapTo(spawnPos, 0.0f, 0.0f);
				groupData = phantom.finalizeSpawn(
						(ServerLevelAccessor) level, difficulty, EntitySpawnReason.NATURAL, groupData);
				level.addFreshEntityWithPassengers(phantom);
			}
		}
	}
}
