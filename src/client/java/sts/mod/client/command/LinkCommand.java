package sts.mod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import sts.mod.client.armoury.ArmouryTracker;
import sts.mod.client.view.ViewedPlayers;

public final class LinkCommand {
	private static final String EXPORT_USAGE = "/sts export_build <player> <build name>";
	private static final String UPLOAD_USAGE = "/sts upload_build <player> <build name>";

	private LinkCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(LinkCommand::registerCommand);
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ignored) {
		dispatcher.register(
			ClientCommandManager.literal("sts")
				.executes(context -> {
					showHelp(context.getSource());
					return 1;
				})
				.then(ClientCommandManager.literal("help")
					.executes(context -> {
						showHelp(context.getSource());
						return 1;
					}))
				.then(ClientCommandManager.literal("link")
					.executes(context -> {
						context.getSource().sendFeedback(Component.literal("Requesting a link for your Minecraft profile..."));
						ArmouryTracker.linkAccount();
						return 1;
					}))
				.then(ClientCommandManager.literal("upload_item")
					.executes(context -> {
						UploadCommand.uploadHeldItem();
						return 1;
					}))
				.then(exportCommand("export_build", EXPORT_USAGE,
					"Generate a shareable builder link for a viewed player's cached build.", ViewedPlayers::export))
				.then(exportCommand("upload_build", UPLOAD_USAGE,
					"Upload a viewed player's cached build to your account.", ViewedPlayers::upload))
		);
	}

	/**
	 * {@code /sts <name> <player> <build name>} - exports/uploads a cached
	 * viewed player's build under the given name (greedy, so it may contain
	 * spaces). Running it with missing arguments prints the usage instead of
	 * Brigadier's generic error.
	 */
	private static LiteralArgumentBuilder<FabricClientCommandSource> exportCommand(
		String name,
		String usage,
		String description,
		BiConsumer<String, String> action
	) {
		return ClientCommandManager.literal(name)
			.executes(context -> {
				showUsage(context.getSource(), usage, description);
				return 1;
			})
			.then(ClientCommandManager.argument("player", StringArgumentType.word())
				.suggests((context, builder) -> {
					for (String cached : ViewedPlayers.names()) {
						builder.suggest(cached);
					}
					return builder.buildFuture();
				})
				.executes(context -> {
					showUsage(context.getSource(), usage, description);
					return 1;
				})
				.then(ClientCommandManager.argument("build_name", StringArgumentType.greedyString())
					.executes(context -> {
						action.accept(
							StringArgumentType.getString(context, "player"),
							StringArgumentType.getString(context, "build_name")
						);
						return 1;
					})));
	}

	private static void showHelp(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Spare the Sympathy commands").withStyle(ChatFormatting.GOLD));
		source.sendFeedback(commandLine("/sts help", "Show this command list."));
		source.sendFeedback(commandLine("/sts link", "Link your Minecraft profile to your Discord account."));
		source.sendFeedback(commandLine("/sts upload_item", "Upload the held item as a custom item on your linked account."));
		source.sendFeedback(commandLine(EXPORT_USAGE, "Generate a shareable builder link for a viewed player's cached build."));
		source.sendFeedback(commandLine(UPLOAD_USAGE, "Upload a viewed player's cached build to your account."));
		source.sendFeedback(
			Component.literal("View a player with /ps, /pa or /vc first so their build is cached.")
				.withStyle(ChatFormatting.DARK_GRAY)
		);
	}

	private static void showUsage(FabricClientCommandSource source, String usage, String description) {
		source.sendFeedback(Component.literal("Usage: " + usage).withStyle(ChatFormatting.YELLOW));
		source.sendFeedback(Component.literal(description).withStyle(ChatFormatting.GRAY));
	}

	/** One help line: clickable command usage plus a grey description. */
	private static Component commandLine(String usage, String description) {
		String[] parts = usage.split(" ");
		String suggestion = (parts.length >= 2 ? parts[0] + " " + parts[1] : usage) + " ";
		return Component.literal(usage)
			.withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to use this command"))))
			.append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY));
	}
}
