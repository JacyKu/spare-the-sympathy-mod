package sts.mod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import sts.mod.client.dump.DumpRunner;

import java.nio.file.Path;

public final class DumpCommand {
	private DumpCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(DumpCommand::registerCommand);
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ignored) {
		dispatcher.register(
			ClientCommandManager.literal("sts")
				.then(ClientCommandManager.literal("dump")
					.executes(context -> runDump(context.getSource(), null))
					.then(ClientCommandManager.argument("dir", StringArgumentType.greedyString())
						.executes(context -> runDump(context.getSource(), StringArgumentType.getString(context, "dir")))))
		);
	}

	private static int runDump(FabricClientCommandSource source, String dirArgument) {
		Path outputDir = dirArgument == null ? DumpRunner.defaultOutputDir() : Path.of(dirArgument);
		source.sendFeedback(Component.literal("Starting Monumenta icon dump into " + outputDir));
		DumpRunner.start(source.getClient(), outputDir, source::sendFeedback);
		return 1;
	}
}
