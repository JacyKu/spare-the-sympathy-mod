package sts.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import sts.mod.client.command.DumpCommand;
import sts.mod.client.dump.DumpRunner;

public class SpareTheSympathyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DumpCommand.register();
		HudRenderCallback.EVENT.register((graphics, tickDelta) -> DumpRunner.onHudRender());
	}
}
