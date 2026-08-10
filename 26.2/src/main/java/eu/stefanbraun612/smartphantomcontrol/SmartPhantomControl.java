package eu.stefanbraun612.smartphantomcontrol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartPhantomControl implements ModInitializer {
	public static final String MOD_ID = "smartphantomcontrol";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// No MinecraftServer/world exists yet at mod-init time (needed to resolve a
		// per-world config path), so the actual config load happens once a server
		// (including SP's embedded one) starts, not here.
		ServerLifecycleEvents.SERVER_STARTING.register(PhantomTuningConfig::load);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("phantomtuner")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("reload").executes(ctx -> {
							PhantomTuningConfig.load(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(
									() -> Component.literal("[SmartPhantomControl] Config reloaded."), true);
							return 1;
						}))));
	}
}
