package sts.mod.client.dump;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import sts.mod.SpareTheSympathy;
import sts.mod.api.MonumentaItemDefinition;
import sts.mod.api.MonumentaItemRepository;
import sts.mod.api.MonumentaStackFactory;
import sts.mod.client.render.AnimatedIconRenderer;
import sts.mod.client.render.FrameItemModel;
import sts.mod.client.render.IconRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Fetches the Monumenta items API (cached), builds a stack per item, renders
 * each as an inventory GUI icon, captures every animation frame with its
 * mcmeta dwell, and writes the sheets.
 *
 * All rendering happens inside the HUD render callback so the game's full
 * render pipeline state (projection, shaders, atlas) is active — the same
 * context inventory and HUD items are drawn in.
 */
public final class DumpRunner {
	private static final ExecutorService FETCH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SpareTheSympathy-Fetch");
		thread.setDaemon(true);
		return thread;
	});
	private static final long HEAD_RETRY_DELAY_MILLIS = 3_000L;

	public record RenderedItem(String key, List<int[]> frames, List<Integer> dwells, String texturePath, boolean animated, boolean retry) {
	}

	private static final class PendingDump {
		final Path outputDir;
		final List<MonumentaItemDefinition> items;
		final Consumer<Component> feedback;
		final List<RenderedItem> rendered = new ArrayList<>();

		PendingDump(Path outputDir, List<MonumentaItemDefinition> items, Consumer<Component> feedback) {
			this.outputDir = outputDir;
			this.items = items;
			this.feedback = feedback;
		}
	}

	private static final class PendingRetry {
		final PendingDump dump;
		final Set<String> retryKeys;

		PendingRetry(PendingDump dump, Set<String> retryKeys) {
			this.dump = dump;
			this.retryKeys = retryKeys;
		}
	}

	private static PendingDump PENDING;
	private static PendingRetry RETRY_PENDING;

	private DumpRunner() {
	}

	/** Nearest-neighbor upscale; lifts 16px GUI captures into 64px cells. */
	public static int[] upscaleNearest(int[] source, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
		int[] result = new int[targetWidth * targetHeight];
		for (int y = 0; y < targetHeight; y++) {
			int srcY = y * sourceHeight / targetHeight;
			for (int x = 0; x < targetWidth; x++) {
				int srcX = x * sourceWidth / targetWidth;
				result[y * targetWidth + x] = source[srcY * sourceWidth + srcX];
			}
		}
		return result;
	}

	public static Path defaultOutputDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("sparethesympathy");
	}

	public static void start(Minecraft minecraft, Path outputDir, Consumer<Component> feedback) {
		Path resolved = outputDir.toAbsolutePath();
		feedback.accept(Component.literal("Fetching Monumenta items API..."));
		FETCH_EXECUTOR.submit(() -> {
			try {
				List<MonumentaItemDefinition> items = MonumentaItemRepository.getItems();
				if (items.isEmpty()) {
					feedback.accept(Component.literal("No Monumenta items loaded (API unreachable and no cache); aborting."));
					return;
				}
				minecraft.execute(() -> {
					PENDING = new PendingDump(resolved, items, feedback);
					feedback.accept(Component.literal("Rendering " + items.size() + " Monumenta item icons (build 20260824h)..."));
				});
			} catch (Throwable throwable) {
				feedback.accept(Component.literal("Failed to fetch Monumenta items: " + throwable));
				SpareTheSympathy.LOGGER.error("Monumenta items fetch failed", throwable);
			}
		});
	}

	/** Called every frame from the HUD render callback. */
	public static void onHudRender() {
		PendingDump pending = PENDING;
		if (pending != null) {
			PENDING = null;
			renderItems(pending);
		}
		PendingRetry retry = RETRY_PENDING;
		if (retry != null) {
			RETRY_PENDING = null;
			retryHeadsNow(retry);
		}
	}

	private static void renderItems(PendingDump pending) {
		try {
			SpareTheSympathy.LOGGER.info("[dump] build tag 20260826a");
			runSelfTest(pending.outputDir);
			IconRenderer iconRenderer = new IconRenderer();
			List<MonumentaItemDefinition> heads = new ArrayList<>();
			int animatedCount = 0;
			boolean wroteStaticDebug = false;
			boolean wroteAnimDebug = false;

			// Warm-up pass: render every item once, discarded. CIT item models
			// and textures (the Monumenta pack's optifine/cit entries) load
			// asynchronously; the first getModel() for an item bakes the flat
			// FALLBACK (the base item's texture) while the real model loads on
			// a worker thread. By the time the real pass reaches each item,
			// its model has baked, so skinned items capture their true model
			// instead of the flat fallback.
			for (MonumentaItemDefinition definition : pending.items) {
				try {
					iconRenderer.render(cleanStack(MonumentaStackFactory.createStack(definition)));
				} catch (Throwable throwable) {
					SpareTheSympathy.LOGGER.warn("Warm-up render failed for '{}': {}", definition.key(), throwable.toString());
				}
			}

			for (int index = 0; index < pending.items.size(); index++) {
				MonumentaItemDefinition definition = pending.items.get(index);
				ItemStack stack = cleanStack(MonumentaStackFactory.createStack(definition));
				AnimatedIconRenderer.Capture capture;
				boolean missingno = false;
				try {
					var model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
					// Some CIT source models reference texture paths that never
					// resolve (pack/ETF mismatch); drop their missing-texture
					// quads so the captured icon has no purple/black patches.
					missingno = FrameItemModel.containsMissingno(model);
					if (missingno) {
						model = FrameItemModel.withoutMissingno(model);
						SpareTheSympathy.LOGGER.info("[dump] dropped missingno quads for '{}'", definition.key());
					}
					capture = AnimatedIconRenderer.capture(stack, iconRenderer, model);
				} catch (Throwable throwable) {
					SpareTheSympathy.LOGGER.warn("Capture failed for '{}': {}", definition.key(), throwable.toString());
					capture = new AnimatedIconRenderer.Capture(List.of(iconRenderer.render(stack)), List.of(1), null, false);
				}
				if (capture.frames().get(0) != null && opaqueCount(capture.frames().get(0)) == 0) {
					var fallbackModel = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
					int[] forced = iconRenderer.render(stack);
					if (opaqueCount(forced) > 0) {
						List<int[]> forcedFrames = new ArrayList<>(capture.frames().size());
						for (int frame = 0; frame < capture.frames().size(); frame++) {
							forcedFrames.add(forced);
						}
						capture = new AnimatedIconRenderer.Capture(forcedFrames, capture.dwells(), capture.texturePath(), capture.animated());
					}
				}
				if (capture.animated()) {
					animatedCount++;
				}
				// Flag for the settle retry: the CIT model was still baking
				// (mixed geometry across frames) or referenced missing
				// textures. The retry re-captures after a delay, when the
				// async model load has finished.
				boolean retry = missingno || inconsistentFrames(capture.frames());
				pending.rendered.add(new RenderedItem(definition.key(), capture.frames(), capture.dwells(), capture.texturePath(), capture.animated(), retry));
				if (!wroteStaticDebug && !capture.animated()) {
					IconRenderer.writeDebugPng(pending.outputDir.resolve("debug-static.png").toString(), capture.frames().get(0), IconRenderer.CAPTURE_SIZE);
					SpareTheSympathy.LOGGER.info("[dump] static debug: opaque={} firstPixels={}", opaqueCount(capture.frames().get(0)), firstPixels(capture.frames().get(0)));
					wroteStaticDebug = true;
				}
				if (!wroteAnimDebug && capture.animated()) {
					IconRenderer.writeDebugPng(pending.outputDir.resolve("debug-anim-0.png").toString(), capture.frames().get(0), IconRenderer.CAPTURE_SIZE);
					if (capture.frames().size() > 1) {
						IconRenderer.writeDebugPng(pending.outputDir.resolve("debug-anim-1.png").toString(), capture.frames().get(1), IconRenderer.CAPTURE_SIZE);
					}
					SpareTheSympathy.LOGGER.info("[dump] anim debug: {} frames opaque0={} opaque1={}", capture.frames().size(), opaqueCount(capture.frames().get(0)), capture.frames().size() > 1 ? opaqueCount(capture.frames().get(1)) : -1);
					wroteAnimDebug = true;
				}
				if (isPlayerHead(stack)) {
					heads.add(definition);
				}
				if ((index + 1) % 200 == 0 || index + 1 == pending.items.size()) {
					pending.feedback.accept(Component.literal("  rendered " + (index + 1) + "/" + pending.items.size() + " items"));
				}
			}
			SpareTheSympathy.LOGGER.info("Rendered {} icons ({} animated), {} player heads pending retry", pending.rendered.size(), animatedCount, heads.size());
			writeSpriteDiagnostics(pending.outputDir, pending.items);

			Set<String> retryKeys = new HashSet<>(heads.stream().map(MonumentaItemDefinition::key).toList());
			for (RenderedItem item : pending.rendered) {
				if (item.retry()) {
					retryKeys.add(item.key());
				}
			}
			if (retryKeys.isEmpty()) {
				writeOutputs(pending, pending.feedback);
			} else {
				scheduleRetry(pending, retryKeys);
			}
		} catch (Throwable throwable) {
			pending.feedback.accept(Component.literal("Dump failed: " + throwable));
			SpareTheSympathy.LOGGER.error("Icon dump failed", throwable);
		}
	}

	private static void scheduleRetry(PendingDump pending, Set<String> retryKeys) {
		pending.feedback.accept(Component.literal("  waiting 3s for head skins and CIT models to settle, then re-rendering " + retryKeys.size() + " items..."));
		FETCH_EXECUTOR.submit(() -> {
			try {
				Thread.sleep(HEAD_RETRY_DELAY_MILLIS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			Minecraft.getInstance().execute(() -> RETRY_PENDING = new PendingRetry(pending, retryKeys));
		});
	}

	private static void retryHeadsNow(PendingRetry retry) {
		try {
			IconRenderer iconRenderer = new IconRenderer();
			int retried = 0;
			for (int index = 0; index < retry.dump.rendered.size(); index++) {
				RenderedItem item = retry.dump.rendered.get(index);
				if (!retry.retryKeys.contains(item.key())) {
					continue;
				}
				MonumentaItemDefinition definition = retry.dump.items.get(index);
				ItemStack stack = cleanStack(MonumentaStackFactory.createStack(definition));
				AnimatedIconRenderer.Capture capture;
				var model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
				if (FrameItemModel.containsMissingno(model)) {
					model = FrameItemModel.withoutMissingno(model);
				}
				capture = AnimatedIconRenderer.capture(stack, iconRenderer, model);
				retry.dump.rendered.set(index, new RenderedItem(item.key(), capture.frames(), capture.dwells(), capture.texturePath(), capture.animated(), false));
				retried++;
			}
			SpareTheSympathy.LOGGER.info("Retried {} head/settle icons", retried);
		} catch (Throwable throwable) {
			SpareTheSympathy.LOGGER.error("Head icon retry failed", throwable);
		} finally {
			writeOutputs(retry.dump, retry.dump.feedback);
		}
	}

	// An item's animation must keep the same geometry; wildly different
	// opaque bounds between frames mean the CIT model baked mid-capture
	// (frame 0 shows the flat fallback, later frames the real model).
	private static boolean inconsistentFrames(List<int[]> frames) {
		if (frames == null || frames.size() < 2) {
			return false;
		}
		int[] first = opaqueBounds(frames.get(0));
		int[] last = opaqueBounds(frames.get(frames.size() - 1));
		return Math.abs(first[0] - last[0]) >= 8 || Math.abs(first[1] - last[1]) >= 8;
	}

	private static int[] opaqueBounds(int[] pixels) {
		int minX = 64, minY = 64, maxX = -1, maxY = -1;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if ((pixels[y * 64 + x] >>> 24) > 0) {
					if (x < minX) minX = x;
					if (x > maxX) maxX = x;
					if (y < minY) minY = y;
					if (y > maxY) maxY = y;
				}
			}
		}
		return maxX < 0 ? new int[]{0, 0} : new int[]{maxX - minX + 1, maxY - minY + 1};
	}

	private static ItemStack cleanStack(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.getOrCreateTag().remove("Enchantments");
		return copy;
	}

	private static boolean isPlayerHead(ItemStack stack) {
		if (stack.getItem() == Items.PLAYER_HEAD) {
			return true;
		}
		var tag = stack.getTag();
		return tag != null && (tag.contains("SkullOwner") || tag.contains("SkullProfile"));
	}

	private static void writeOutputs(PendingDump pending, Consumer<Component> feedback) {
		try {
			Files.createDirectories(pending.outputDir);
			TextureSheetsWriter.write(pending.outputDir, pending.rendered);
			long animated = pending.rendered.stream().filter(RenderedItem::animated).count();
			feedback.accept(Component.literal("Dump complete: " + pending.rendered.size() + " items (" + animated + " animated) written to " + pending.outputDir));
		} catch (Throwable throwable) {
			feedback.accept(Component.literal("Dump failed while writing: " + throwable));
			SpareTheSympathy.LOGGER.error("Failed to write spritesheet outputs", throwable);
		}
	}

	/** Writes one line per item listing its model sprites and their animation state. */
	private static void writeSpriteDiagnostics(Path outputDir, List<MonumentaItemDefinition> items) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			StringBuilder log = new StringBuilder(items.size() * 100);
			for (MonumentaItemDefinition definition : items) {
				ItemStack stack = cleanStack(MonumentaStackFactory.createStack(definition));
				var model = minecraft.getItemRenderer().getModel(stack, null, null, 0);
				List<String> sprites = new ArrayList<>();
				for (var sprite : AnimatedIconRenderer.allSprites(model)) {
					int frames = 0;
					try {
						frames = (int) sprite.contents().getUniqueFrames().count();
					} catch (RuntimeException exception) {
						frames = -1;
					}
					sprites.add(sprite.contents().name() + "@" + frames);
				}
				log.append(definition.key()).append(" || ").append(String.join(", ", sprites)).append('\n');
			}
			Files.writeString(outputDir.resolve("debug-sprites.log"), log.toString());
			SpareTheSympathy.LOGGER.info("Wrote sprite diagnostics to {}", outputDir.resolve("debug-sprites.log"));
		} catch (Throwable throwable) {
			SpareTheSympathy.LOGGER.warn("Sprite diagnostics failed", throwable);
		}
	}

	private static void runSelfTest(Path outputDir) {
		try {
			IconRenderer renderer = new IconRenderer();
			int[] pixels = renderer.render(new ItemStack(net.minecraft.world.item.Items.DIAMOND));
			IconRenderer.writeDebugPng(outputDir.resolve("debug-selftest.png").toString(), pixels, 16);
			SpareTheSympathy.LOGGER.info("[selftest] diamond opaque={}/{}", opaqueCount(pixels), pixels.length);
		} catch (Throwable throwable) {
			SpareTheSympathy.LOGGER.warn("Self test failed", throwable);
		}
	}

	private static int opaqueCount(int[] pixels) {
		int count = 0;
		for (int pixel : pixels) {
			if ((pixel >>> 24) > 0) {
				count++;
			}
		}
		return count;
	}

	private static String firstPixels(int[] pixels) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < Math.min(8, pixels.length); index++) {
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(String.format("%08x", pixels[index]));
		}
		return builder.toString();
	}
}
