package sts.mod.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sts.mod.client.view.ViewedPlayers;

/**
 * Captures outgoing chat commands so the viewed-player cache knows who
 * {@code /ps}, {@code /pa} and {@code /vc} were about. Those GUIs' titles
 * (except the charms one) don't name the player, so the command argument is
 * the only reliable source.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "sendCommand", at = @At("HEAD"))
	private void sparethesympathy$captureCommand(String command, CallbackInfo ci) {
		ViewedPlayers.onCommand(command);
	}

	@Inject(method = "sendUnsignedCommand", at = @At("HEAD"))
	private void sparethesympathy$captureUnsignedCommand(String command, CallbackInfoReturnable<Boolean> cir) {
		ViewedPlayers.onCommand(command);
	}
}
