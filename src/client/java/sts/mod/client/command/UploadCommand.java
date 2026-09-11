package sts.mod.client.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import sts.mod.api.ItemUploader;
import sts.mod.api.StsApiClient;
import sts.mod.client.armoury.ArmouryTracker;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * `/sts upload_item` - uploads the held item to the site as a custom item on
 * player's linked account. Items the site already knows still upload fine
 * (duplicates are skipped server-side); this is mainly for unreleased/event
 * items that aren't in the item database.
 */
public final class UploadCommand {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "sts-item-upload");
		thread.setDaemon(true);
		return thread;
	});

	private UploadCommand() {
	}

	public static void uploadHeldItem() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.isEmpty()) {
			ArmouryTracker.showMessage("Hold the item you want to upload, then run /sts upload_item.");
			return;
		}
		if (mc.getUser() == null || mc.getUser().getProfileId() == null) {
			ArmouryTracker.showMessage("No Minecraft profile available - cannot upload.");
			return;
		}

		JsonObject payload = ItemUploader.buildPayload(stack);
		String name = payload.get("name").getAsString();
		JsonArray items = new JsonArray();
		items.add(payload);
		String uuid = mc.getUser().getProfileId().toString();

		ArmouryTracker.showMessage("Uploading \"" + name + "\"...");
		EXECUTOR.execute(() -> {
			String message;
			try {
				StsApiClient.UploadResult result = StsApiClient.uploadItems(uuid, items);
				if (!result.created().isEmpty()) {
					message = "Uploaded \"" + name + "\" to your custom items.";
				} else if (!result.skipped().isEmpty()) {
					message = "\"" + name + "\" is already in your custom items.";
				} else {
					message = "Nothing uploaded for \"" + name + "\".";
				}
			} catch (IOException e) {
				String error = e.getMessage() == null ? "" : e.getMessage();
				if (error.contains("not linked")) {
					message = "Link your account first: run /sts link and confirm in the browser.";
				} else {
					message = "Upload failed: " + error;
				}
			}
			final String feedback = message;
			mc.execute(() -> ArmouryTracker.showMessage(feedback));
		});
	}
}
