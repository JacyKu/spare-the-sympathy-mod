package sts.mod.client.armoury;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import sts.mod.api.BuildTokenEncoder;
import sts.mod.api.MonumentaItemDefinition;
import sts.mod.api.StsApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the player's current loadout out of the Mechanical Armory GUI (the
 * server-side Loadout Manager in Monumenta) and converts it into the data a
 * {@code v1_} build token needs.
 * <p>
 * The armoury's loadout view is a 6-row chest GUI laid out as:
 * row 0 = meta buttons, rows 1-2 = equipment (boots/legs/chest/helmet at
 * container slots 14..11, offhand 15, mainhand 18), row 4 = charms
 * (slots 38+), row 5 = class + abilities (slot 47). The icons are synthetic
 * server-side items carrying only a vanilla material, a display name and a
 * lore, so items are matched back to the Monumenta dictionary by
 * (vanilla base item, name) and skills are parsed from the class item's lore.
 */
public final class ArmouryLoadoutReader {
	// mainhand, offhand, helmet, chestplate, leggings, boots -> container slots
	private static final int[] EQUIPMENT_SLOTS = { 18, 15, 11, 12, 13, 14 };
	private static final int CLASS_SLOT = 47;
	private static final int CHARM_START = 38;
	private static final int CHARM_END = 45;

	private static final Pattern SKILL_LINE = Pattern.compile("^(.+): (\\d+)(\\*?)$");
	private static final Pattern CLASS_NAME = Pattern.compile("^(.+?)\\s*\\((.+?)\\)$");
	private static final Pattern MASTERWORK = Pattern.compile("-(\\d+)$");
	private static final Pattern PREFERRED_DELVE = Pattern.compile("^Preferred Delve Infusion: (.+)$");

	// Equipment slot names in the builder's order (mainhand, offhand, helmet,
	// chestplate, leggings, boots).
	private static final String[] SLOT_NAMES = { "mainhand", "offhand", "helmet", "chestplate", "leggings", "boots" };

	private ArmouryLoadoutReader() {
	}

	/** The armoury loadout view the reader understands. */
	public record Loadout(
		String name,
		String[] itemKeys,
		List<String> charmKeys,
		String className,
		String specName,
		List<BuildTokenEncoder.Skill> skills,
		List<BuildTokenEncoder.Skill> specSkills,
		List<String> enhancements,
		java.util.Map<String, String> delveInfusions
	) {
	}

	/** Any page of the Mechanical Armory GUI (overview or loadout view). */
	public static boolean isArmouryScreen(Screen screen) {
		if (!(screen instanceof ContainerScreen containerScreen)) {
			return false;
		}
		AbstractContainerMenu menu = containerScreen.getMenu();
		if (menu == null || menu.slots.size() < 54) {
			return false;
		}
		return containerScreen.getTitle().getString().contains("Mechanical Armory");
	}

	/** The overview page: loadout tiles, no editable loadout contents. */
	public static boolean isOverviewPage(Screen screen) {
		if (!isArmouryScreen(screen)) {
			return false;
		}
		AbstractContainerMenu menu = ((ContainerScreen) screen).getMenu();
		// The overview's meta row: info sign, pages map, slots armour stand,
		// stash chest - none of these exist in the loadout view.
		for (int slot : new int[] { 4, 5, 6, 7 }) {
			ItemStack stack = menu.slots.get(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String hover = stack.getHoverName().getString();
			if (hover.contains("Mechanical Armory Info") || hover.contains("Available Loadout")) {
				return true;
			}
		}
		// Fallback: loadout tiles carry the "Has equipment / Has vanity /
		// Has charms" lore markers.
		Player player = Minecraft.getInstance().player;
		for (int slot = 9; slot < 18; slot++) {
			ItemStack stack = menu.slots.get(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			for (var line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
				String text = line.getString();
				if (text.contains("Has equipment") || text.contains("Has vanity") || text.contains("Has charms")) {
					return true;
				}
			}
		}
		return false;
	}

	/** The loadout view specifically (the loadout is open for editing). */
	public static boolean isLoadoutView(Screen screen) {
		return isArmouryScreen(screen) && !isOverviewPage(screen);
	}

	/** Reads the loadout from the open loadout view. Returns null when nothing usable is shown. */
	public static Loadout read(Screen screen, List<MonumentaItemDefinition> items, List<StsApiClient.GameClass> classes) {
		if (!(screen instanceof ContainerScreen containerScreen)) {
			return null;
		}
		AbstractContainerMenu menu = containerScreen.getMenu();
		if (menu == null || menu.slots.size() < 54) {
			return null;
		}

		String[] itemKeys = new String[6];
		int[] resolvedSlots = new int[6];
		for (int i = 0; i < 6; i++) {
			int slot = EQUIPMENT_SLOTS[i];
			if (i == 0 && menu.slots.get(slot).getItem().isEmpty()) {
				// Mainhand = first occupied hotbar slot (the loadout can keep
				// its weapon anywhere in the hotbar, not just slot 0).
				for (int hotbar = 19; hotbar <= 26; hotbar++) {
					if (!menu.slots.get(hotbar).getItem().isEmpty()) {
						slot = hotbar;
						break;
					}
				}
			}
			resolvedSlots[i] = slot;
			ItemStack stack = menu.slots.get(slot).getItem();
			itemKeys[i] = matchItemKey(stack, items);
		}

		// Delve infusion preferences: each equipment icon's lore carries
		// "Preferred Delve Infusion: Y" ("any" means no preference). These
		// ride along with the save so the builder's infusion dropdown shows
		// them.
		java.util.Map<String, String> delveInfusions = new java.util.LinkedHashMap<>();
		Player player = Minecraft.getInstance().player;
		for (int i = 0; i < 6; i++) {
			ItemStack stack = menu.slots.get(resolvedSlots[i]).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String slotName = SLOT_NAMES[i];
			for (var line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
				String text = line.getString();
				Matcher delve = PREFERRED_DELVE.matcher(text.trim());
				if (delve.matches() && !"any".equalsIgnoreCase(delve.group(1).trim())) {
					delveInfusions.put(slotName, delve.group(1).trim());
				}
			}
		}

		// The loadout's name is the custom name on the icon at slot 4.
		String name = null;
		ItemStack nameItem = menu.slots.get(4).getItem();
		if (!nameItem.isEmpty()) {
			String hover = nameItem.getHoverName().getString();
			if (!hover.isEmpty()) {
				name = hover;
			}
		}

		List<String> charmKeys = new ArrayList<>();
		for (int slot = CHARM_START; slot < CHARM_END; slot++) {
			ItemStack stack = menu.slots.get(slot).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String key = matchCharmKey(stack, items);
			if (key != null) {
				charmKeys.add(key);
			}
		}

		String className = null;
		String specName = null;
		List<BuildTokenEncoder.Skill> skills = new ArrayList<>();
		List<BuildTokenEncoder.Skill> specSkills = new ArrayList<>();
		List<String> enhancements = new ArrayList<>();

		ItemStack classItem = menu.slots.get(CLASS_SLOT).getItem();
		if (!classItem.isEmpty()) {
			String displayName = classItem.getHoverName().getString();
			Matcher classMatcher = CLASS_NAME.matcher(displayName);
			if (classMatcher.matches()) {
				className = classMatcher.group(1).trim();
				specName = classMatcher.group(2).trim();
			} else if (!displayName.equals("No Class")) {
				className = displayName;
			}
			if (className != null) {
				parseSkills(classItem, className, specName, classes, skills, specSkills, enhancements);
			}
		}

		return new Loadout(name, itemKeys, charmKeys, className, specName, skills, specSkills, enhancements, delveInfusions);
	}

	// Matches an armoury icon back to a dictionary entry by vanilla base item +
	// display name; masterwork variants share the name, so the highest one
	// (the "-N" key suffix) wins, mirroring the site's default.
	private static String matchItemKey(ItemStack stack, List<MonumentaItemDefinition> items) {
		if (stack.isEmpty() || items.isEmpty()) {
			return "None";
		}
		String name = stack.getHoverName().getString().trim();
		String baseItem = vanillaBaseName(stack);
		MonumentaItemDefinition best = null;
		int bestLevel = -1;
		for (MonumentaItemDefinition item : items) {
			if (item.type() == null || item.type().equals("Charm")) {
				continue;
			}
			if (!item.name().equalsIgnoreCase(name)) {
				continue;
			}
			if (!item.baseItemName().equalsIgnoreCase(baseItem)) {
				continue;
			}
			int level = masterworkLevel(item.key());
			if (level > bestLevel) {
				best = item;
				bestLevel = level;
			}
		}
		if (best == null) {
			// Fallback: name-only match (some icons use a different material
			// spelling than the dictionary's base_item).
			for (MonumentaItemDefinition item : items) {
				if (item.type() == null || item.type().equals("Charm")) {
					continue;
				}
				if (!item.name().equalsIgnoreCase(name)) {
					continue;
				}
				int level = masterworkLevel(item.key());
				if (level > bestLevel) {
					best = item;
					bestLevel = level;
				}
			}
		}
		return best != null ? best.key() : "None";
	}

	private static String matchCharmKey(ItemStack stack, List<MonumentaItemDefinition> items) {
		if (stack.isEmpty() || items.isEmpty()) {
			return null;
		}
		String name = stack.getHoverName().getString().trim();
		MonumentaItemDefinition best = null;
		int bestPower = -1;
		for (MonumentaItemDefinition item : items) {
			if (!"Charm".equals(item.type())) {
				continue;
			}
			if (!item.name().equalsIgnoreCase(name)) {
				continue;
			}
			if (item.power() > bestPower) {
				best = item;
				bestPower = item.power();
			}
		}
		if (best == null) {
			return null;
		}
		return sts.mod.api.CharmShortener.shortenCharm(best.name(), best.power(), best.className());
	}

	// Parses "Ability Name: 3*" lore lines off the class item. Abilities max
	// at 2 points; the star marks an ENHANCED ability (not extra points), so a
	// starred skill keeps its shown points and is added to the build's
	// enhancement list.
	private static void parseSkills(
		ItemStack classItem,
		String className,
		String specName,
		List<StsApiClient.GameClass> classes,
		List<BuildTokenEncoder.Skill> skills,
		List<BuildTokenEncoder.Skill> specSkills,
		List<String> enhancements
	) {
		StsApiClient.GameClass gameClass = findClass(classes, className);
		if (gameClass == null) {
			return;
		}
		java.util.Map<String, String> displayToId = new java.util.HashMap<>();
		for (StsApiClient.Ability ability : gameClass.skills()) {
			displayToId.putIfAbsent(ability.displayName(), ability.scoreboardId());
		}
		java.util.Set<String> specIds = new java.util.HashSet<>();
		StsApiClient.Spec spec = findSpec(gameClass, specName);
		if (spec != null) {
			for (StsApiClient.Ability ability : spec.specSkills()) {
				specIds.add(ability.scoreboardId());
				displayToId.putIfAbsent(ability.displayName(), ability.scoreboardId());
			}
		}

		Player player = Minecraft.getInstance().player;
		List<net.minecraft.network.chat.Component> tooltip = classItem.getTooltipLines(player, TooltipFlag.NORMAL);
		for (net.minecraft.network.chat.Component line : tooltip) {
			String text = line.getString();
			Matcher matcher = SKILL_LINE.matcher(text.trim());
			if (!matcher.matches()) {
				continue;
			}
			String displayName = matcher.group(1).trim();
			int points = Integer.parseInt(matcher.group(2));
			boolean enhanced = !matcher.group(3).isEmpty();
			String id = displayToId.get(displayName);
			if (id == null) {
				continue;
			}
			if (enhanced) {
				enhancements.add(id);
			}
			(specIds.contains(id) ? specSkills : skills).add(new BuildTokenEncoder.Skill(id, points));
		}
	}

	private static StsApiClient.GameClass findClass(List<StsApiClient.GameClass> classes, String className) {
		if (className == null || classes == null) {
			return null;
		}
		for (StsApiClient.GameClass gameClass : classes) {
			if (gameClass.className().equalsIgnoreCase(className)) {
				return gameClass;
			}
		}
		return null;
	}

	private static StsApiClient.Spec findSpec(StsApiClient.GameClass gameClass, String specName) {
		if (specName == null) {
			return null;
		}
		for (StsApiClient.Spec spec : gameClass.specs()) {
			if (spec.specName().equalsIgnoreCase(specName)) {
				return spec;
			}
		}
		return null;
	}

	private static String vanillaBaseName(ItemStack stack) {
		String name = stack.getItem().getName(stack).getString().trim();
		int paren = name.indexOf(" (");
		return paren > 0 ? name.substring(0, paren) : name;
	}

	private static int masterworkLevel(String key) {
		Matcher matcher = MASTERWORK.matcher(key == null ? "" : key);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
	}
}
