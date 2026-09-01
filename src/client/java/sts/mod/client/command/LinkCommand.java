package sts.mod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import sts.mod.client.armoury.ArmouryTracker;

public final class LinkCommand {
	private LinkCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(LinkCommand::registerCommand);
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ignored) {
		dispatcher.register(
			ClientCommandManager.literal("stsmod")
				.then(ClientCommandManager.literal("link")
					.executes(context -> {
						context.getSource().sendFeedback(Component.literal("Requesting a link for your Minecraft profile..."));
						ArmouryTracker.linkAccount();
						return 1;
					}))
		);
	}
}
