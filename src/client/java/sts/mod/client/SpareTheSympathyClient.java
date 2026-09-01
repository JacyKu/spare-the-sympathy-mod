package sts.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import sts.mod.api.StsConfig;
import sts.mod.client.armoury.ArmouryOverlay;
import sts.mod.client.armoury.ArmouryTracker;
import sts.mod.client.command.DumpCommand;
import sts.mod.client.command.LinkCommand;
import sts.mod.client.dump.DumpRunner;

public class SpareTheSympathyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		StsConfig.load();
		DumpCommand.register();
		LinkCommand.register();
		HudRenderCallback.EVENT.register((graphics, tickDelta) -> DumpRunner.onHudRender());
		// The armoury buttons render via ContainerScreenMixin at the RETURN of
		// ContainerScreen.render, i.e. after slot contents and item tooltips.
		ClientTickEvents.END_CLIENT_TICK.register(client -> ArmouryTracker.onClientTick());
	}
}
