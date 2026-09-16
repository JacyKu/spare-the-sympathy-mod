package sts.mod.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import sts.mod.SpareTheSympathy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures every animation frame of a rendered item icon. Each playback frame
 * is uploaded to the atlas through the sprite's own upload method (the same
 * code the game's ticker uses) and the icon is captured with the plain
 * vanilla GUI render, exactly like the static items.
 */
public final class AnimatedIconRenderer {
	public record Capture(List<int[]> frames, List<Integer> dwells, String texturePath, boolean animated) {
	}

	private static final RandomSource RANDOM = RandomSource.create();

	private AnimatedIconRenderer() {
	}

	public static Capture capture(ItemStack stack, IconRenderer iconRenderer) {
		return capture(stack, iconRenderer, Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0));
	}

	/**
	 * Captures with an explicit baked model (the caller may have wrapped it,
	 * e.g. to drop missing-texture quads). The model is used for every frame;
	 * callers that want late CIT-model bakes picked up should resolve the
	 * model themselves after the textures have settled (see DumpRunner's
	 * warm-up pass).
	 */
	public static Capture capture(ItemStack stack, IconRenderer iconRenderer, BakedModel model) {
		List<TextureAtlasSprite> animatedSprites = animatedSprites(model);
		if (animatedSprites.isEmpty()) {
			return staticCapture(iconRenderer, stack, model);
		}

		TextureAtlasSprite primary = animatedSprites.get(0);
		SpriteContents contents = primary.contents();
		Object primaryTexture = animatedTextureOf(contents);
		if (primaryTexture == null) {
			return staticCapture(iconRenderer, stack, model);
		}
		List<?> playback = animFramesOf(primaryTexture);
		if (playback == null || playback.size() <= 1) {
			return staticCapture(iconRenderer, stack, model);
		}

		// Upload frames for EVERY animated sprite of the model, not just the
		// first: items like Firmament have their animation on multiple faces,
		// and the primary sprite may not be the visible one.
		List<TextureAtlasSprite> sprites = new ArrayList<>();
		List<Object> textures = new ArrayList<>();
		List<List<?>> playbacks = new ArrayList<>();
		for (TextureAtlasSprite sprite : animatedSprites) {
			Object texture = animatedTextureOf(sprite.contents());
			List<?> frames = texture != null ? animFramesOf(texture) : null;
			if (frames != null && frames.size() > 1) {
				sprites.add(sprite);
				textures.add(texture);
				playbacks.add(frames);
			}
		}
		if (textures.isEmpty()) {
			return staticCapture(iconRenderer, stack, model);
		}

		// Flat items (builtin/generated models) render with a silhouette baked
		// from ALL frames at once, so per-frame artwork that moves positionally
		// looks frozen/stray. Rebuild the flat model from each frame's own
		// pixels instead, so the artwork sweeps to its true position.
		boolean flatItem = FrameItemModel.isFlatItem(stack, model);

		try {
			List<int[]> frames = new ArrayList<>(playback.size());
			List<Integer> dwells = new ArrayList<>(playback.size());
			for (int frameIndex = 0; frameIndex < playback.size(); frameIndex++) {
				int[] cells = new int[textures.size()];
				for (int spriteIndex = 0; spriteIndex < textures.size(); spriteIndex++) {
					List<?> spritePlayback = playbacks.get(spriteIndex);
					int cell = frameIndexOf(spritePlayback.get(Math.min(frameIndex, spritePlayback.size() - 1)));
					cells[spriteIndex] = cell;
					uploadFrame(textures.get(spriteIndex), sprites.get(spriteIndex), cell);
				}
				if (flatItem) {
					// Rebuild the flat model from ALL the model's sprites, not
					// just the animated ones: static base layers (e.g. the
					// Mimic's body, the Sketched Bag's base) would otherwise
					// vanish, leaving only the animated overlay. Animated
					// sprites sample their current cell; static ones sample
					// frame 0. Missing-texture placeholders are dropped.
					List<TextureAtlasSprite> frameSprites = new ArrayList<>();
					List<Integer> frameCells = new ArrayList<>();
					for (TextureAtlasSprite sprite : allSprites(model)) {
						if (FrameItemModel.isMissingno(sprite)) {
							continue;
						}
						frameSprites.add(sprite);
						int animatedIndex = sprites.indexOf(sprite);
						frameCells.add(animatedIndex >= 0 ? cells[animatedIndex] : 0);
					}
					int[] cellsForFrame = frameCells.stream().mapToInt(Integer::intValue).toArray();
					BakedModel frameModel = FrameItemModel.forFrame(model, frameSprites, cellsForFrame);
					frames.add(iconRenderer.render(stack, frameModel));
				} else {
					frames.add(iconRenderer.render(stack, model));
				}
				dwells.add(frameTimeOf(playback.get(frameIndex)));
			}
			// Restore the first frame so the in-game animation keeps playing
			// normally after the dump.
			for (int spriteIndex = 0; spriteIndex < textures.size(); spriteIndex++) {
				uploadFrame(textures.get(spriteIndex), sprites.get(spriteIndex), frameIndexOf(playbacks.get(spriteIndex).get(0)));
			}
			String texturePath = primary.contents().name().getNamespace() + "/" + primary.contents().name().getPath();
			return new Capture(frames, dwells, texturePath, true);
		} catch (Throwable throwable) {
			SpareTheSympathy.LOGGER.warn("Animated capture failed for {}, falling back to static", stack, throwable);
			return staticCapture(iconRenderer, stack, model);
		}
	}

	private static Capture staticCapture(IconRenderer iconRenderer, ItemStack stack, BakedModel model) {
		return new Capture(List.of(iconRenderer.render(stack, model)), List.of(1), null, false);
	}

	private static Capture staticCapture(IconRenderer iconRenderer, ItemStack stack) {
		return new Capture(List.of(iconRenderer.render(stack)), List.of(1), null, false);
	}

	private static List<TextureAtlasSprite> animatedSprites(BakedModel model) {
		List<TextureAtlasSprite> sprites = new ArrayList<>();
		for (Direction direction : Direction.values()) {
			for (BakedQuad quad : model.getQuads(null, direction, RANDOM)) {
				TextureAtlasSprite sprite = quad.getSprite();
				if (sprite != null && isAnimated(sprite) && !sprites.contains(sprite)) {
					sprites.add(sprite);
				}
			}
		}
		for (BakedQuad quad : model.getQuads(null, null, RANDOM)) {
			TextureAtlasSprite sprite = quad.getSprite();
			if (sprite != null && isAnimated(sprite) && !sprites.contains(sprite)) {
				sprites.add(sprite);
			}
		}
		return sprites;
	}

	/** All sprites referenced by the model. */
	public static List<TextureAtlasSprite> allSprites(BakedModel model) {
		List<TextureAtlasSprite> sprites = new ArrayList<>();
		for (Direction direction : Direction.values()) {
			for (BakedQuad quad : model.getQuads(null, direction, RANDOM)) {
				if (quad.getSprite() != null && !sprites.contains(quad.getSprite())) {
					sprites.add(quad.getSprite());
				}
			}
		}
		for (BakedQuad quad : model.getQuads(null, null, RANDOM)) {
			if (quad.getSprite() != null && !sprites.contains(quad.getSprite())) {
				sprites.add(quad.getSprite());
			}
		}
		return sprites;
	}

	private static boolean isAnimated(TextureAtlasSprite sprite) {
		try {
			return sprite.contents().getUniqueFrames().count() > 1;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	// ------------------------------------------------------------------
	// Reflection: located by class relationships so it works under any
	// mapping (field/method names differ between dev and runtime jars).
	// ------------------------------------------------------------------

	private static Object animatedTextureOf(SpriteContents contents) {
		try {
			for (Field field : SpriteContents.class.getDeclaredFields()) {
				if (field.getType().getDeclaringClass() == SpriteContents.class) {
					field.setAccessible(true);
					return field.get(contents);
				}
			}
		} catch (ReflectiveOperationException exception) {
			SpareTheSympathy.LOGGER.warn("Failed to access animated texture field", exception);
		}
		return null;
	}

	private static List<?> animFramesOf(Object animatedTexture) {
		try {
			for (Field field : animatedTexture.getClass().getDeclaredFields()) {
				if (List.class.isAssignableFrom(field.getType())) {
					field.setAccessible(true);
					return (List<?>) field.get(animatedTexture);
				}
			}
		} catch (ReflectiveOperationException exception) {
			SpareTheSympathy.LOGGER.warn("Failed to access animated frames field", exception);
		}
		return null;
	}

	private static void uploadFrame(Object animatedTexture, TextureAtlasSprite sprite, int cellIndex) throws ReflectiveOperationException {
		// The game's ticker uploads while the atlas texture is bound; the
		// subimage upload writes into whatever texture is currently bound, so
		// bind the item atlas explicitly before uploading.
		AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation());
		GlStateManager._bindTexture(atlas.getId());
		uploadFrame(animatedTexture, sprite.getX(), sprite.getY(), cellIndex);
	}

	private static void uploadFrame(Object animatedTexture, int x, int y, int cellIndex) throws ReflectiveOperationException {
		for (Method method : animatedTexture.getClass().getDeclaredMethods()) {
			if (method.getReturnType() == void.class
				&& method.getParameterCount() == 3
				&& method.getParameterTypes()[0] == int.class
				&& method.getParameterTypes()[1] == int.class
				&& method.getParameterTypes()[2] == int.class) {
				method.setAccessible(true);
				method.invoke(animatedTexture, x, y, cellIndex);
				return;
			}
		}
		throw new NoSuchMethodException("uploadFrame(int,int,int) not found on " + animatedTexture.getClass().getName());
	}

	/** FrameInfo.index: first int field in declaration order. */
	private static int frameIndexOf(Object frameInfo) {
		return intFieldOf(frameInfo, 0);
	}

	/** FrameInfo.time: second int field in declaration order. */
	private static int frameTimeOf(Object frameInfo) {
		return intFieldOf(frameInfo, 1);
	}

	private static int intFieldOf(Object owner, int ordinal) {
		try {
			int seen = 0;
			for (Field field : owner.getClass().getDeclaredFields()) {
				if (field.getType() == int.class) {
					if (seen == ordinal) {
						field.setAccessible(true);
						return field.getInt(owner);
					}
					seen++;
				}
			}
		} catch (ReflectiveOperationException exception) {
			SpareTheSympathy.LOGGER.warn("Failed to read frame info field", exception);
		}
		return 0;
	}
}
