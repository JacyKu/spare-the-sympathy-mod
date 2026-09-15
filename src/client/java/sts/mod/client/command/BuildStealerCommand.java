package sts.mod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import sts.mod.api.StsConfig;
import sts.mod.client.view.ViewedPlayers;

/**
 * {@code /buildstealer2000 <player>} - shorthand for {@code /ps <player>}:
 * opens the player's stats GUI and marks them as the pending /ps target, so
 * the normal caching flow (and {@code /sts export_build} / {@code upload_build})
 * picks the build up from there.
 */
public final class BuildStealerCommand {
	// Optional taunt: only this alias schedules it, and only once enabled in
	// the config. Sent a second after /ps so both commands don't hit the
	// server in the same tick.
	private static final int TAUNT_DELAY_TICKS = 20;
	private static String tauntTarget;
	private static String tauntText;
	private static int tauntTicks;

	private BuildStealerCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(BuildStealerCommand::registerCommand);
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ignored) {
		dispatcher.register(
			ClientCommandManager.literal("buildstealer2000")
				.executes(context -> {
					showUsage(context.getSource());
					return 1;
				})
				.then(ClientCommandManager.argument("player", StringArgumentType.word())
					.suggests((context, builder) -> {
						Minecraft mc = Minecraft.getInstance();
						if (mc.getConnection() != null) {
							for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
								builder.suggest(info.getProfile().getName());
							}
						}
						// Players already viewed this session (they may be
						// offline now) so the build can be re-opened/exported.
						for (String cached : ViewedPlayers.names()) {
							builder.suggest(cached);
						}
						return builder.buildFuture();
					})
					.executes(context -> {
						openStats(context.getSource(), StringArgumentType.getString(context, "player"));
						return 1;
					}))
		);
	}

	private static void openStats(FabricClientCommandSource source, String player) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.connection == null) {
			source.sendError(Component.literal("Not connected to a server."));
			return;
		}
		// Mark the /ps target first: client commands never reach the server,
		// so the outgoing-command capture in ClientPacketListenerMixin doesn't
		// see this alias.
		ViewedPlayers.onCommand("ps " + player);
		// Now run the real /ps so its GUI opens (the mixin captures this one
		// too, which is harmless - the pending target is the same).
		mc.player.connection.sendCommand("ps " + player);
		// The taunt is an explicit /buildstealer2000 feature: plain /ps never
		// reaches this code.
		if (StsConfig.buildStealerMessageEnabled()) {
			scheduleTaunt(player, StsConfig.buildStealerMessage());
		}
		source.sendFeedback(
			Component.literal("Opening /ps " + player + "...").withStyle(ChatFormatting.GRAY)
		);
	}

	/** Queues the /msg taunt; {@link #onClientTick()} sends it after the delay. */
	private static void scheduleTaunt(String player, String message) {
		tauntTarget = player;
		tauntText = message;
		tauntTicks = TAUNT_DELAY_TICKS;
	}

	/** Called every client tick: sends a scheduled taunt once its delay passes. */
	public static void onClientTick() {
		if (tauntTarget == null) {
			return;
		}
		if (tauntTicks > 0) {
			tauntTicks--;
			return;
		}
		String target = tauntTarget;
		String text = tauntText;
		tauntTarget = null;
		tauntText = null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.connection != null) {
			mc.player.connection.sendCommand("msg " + target + " " + text);
		}
	}

	private static void showUsage(FabricClientCommandSource source) {
		source.sendFeedback(
			Component.literal("Usage: /buildstealer2000 <player>").withStyle(ChatFormatting.YELLOW)
		);
		source.sendFeedback(
			Component.literal("Opens /ps for the player; then use /sts export_build or /sts upload_build.")
				.withStyle(ChatFormatting.GRAY)
		);
	}
}
