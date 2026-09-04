package eu.stefanbraun612.smartendertomcontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads/holds the active tuning data (global or per-world - see docs/GUIDE.md). */
public final class SmartEndertomConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("smartendertomcontrol");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path GLOBAL_CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve("smartendertomcontrol.json");
	private static final String PER_WORLD_SUBDIR = "smartendertomcontrol";
	private static final String PER_WORLD_FILE_NAME = "config.json";

	// Pre-A0.3 file locations, back when this mod was "Smart Phantom Control" -
	// the id/package rename means the new paths above are otherwise never
	// connected to these at all, so a one-time migration is done explicitly.
	private static final Path LEGACY_GLOBAL_CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve("smartphantomcontrol.json");
	private static final String LEGACY_PER_WORLD_SUBDIR = "smartphantomcontrol";

	private static volatile Data active = Data.defaults();
	private static volatile Path activePath = GLOBAL_CONFIG_PATH;

	private SmartEndertomConfig() {
	}

	public static Data get() {
		return active;
	}

	/** The config file actually driving current behavior (global or per-world, whichever is active). */
	public static Path getActivePath() {
		return activePath;
	}

	public static synchronized void load(MinecraftServer server) {
		if (migrateLegacy(LEGACY_GLOBAL_CONFIG_PATH, GLOBAL_CONFIG_PATH)) {
			LOGGER.info("[SmartEndertomControl] Migrated old Smart Phantom Control config {} -> {}",
					LEGACY_GLOBAL_CONFIG_PATH, GLOBAL_CONFIG_PATH);
		}
		Data global = loadOrCreate(GLOBAL_CONFIG_PATH, Data::defaults).sanitized();

		if ("per_world".equalsIgnoreCase(global.configScope)) {
			Path worldConfigDir = server.getWorldPath(LevelResource.ROOT).resolve(PER_WORLD_SUBDIR);
			Path worldConfigPath = worldConfigDir.resolve(PER_WORLD_FILE_NAME);
			Path legacyWorldConfigDir = server.getWorldPath(LevelResource.ROOT).resolve(LEGACY_PER_WORLD_SUBDIR);
			Path legacyWorldConfigPath = legacyWorldConfigDir.resolve(PER_WORLD_FILE_NAME);
			try {
				if (!Files.exists(worldConfigPath)) {
					Files.createDirectories(worldConfigDir);
					if (migrateLegacy(legacyWorldConfigPath, worldConfigPath)) {
						LOGGER.info("[SmartEndertomControl] Migrated this world's old Smart Phantom Control "
								+ "config {} -> {}", legacyWorldConfigPath, worldConfigPath);
						try {
							Files.deleteIfExists(legacyWorldConfigDir);
						} catch (IOException ignored) {
							// Non-empty (e.g. other leftover files) - harmless to leave it behind.
						}
					} else {
						save(worldConfigPath, global.copyTuningOnly());
						LOGGER.info("[SmartEndertomControl] First run for this world under per-world scope - "
								+ "created {} as a copy of the global config", worldConfigPath);
					}
				}
				active = loadOrCreate(worldConfigPath, () -> global).sanitized();
				activePath = worldConfigPath;
				LOGGER.info("[SmartEndertomControl] Using PER-WORLD config: {} (thresholdTicks={}, tiers={})",
						worldConfigPath, active.thresholdTicks, active.tiers.size());
			} catch (IOException e) {
				LOGGER.error("[SmartEndertomControl] Failed to set up per-world config at {}, "
						+ "falling back to the global config for this session", worldConfigPath, e);
				active = global;
				activePath = GLOBAL_CONFIG_PATH;
			}
		} else {
			active = global;
			activePath = GLOBAL_CONFIG_PATH;
			LOGGER.info("[SmartEndertomControl] Using GLOBAL config: {} (thresholdTicks={}, tiers={})",
					GLOBAL_CONFIG_PATH, active.thresholdTicks, active.tiers.size());
		}
	}

	private static Data loadOrCreate(Path path, java.util.function.Supplier<Data> fallback) {
		try {
			if (!Files.exists(path)) {
				save(path, fallback.get());
			}
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				return loaded != null ? loaded.sanitized() : fallback.get().sanitized();
			}
		} catch (IOException e) {
			LOGGER.error("[SmartEndertomControl] Failed to load {}, falling back to defaults", path, e);
			return fallback.get().sanitized();
		}
	}

	private static void save(Path path, Data value) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(value, writer);
		}
	}

	/**
	 * One-time upgrade path from this mod's pre-rename ("Smart Phantom
	 * Control") config file: if newPath doesn't exist yet but legacyPath does,
	 * reads legacyPath (any new A0.3+ fields simply take their Java-side
	 * defaults, same as any other old config file), writes it out at newPath,
	 * and deletes legacyPath. No-op (returns false) once newPath exists, so
	 * this only ever runs once per global/per-world file.
	 */
	private static boolean migrateLegacy(Path legacyPath, Path newPath) {
		if (Files.exists(newPath) || !Files.exists(legacyPath)) {
			return false;
		}
		try {
			Data migrated;
			try (Reader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				migrated = (loaded != null ? loaded : Data.defaults()).sanitized();
			}
			save(newPath, migrated);
			Files.delete(legacyPath);
			return true;
		} catch (IOException e) {
			LOGGER.error("[SmartEndertomControl] Failed to migrate legacy config {}, "
					+ "leaving it in place and starting fresh at {}", legacyPath, newPath, e);
			return false;
		}
	}

	public static final class Data {
		/** "global" or "per_world" - see docs/GUIDE.md. */
		public String configScope = "global";
		public int thresholdTicks = 72000;
		public List<NightTier> tiers = defaultTiers();
		/** Log every insomnia roll to the server console. Off by default. */
		public boolean logToConsole = false;
		/** Log every insomnia roll to the affected player's chat. Off by default. */
		public boolean logToChat = false;
		/** Master switch for the two fields below - see docs/GUIDE.md. */
		public boolean neutralUntilEligible = false;
		/** Only matters if neutralUntilEligible=true - see docs/GUIDE.md. */
		public boolean dropTargetOnceIneligible = false;
		/** Only matters if neutralUntilEligible=true - see docs/GUIDE.md. */
		public boolean retaliateWhenAttacked = false;
		/** Master switch for maxGroupSizeC - see docs/GUIDE.md. */
		public boolean useCustomGroupSize = false;
		/** Only matters if useCustomGroupSize=true - flat group-size cap, replacing tiers/vanilla. */
		public int maxGroupSizeC = 3;
		/** Master switch for successCeiling - see docs/GUIDE.md. */
		public boolean useSuccessCeiling = false;
		/** Only matters if useSuccessCeiling=true - hard cap on spawn chance regardless of night/tier. */
		public double successCeiling = 0.5;

		/** Master switch for all Enderman block-griefing tuning below - see docs/GUIDE.md. */
		public boolean endermanBlockChange = false;
		/** Chance per tick an Enderman successfully picks up a carriable block. Vanilla default is much higher. */
		public double endermanPickupChance = 0.05;
		/** Chance per tick a block-carrying Enderman successfully places its block. Vanilla default is much higher. */
		public double endermanPlaceChance = 0.0005;
		/** Master switch for the second pickup roll against problematicBlock - see docs/GUIDE.md. */
		public boolean gateProblematicBlocks = false;
		/** Block IDs subject to the second, harder pickup roll when gateProblematicBlocks=true. */
		public List<String> problematicBlock = defaultProblematicBlocks();
		/** Only matters if gateProblematicBlocks=true - chance a problematic-block pickup is still allowed through. */
		public double problematicBlockChance = 0.4;

		private static Data defaults() {
			return new Data();
		}

		private static List<String> defaultProblematicBlocks() {
			List<String> blocks = new ArrayList<>();
			blocks.add("minecraft:cactus");
			blocks.add("minecraft:crimson_roots");
			blocks.add("minecraft:warped_roots");
			blocks.add("minecraft:red_mushroom");
			blocks.add("minecraft:brown_mushroom");
			return blocks;
		}

		private static List<NightTier> defaultTiers() {
			List<NightTier> tiers = new ArrayList<>();
			tiers.add(new NightTier(0.10, 1));
			tiers.add(new NightTier(0.30, 2));
			tiers.add(new NightTier(0.40, 2));
			tiers.add(new NightTier(0.50, 3));
			return tiers;
		}

		private Data copyTuningOnly() {
			Data copy = new Data();
			copy.thresholdTicks = this.thresholdTicks;
			copy.tiers = new ArrayList<>(this.tiers);
			copy.logToConsole = this.logToConsole;
			copy.logToChat = this.logToChat;
			copy.neutralUntilEligible = this.neutralUntilEligible;
			copy.dropTargetOnceIneligible = this.dropTargetOnceIneligible;
			copy.retaliateWhenAttacked = this.retaliateWhenAttacked;
			copy.useCustomGroupSize = this.useCustomGroupSize;
			copy.maxGroupSizeC = this.maxGroupSizeC;
			copy.useSuccessCeiling = this.useSuccessCeiling;
			copy.successCeiling = this.successCeiling;
			copy.endermanBlockChange = this.endermanBlockChange;
			copy.endermanPickupChance = this.endermanPickupChance;
			copy.endermanPlaceChance = this.endermanPlaceChance;
			copy.gateProblematicBlocks = this.gateProblematicBlocks;
			copy.problematicBlock = new ArrayList<>(this.problematicBlock);
			copy.problematicBlockChance = this.problematicBlockChance;
			return copy;
		}

		Data sanitized() {
			if (thresholdTicks < 1) {
				thresholdTicks = 1;
			}
			if (tiers == null) {
				tiers = new ArrayList<>();
			}
			if (configScope == null
					|| (!configScope.equalsIgnoreCase("global") && !configScope.equalsIgnoreCase("per_world"))) {
				LOGGER.warn("[SmartEndertomControl] Invalid configScope '{}', falling back to 'global'", configScope);
				configScope = "global";
			}
			if (maxGroupSizeC < 1) {
				maxGroupSizeC = 1;
			}
			successCeiling = clamp01(successCeiling);
			endermanPickupChance = clamp01(endermanPickupChance);
			endermanPlaceChance = clamp01(endermanPlaceChance);
			problematicBlockChance = clamp01(problematicBlockChance);
			if (problematicBlock == null) {
				problematicBlock = new ArrayList<>();
			}
			return this;
		}

		private static double clamp01(double value) {
			if (value < 0) {
				return 0;
			}
			if (value > 1) {
				return 1;
			}
			return value;
		}
	}

	public static final class NightTier {
		/** Max spawn-chance for this night. -1 = uncapped. */
		public double chanceCap = -1;
		/** Max group size for this night. -1 = vanilla difficulty-based. */
		public int maxGroupSize = -1;

		public NightTier() {
		}

		public NightTier(double chanceCap, int maxGroupSize) {
			this.chanceCap = chanceCap;
			this.maxGroupSize = maxGroupSize;
		}
	}
}
