package sts.mod.client.render;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

	/** Collects the sprites referenced by a model (for sizing). */
public final class TextureSpriteCollector {
	public record SpriteRef(TextureAtlasSprite sprite) {
		public net.minecraft.client.renderer.texture.SpriteContents contents() {
			return sprite.contents();
		}
	}

	private static final RandomSource RANDOM = RandomSource.create();

	private TextureSpriteCollector() {
	}

	public static List<SpriteRef> sprites(BakedModel model) {
		Set<TextureAtlasSprite> sprites = new LinkedHashSet<>();
		for (Direction direction : Direction.values()) {
			for (BakedQuad quad : model.getQuads(null, direction, RANDOM)) {
				if (quad.getSprite() != null) {
					sprites.add(quad.getSprite());
				}
			}
		}
		for (BakedQuad quad : model.getQuads(null, null, RANDOM)) {
			if (quad.getSprite() != null) {
				sprites.add(quad.getSprite());
			}
		}
		List<SpriteRef> result = new ArrayList<>(sprites.size());
		for (TextureAtlasSprite sprite : sprites) {
			result.add(new SpriteRef(sprite));
		}
		return result;
	}
}
