package eu.stefanbraun612.smartendertomcontrol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class SmartEndertomControl implements ModInitializer {
	public static final String MOD_ID = "smartendertomcontrol";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// No MinecraftServer/world exists yet at mod-init time (needed to resolve a
		// per-world config path), so the actual config load happens once a server
		// (including SP's embedded one) starts, not here.
		ServerLifecycleEvents.SERVER_STARTING.register(SmartEndertomConfig::load);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("endertomtuner")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("reload").executes(ctx -> {
							SmartEndertomConfig.load(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(
									() -> Component.literal("[SmartEndertomControl] Config reloaded."), true);
							return 1;
						}))
						.then(Commands.literal("edit").executes(ctx -> {
							MinecraftServer server = ctx.getSource().getServer();
							Path path = SmartEndertomConfig.getActivePath();
							if (!server.isSingleplayer()) {
								ctx.getSource().sendFailure(Component.literal(
										"[SmartEndertomControl] /endertomtuner edit only works in Singleplayer. "
												+ "Edit the file directly on the server: " + path));
								return 0;
							}
							try {
								openInFileEditor(path);
								ctx.getSource().sendSuccess(
										() -> Component.literal("[SmartEndertomControl] Opening " + path), true);
								return 1;
							} catch (IOException e) {
								LOGGER.error("[SmartEndertomControl] Failed to open {}", path, e);
								ctx.getSource().sendFailure(Component.literal(
										"[SmartEndertomControl] Failed to open the file - edit it manually: " + path));
								return 0;
							}
						}))));
	}

	/**
	 * Many Minecraft launchers run the client JVM in AWT headless mode (to avoid
	 * AWT/LWJGL window conflicts), which makes java.awt.Desktop entirely
	 * unavailable even on a normal desktop session. Falls back to shelling out
	 * to the OS's own "open with default application" mechanism instead - the
	 * same thing double-clicking the file does, no AWT involved.
	 */
	private static void openInFileEditor(Path path) throws IOException {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			Desktop.getDesktop().open(path.toFile());
			return;
		}

		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		ProcessBuilder processBuilder;
		if (os.contains("win")) {
			processBuilder = new ProcessBuilder("cmd.exe", "/c", "start", "", path.toString());
		} else if (os.contains("mac")) {
			processBuilder = new ProcessBuilder("open", path.toString());
		} else {
			processBuilder = new ProcessBuilder("xdg-open", path.toString());
		}
		processBuilder.start();
	}
}
