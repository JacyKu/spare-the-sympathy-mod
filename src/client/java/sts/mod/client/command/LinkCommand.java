package sts.mod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import sts.mod.client.armoury.ArmouryTracker;
import sts.mod.client.view.ViewedPlayers;

public final class LinkCommand {
	private LinkCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(LinkCommand::registerCommand);
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ignored) {
		dispatcher.register(
			ClientCommandManager.literal("sts")
				.then(ClientCommandManager.literal("link")
					.executes(context -> {
						context.getSource().sendFeedback(Component.literal("Requesting a link for your Minecraft profile..."));
						ArmouryTracker.linkAccount();
						return 1;
					}))
				.then(ClientCommandManager.literal("upload")
					.executes(context -> {
						UploadCommand.uploadHeldItem();
						return 1;
					}))
				.then(ClientCommandManager.literal("export")
					.then(ClientCommandManager.argument("player", StringArgumentType.word())
						.suggests((context, builder) -> {
							for (String name : ViewedPlayers.names()) {
								builder.suggest(name);
							}
							return builder.buildFuture();
						})
						.then(ClientCommandManager.argument("build_name", StringArgumentType.greedyString())
							.executes(context -> {
								ViewedPlayers.export(
									StringArgumentType.getString(context, "player"),
									StringArgumentType.getString(context, "build_name")
								);
								return 1;
							}))))
		);
	}
}
