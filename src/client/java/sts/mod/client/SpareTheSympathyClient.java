package sts.mod.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import sts.mod.api.StsConfig;
import sts.mod.client.armoury.ArmouryOverlay;
import sts.mod.client.armoury.ArmouryTracker;
import sts.mod.client.command.ConfigCommand;
import sts.mod.client.command.DumpCommand;
import sts.mod.client.command.LinkCommand;
import sts.mod.client.dump.DumpRunner;
import sts.mod.client.view.ViewedPlayersTracker;
import sts.mod.config.StsModConfig;

public class SpareTheSympathyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AutoConfig.register(StsModConfig.class, GsonConfigSerializer::new);
		StsConfig.load();
		DumpCommand.register();
		LinkCommand.register();
		ConfigCommand.register();
		HudRenderCallback.EVENT.register((graphics, tickDelta) -> DumpRunner.onHudRender());
		// The armoury buttons render via ContainerScreenMixin at the RETURN of
		// ContainerScreen.render, i.e. after slot contents and item tooltips.
		ClientTickEvents.END_CLIENT_TICK.register(client -> ArmouryTracker.onClientTick());
		// Caches other players' builds while their /ps, /pa or /vc GUIs are open.
		ClientTickEvents.END_CLIENT_TICK.register(client -> ViewedPlayersTracker.onClientTick());
	}
}
