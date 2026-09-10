package sts.mod.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Turns an in-game ItemStack into the payload the STS site uses to create a
 * custom item: display name, vanilla base item, equipment type, the colored
 * display-lore lines, and the Monumenta Stock enchantment levels. The site
 * maps the lore to its stat keys (lib/item-lore.js).
 */
public final class ItemUploader {
	private ItemUploader() {
	}

	public static JsonObject buildPayload(ItemStack stack) {
		JsonObject payload = new JsonObject();
		String name = stack.getHoverName().getString().trim();
		payload.addProperty("name", name);
		payload.addProperty("baseItem", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		payload.addProperty("type", inferType(stack));

		CompoundTag display = stack.getTagElement("display");
		if (display != null && display.contains("Lore", Tag.TAG_LIST)) {
			JsonArray lore = new JsonArray();
			ListTag lines = display.getList("Lore", Tag.TAG_STRING);
			for (int i = 0; i < lines.size(); i++) {
				lore.add(lines.getString(i));
			}
			payload.add("lore", lore);
		}

		CompoundTag monumenta = stack.getTagElement("Monumenta");
		if (monumenta != null) {
			CompoundTag stock = monumenta.getCompound("Stock");
			CompoundTag enchantments = stock.getCompound("Enchantments");
			if (!enchantments.isEmpty()) {
				JsonObject enchants = new JsonObject();
				for (String key : enchantments.getAllKeys()) {
					CompoundTag entry = enchantments.getCompound(key);
					int level = entry.contains("Level") ? entry.getInt("Level") : 1;
					if (level > 0) {
						enchants.addProperty(key, level);
					}
				}
				if (enchants.size() > 0) {
					payload.add("enchants", enchants);
				}
			}
		}
		return payload;
	}

	private static String inferType(ItemStack stack) {
		CompoundTag monumenta = stack.getTagElement("Monumenta");
		if (monumenta != null && monumenta.contains("Power")) {
			return "Charm";
		}

		EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
		switch (slot) {
			case HEAD:
				return "Helmet";
			case CHEST:
				return "Chestplate";
			case LEGS:
				return "Leggings";
			case FEET:
				return "Boots";
			case OFFHAND:
				return "Offhand";
			default:
				break;
		}

		Item item = stack.getItem();
		if (item instanceof SwordItem) {
			return "Mainhand Sword";
		}
		if (item instanceof AxeItem) {
			return "Axe";
		}
		if (item instanceof PickaxeItem) {
			return "Pickaxe";
		}
		if (item instanceof ShovelItem) {
			return "Shovel";
		}
		if (item instanceof TridentItem) {
			return "Trident";
		}
		if (item instanceof BowItem) {
			return "Bow";
		}
		if (item instanceof CrossbowItem) {
			return "Crossbow";
		}
		if (item instanceof ShieldItem) {
			return "Offhand";
		}
		if (item instanceof PotionItem || item.isEdible()) {
			return "Consumable";
		}
		return "Mainhand";
	}
}
