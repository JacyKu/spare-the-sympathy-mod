package sts.mod.client.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Rebuilds the builtin/generated ("flat") item model from a SINGLE animation
 * frame's pixels. The game bakes the flat model from the union of all frames,
 * which locks the artwork to one silhouette; generating per frame lets artwork
 * that moves positionally between cells render at its true position.
 *
 * The span-scan and element geometry replicate
 * {@link net.minecraft.client.renderer.block.model.ItemModelGenerator}
 * exactly, restricted to one frame's pixels.
 */
public final class FrameItemModel {
	private FrameItemModel() {
	}

	private enum SpanFacing {
		UP(Direction.UP, 0, -1),
		DOWN(Direction.DOWN, 0, 1),
		LEFT(Direction.EAST, -1, 0),
		RIGHT(Direction.WEST, 1, 0);

		final Direction direction;
		final int xOffset;
		final int yOffset;

		SpanFacing(Direction direction, int xOffset, int yOffset) {
			this.direction = direction;
			this.xOffset = xOffset;
			this.yOffset = yOffset;
		}

		boolean isHorizontal() {
			return this == UP || this == DOWN;
		}
	}

	private static final class Span {
		final SpanFacing facing;
		int min;
		int max;
		final int anchor;

		Span(SpanFacing facing, int extent, int anchor) {
			this.facing = facing;
			this.min = extent;
			this.max = extent;
			this.anchor = anchor;
		}

		void expand(int value) {
			if (value < this.min) {
				this.min = value;
			} else if (value > this.max) {
				this.max = value;
			}
		}
	}

	/** True when the item renders with the flat generated model (not bow/crossbow/shield/3D). */
	public static boolean isFlatItem(ItemStack stack, BakedModel model) {
		Item item = stack.getItem();
		if (item == Items.BOW || item == Items.CROSSBOW || item == Items.SHIELD) {
			return false;
		}
		// 3D block models (blockbench CIT models, shulker boxes, glazed
		// terracotta, ...) use block lighting; flat builtin/generated models
		// never do. Check this BEFORE the span-quad heuristic below: complex
		// 3D models have many small detail elements whose tiny UV regions
		// make them look "flat" by quad shape alone.
		if (model.usesBlockLight()) {
			return false;
		}
		// The builtin/generated flat model is a pixel-span construction: many
		// small quads whose UVs cover a tiny fraction of the sprite region
		// (plus a front/back pair covering the whole region). 3D block models
		// have few faces, each sampling the full region.
		List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create());
		if (quads.size() < 30) {
			return false;
		}
		int tiny = 0;
		for (BakedQuad quad : quads) {
			TextureAtlasSprite sprite = quad.getSprite();
			if (sprite == null) {
				continue;
			}
			float regionArea = (sprite.getU1() - sprite.getU0()) * (sprite.getV1() - sprite.getV0());
			if (regionArea <= 0.0F) {
				continue;
			}
			int[] vertices = quad.getVertices();
			float minU = 1.0F;
			float minV = 1.0F;
			float maxU = 0.0F;
			float maxV = 0.0F;
			for (int index = 0; index < 4; index++) {
				float u = Float.intBitsToFloat(vertices[index * 8 + 4]);
				float v = Float.intBitsToFloat(vertices[index * 8 + 5]);
				minU = Math.min(minU, u);
				minV = Math.min(minV, v);
				maxU = Math.max(maxU, u);
				maxV = Math.max(maxV, v);
			}
			float quadArea = (maxU - minU) * (maxV - minV);
			if (quadArea < regionArea * 0.05F) {
				tiny++;
			}
		}
		return tiny > quads.size() / 2;
	}

	/**
	 * Builds a baked model for one animation frame: the flat quads generated
	 * from each sprite's cell pixels, with the original model's transforms.
	 *
	 * @param frameCells the cell index per sprite (parallel to {@code sprites})
	 */
	public static BakedModel forFrame(BakedModel original, List<TextureAtlasSprite> sprites, int[] frameCells) {
		List<BakedQuad> quads = new ArrayList<>();
		for (int index = 0; index < sprites.size(); index++) {
			quads.addAll(generateQuads(sprites.get(index), frameCells[index]));
		}
		return new FrameModel(quads, original);
	}

	/** True for models built by {@link #forFrame} (flat per-frame quads). */
	public static boolean isFrameModel(BakedModel model) {
		return model instanceof FrameModel;
	}

	private static final ResourceLocation MISSINGNO = new ResourceLocation("minecraft", "missingno");

	/** True when the sprite is the missing-texture placeholder. */
	public static boolean isMissingno(TextureAtlasSprite sprite) {
		return sprite != null && MISSINGNO.equals(sprite.contents().name());
	}

	/** True when any quad of the model references the missing-texture sprite. */
	public static boolean containsMissingno(BakedModel model) {
		return anyQuad(model, quad -> isMissingno(quad.getSprite()));
	}

	/**
	 * Wraps a model and drops every quad whose sprite is the missing-texture
	 * placeholder. Some CIT source models in the Monumenta pack reference
	 * texture paths that never resolve (ETF logs "Missing textures in model");
	 * without the filter the captured icon carries purple/black patches.
	 */
	public static BakedModel withoutMissingno(BakedModel original) {
		return new FilteredModel(original, quad -> !isMissingno(quad.getSprite()));
	}

	private static boolean anyQuad(BakedModel model, Predicate<BakedQuad> test) {
		for (Direction direction : Direction.values()) {
			for (BakedQuad quad : model.getQuads(null, direction, RandomSource.create())) {
				if (test.test(quad)) {
					return true;
				}
			}
		}
		for (BakedQuad quad : model.getQuads(null, null, RandomSource.create())) {
			if (test.test(quad)) {
				return true;
			}
		}
		return false;
	}

	/** A baked model that forwards everything but filters its quads. */
	private record FilteredModel(BakedModel original, Predicate<BakedQuad> keep) implements BakedModel {
		@Override
		public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource random) {
			List<BakedQuad> quads = original.getQuads(state, direction, random);
			return quads.stream().filter(keep).toList();
		}

		@Override
		public boolean useAmbientOcclusion() {
			return original.useAmbientOcclusion();
		}

		@Override
		public boolean isGui3d() {
			return original.isGui3d();
		}

		@Override
		public boolean usesBlockLight() {
			return original.usesBlockLight();
		}

		@Override
		public boolean isCustomRenderer() {
			return original.isCustomRenderer();
		}

		@Override
		public TextureAtlasSprite getParticleIcon() {
			return original.getParticleIcon();
		}

		@Override
		public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
			return original.getTransforms();
		}

		@Override
		public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
			return original.getOverrides();
		}
	}

	private static List<BakedQuad> generateQuads(TextureAtlasSprite sprite, int frame) {
		SpriteContents contents = sprite.contents();
		int width = contents.width();
		int height = contents.height();

		List<Span> spans = new ArrayList<>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (isTransparent(contents, frame, x, y, width, height)) {
					continue;
				}
				for (SpanFacing facing : SpanFacing.values()) {
					if (isTransparent(contents, frame, x + facing.xOffset, y + facing.yOffset, width, height)) {
						createOrExpandSpan(spans, facing, x, y);
					}
				}
			}
		}
		float uScale = 16.0F / width;
		float vScale = 16.0F / height;
		FaceBakery faceBakery = new FaceBakery();
		List<BakedQuad> quads = new ArrayList<>(spans.size() + 2);
		// The visible faces: the full-box front (NORTH) / back (SOUTH) pair
		// with the whole-region UVs, exactly like ItemModelGenerator's
		// full-box element. The span quads below are the thin side edges.
		Vector3f fullFrom = new Vector3f(0.0F, 0.0F, 7.5F);
		Vector3f fullTo = new Vector3f(16.0F, 16.0F, 8.5F);
		BlockElementFace southFace = new BlockElementFace(null, -1, "layer0",
			new BlockFaceUV(new float[]{0.0F, 0.0F, 16.0F, 16.0F}, 0));
		quads.add(faceBakery.bakeQuad(fullFrom, fullTo, southFace, sprite, Direction.SOUTH,
			BlockModelRotation.X0_Y0, null, true, contents.name()));
		BlockElementFace northFace = new BlockElementFace(null, -1, "layer0",
			new BlockFaceUV(new float[]{16.0F, 0.0F, 0.0F, 16.0F}, 0));
		quads.add(faceBakery.bakeQuad(fullFrom, fullTo, northFace, sprite, Direction.NORTH,
			BlockModelRotation.X0_Y0, null, true, contents.name()));
		for (Span span : spans) {
			float x1;
			float x2;
			float x3;
			float x4;
			float y1;
			float y2;
			float y3;
			float y4;
			switch (span.facing) {
				case UP -> {
					x1 = x2 = span.min;
					x3 = x4 = span.max + 1;
					y1 = y2 = y3 = span.anchor;
					y4 = span.anchor + 1;
				}
				case DOWN -> {
					x1 = x2 = span.min;
					x3 = x4 = span.max + 1;
					y1 = span.anchor;
					y2 = y3 = y4 = span.anchor + 1;
				}
				case LEFT -> {
					x1 = x2 = span.anchor;
					x3 = span.anchor + 1;
					x4 = span.anchor;
					y1 = y3 = span.max + 1;
					y2 = y4 = span.min;
				}
				default -> {
					x1 = span.anchor;
					x2 = x3 = x4 = span.anchor + 1;
					y1 = y3 = span.max + 1;
					y2 = y4 = span.min;
				}
			}
			x2 *= uScale;
			x4 *= uScale;
			y2 = 16.0F - y2 * vScale;
			y3 = 16.0F - y3 * vScale;
			x1 *= uScale;
			x3 *= uScale;
			y1 *= vScale;
			y4 *= vScale;

			BlockFaceUV uv = new BlockFaceUV(new float[]{x1, y1, x3, y4}, 0);
			BlockElementFace face = new BlockElementFace(null, -1, "layer0", uv);
			Vector3f from;
			Vector3f to;
			switch (span.facing) {
				case UP -> {
					from = new Vector3f(x2, y2, 7.5F);
					to = new Vector3f(x4, y2, 8.5F);
				}
				case DOWN -> {
					from = new Vector3f(x2, y3, 7.5F);
					to = new Vector3f(x4, y3, 8.5F);
				}
				case LEFT -> {
					from = new Vector3f(x2, y2, 7.5F);
					to = new Vector3f(x2, y3, 8.5F);
				}
				default -> {
					from = new Vector3f(x4, y2, 7.5F);
					to = new Vector3f(x4, y3, 8.5F);
				}
			}
			quads.add(faceBakery.bakeQuad(from, to, face, sprite, span.facing.direction,
				BlockModelRotation.X0_Y0, null, true, contents.name()));
		}
		return quads;
	}

	private static void createOrExpandSpan(List<Span> spans, SpanFacing facing, int x, int y) {
		Span found = null;
		int anchor = facing.isHorizontal() ? y : x;
		for (Span span : spans) {
			if (span.facing == facing && span.anchor == anchor) {
				found = span;
				break;
			}
		}
		int extent = facing.isHorizontal() ? x : y;
		if (found == null) {
			spans.add(new Span(facing, extent, anchor));
		} else {
			found.expand(extent);
		}
	}

	private static boolean isTransparent(SpriteContents contents, int frame, int x, int y, int width, int height) {
		if (x < 0 || y < 0 || x >= width || y >= height) {
			return true;
		}
		return contents.isTransparent(frame, x, y);
	}

	private record FrameModel(List<BakedQuad> quads, BakedModel original) implements BakedModel {
		@Override
		public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource random) {
			return direction == null ? quads : List.of();
		}

		@Override
		public boolean useAmbientOcclusion() {
			return false;
		}

		@Override
		public boolean isGui3d() {
			return false;
		}

		@Override
		public boolean usesBlockLight() {
			return original.usesBlockLight();
		}

		@Override
		public boolean isCustomRenderer() {
			return false;
		}

		@Override
		public TextureAtlasSprite getParticleIcon() {
			return original.getParticleIcon();
		}

		@Override
		public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
			return original.getTransforms();
		}

		@Override
		public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
			return net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY;
		}
	}
}
