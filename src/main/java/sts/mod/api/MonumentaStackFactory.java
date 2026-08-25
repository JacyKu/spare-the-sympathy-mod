package sts.mod.api;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import sts.mod.SpareTheSympathy;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds renderable ItemStacks from API definitions. Items with real server
 * NBT get it verbatim (so CIT textures, models, and head skins all apply);
 * the rest fall back to a plain stack with the display name so CIT entries
 * matching on the name still work.
 */
public final class MonumentaStackFactory {
	private static final Map<String, String> SPECIAL_CASE_PATHS = createSpecialCasePaths();

	private MonumentaStackFactory() {
	}

	public static ItemStack createStack(MonumentaItemDefinition definition) {
		Item item = resolveBaseItem(definition.baseItemName());
		ItemStack stack = new ItemStack(item);
		if (definition.rawNbt() != null && !definition.rawNbt().isBlank()) {
			try {
				CompoundTag tag = TagParser.parseTag(definition.rawNbt());
				if (tag != null && !tag.isEmpty()) {
					stack.setTag(tag);
					return stack;
				}
			} catch (CommandSyntaxException exception) {
				SpareTheSympathy.LOGGER.warn("Failed to parse SNBT for '{}', using synthesized stack", definition.key());
			}
		}

		stack.getOrCreateTag().putInt("HideFlags", 3);
		CompoundTag plainDisplay = stack.getOrCreateTagElement("plain").getCompound("display");
		plainDisplay.putString("Name", definition.name());
		stack.getOrCreateTagElement("plain").put("display", plainDisplay);
		CompoundTag display = stack.getOrCreateTagElement("display");
		display.putString("Name", Component.Serializer.toJson(Component.literal(definition.name())));
		return stack;
	}

	private static Item resolveBaseItem(String baseItemName) {
		Item resolved = resolveBaseItemLookup(baseItemName);
		if (resolved != null) {
			return resolved;
		}
		if (baseItemName != null && !baseItemName.isBlank()) {
			SpareTheSympathy.LOGGER.warn("Unable to resolve Monumenta base item '{}', falling back to paper", baseItemName);
		}
		return Items.PAPER;
	}

	private static Item resolveBaseItemLookup(String baseItemName) {
		String normalized = normalizeBaseItemName(baseItemName == null ? "" : baseItemName);
		ResourceLocation candidate = resolveBaseItemId(baseItemName);
		if (candidate != null) {
			Item direct = BuiltInRegistries.ITEM.get(candidate);
			if (direct != Items.AIR) {
				return direct;
			}
		}

		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (id == null) {
				continue;
			}
			if (candidate != null
				&& id.getNamespace().equalsIgnoreCase(candidate.getNamespace())
				&& id.getPath().equalsIgnoreCase(candidate.getPath())) {
				return item;
			}
			if (!normalized.isBlank() && normalizeBaseItemName(id.getPath()).equals(normalized)) {
				return item;
			}
		}
		return null;
	}

	private static ResourceLocation resolveBaseItemId(String baseItemName) {
		if (baseItemName == null || baseItemName.isBlank()) {
			return null;
		}
		String trimmed = baseItemName.trim();
		ResourceLocation direct = ResourceLocation.tryParse(trimmed);
		if (direct != null) {
			return direct;
		}

		String normalized = normalizeBaseItemName(trimmed);
		String specialPath = SPECIAL_CASE_PATHS.get(normalized);
		if (specialPath != null) {
			return new ResourceLocation("minecraft", specialPath);
		}
		return normalized.isBlank() ? null : new ResourceLocation("minecraft", normalized);
	}

	private static String normalizeBaseItemName(String value) {
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
		normalized = normalized.replaceAll("\\p{M}+", "");
		normalized = normalized.toLowerCase(Locale.ROOT);
		normalized = normalized.replaceAll("[^a-z0-9]+", "_");
		normalized = normalized.replaceAll("_+", "_");
		normalized = normalized.replaceAll("^_", "");
		normalized = normalized.replaceAll("_$", "");
		return normalized;
	}

	private static Map<String, String> createSpecialCasePaths() {
		Map<String, String> specialPaths = new LinkedHashMap<>();
		specialPaths.put("nether_quartz", "quartz");
		specialPaths.put("redstone_dust", "redstone");
		specialPaths.put("bottle_o_enchanting", "experience_bottle");
		return specialPaths;
	}
}
