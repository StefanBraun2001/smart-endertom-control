package eu.stefanbraun612.smartphantomcontrol;

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

/**
 * Loads/holds the active tuning data. The global file
 * (config/smartphantomcontrol.json) always exists and its "configScope"
 * field decides where the actual tuning values come from:
 * - "global": the global file's own fields are used directly.
 * - "per_world": <world-save>/smartphantomcontrol/config.json is used
 *   instead, created as a copy of the global file's fields the first time
 *   a given world is loaded under this scope. Switching back to "global"
 *   later leaves that per-world file untouched on disk (not deleted, not
 *   read) so switching back to "per_world" resumes it unchanged.
 * Re-resolved on every server start and on "/phantomtuner reload" - not
 * hot-reloaded automatically otherwise.
 */
public final class PhantomTuningConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("smartphantomcontrol");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path GLOBAL_CONFIG_PATH =
			FabricLoader.getInstance().getConfigDir().resolve("smartphantomcontrol.json");
	private static final String PER_WORLD_SUBDIR = "smartphantomcontrol";
	private static final String PER_WORLD_FILE_NAME = "config.json";

	private static volatile Data active = Data.defaults();
	private static volatile Path activePath = GLOBAL_CONFIG_PATH;

	private PhantomTuningConfig() {
	}

	public static Data get() {
		return active;
	}

	/** The config file actually driving current behavior (global or per-world, whichever is active). */
	public static Path getActivePath() {
		return activePath;
	}

	public static synchronized void load(MinecraftServer server) {
		Data global = loadOrCreate(GLOBAL_CONFIG_PATH, Data::defaults).sanitized();

		if ("per_world".equalsIgnoreCase(global.configScope)) {
			Path worldConfigDir = server.getWorldPath(LevelResource.ROOT).resolve(PER_WORLD_SUBDIR);
			Path worldConfigPath = worldConfigDir.resolve(PER_WORLD_FILE_NAME);
			try {
				if (!Files.exists(worldConfigPath)) {
					Files.createDirectories(worldConfigDir);
					save(worldConfigPath, global.copyTuningOnly());
					LOGGER.info("[SmartPhantomControl] First run for this world under per-world scope - "
							+ "created {} as a copy of the global config", worldConfigPath);
				}
				active = loadOrCreate(worldConfigPath, () -> global).sanitized();
				activePath = worldConfigPath;
				LOGGER.info("[SmartPhantomControl] Using PER-WORLD config: {} (thresholdTicks={}, tiers={})",
						worldConfigPath, active.thresholdTicks, active.tiers.size());
			} catch (IOException e) {
				LOGGER.error("[SmartPhantomControl] Failed to set up per-world config at {}, "
						+ "falling back to the global config for this session", worldConfigPath, e);
				active = global;
				activePath = GLOBAL_CONFIG_PATH;
			}
		} else {
			active = global;
			activePath = GLOBAL_CONFIG_PATH;
			LOGGER.info("[SmartPhantomControl] Using GLOBAL config: {} (thresholdTicks={}, tiers={})",
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
			LOGGER.error("[SmartPhantomControl] Failed to load {}, falling back to defaults", path, e);
			return fallback.get().sanitized();
		}
	}

	private static void save(Path path, Data value) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(value, writer);
		}
	}

	public static final class Data {
		/** "global" (default) uses this file's own fields; "per_world" uses a per-world copy instead - see class doc. */
		public String configScope = "global";
		public int thresholdTicks = 72000;
		public List<NightTier> tiers = defaultTiers();
		/** Testing aid: writes a line to the server log on every insomnia roll (chance, cap, group size). Off by default. */
		public boolean logToConsole = false;
		/** Testing aid: sends the same line as an in-game chat message to the affected player on every insomnia roll. Off by default. */
		public boolean logToChat = false;
		/** Master switch for the three fields below. Off by default = pure vanilla Phantom AI, the other two are inert regardless of their value. */
		public boolean neutralUntilEligible = false;
		/** Only meaningful if neutralUntilEligible=true. false = a Phantom keeps fighting a target even if that player sleeps mid-fight; true = it re-checks every tick and disengages the instant the target becomes ineligible (except a target it's actively retaliating against - see retaliateWhenAttacked). */
		public boolean dropTargetOnceIneligible = false;
		/** Only meaningful if neutralUntilEligible=true. If true, a Phantom that gets hit by an otherwise-ineligible player and has no current target will fight back - restores vanilla's usual mob retaliation behavior, which Phantoms normally lack entirely. Only applies to Phantoms spawned after this is enabled (goal registration happens once, at spawn). */
		public boolean retaliateWhenAttacked = false;

		private static Data defaults() {
			return new Data();
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
				LOGGER.warn("[SmartPhantomControl] Invalid configScope '{}', falling back to 'global'", configScope);
				configScope = "global";
			}
			return this;
		}
	}

	public static final class NightTier {
		/** Upper bound on the spawn-chance roll for this night. -1 = no cap (use the vanilla-formula value as-is). */
		public double chanceCap = -1;
		/** Upper bound on how many Phantoms can spawn per successful roll this night. -1 = no cap (use vanilla difficulty-based group size). */
		public int maxGroupSize = -1;

		public NightTier() {
		}

		public NightTier(double chanceCap, int maxGroupSize) {
			this.chanceCap = chanceCap;
			this.maxGroupSize = maxGroupSize;
		}
	}
}
