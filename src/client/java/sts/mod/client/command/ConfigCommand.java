package sts.mod.client.command;

import com.mojang.brigadier.CommandDispatcher;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.CommandBuildContext;
import sts.mod.config.StsModConfig;

public final class ConfigCommand {
	private ConfigCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(ConfigCommand::registerCommand);
	}

	private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext ignored) {
		dispatcher.register(
			ClientCommandManager.literal("stsmod")
				.then(ClientCommandManager.literal("config")
					.executes(context -> {
						Minecraft mc = Minecraft.getInstance();
						Screen screen = AutoConfig.getConfigScreen(StsModConfig.class, mc.screen).get();
						mc.setScreen(screen);
						return 1;
					}))
		);
	}
}