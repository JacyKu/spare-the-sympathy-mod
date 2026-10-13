package sts.mod.client.armoury;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import sts.mod.api.BuildTokenEncoder;
import sts.mod.api.ItemUploader;
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
	// The armoury icons carry the loadout's *preferred* basic infusion
	// ("Preferred Infusion: Vitality") but never its level - the level lives on
	// the real item - so the maximum level is assumed on export.
	private static final int ASSUMED_BASIC_INFUSION_LEVEL = 4;
	// The tooltip's "Masterwork : ★★" line (a star per level, 1-4).
	private static final Pattern MASTERWORK_STARS = Pattern.compile("Masterwork\\s*:\\s*([\\u2605]+)");
	// The Mechanical Armory's icons mark the exalted (Ring) version of an item
	// with this lore line (see Monumenta's LoadoutManagerGui). The exact
	// masterwork level is not disclosed, so the highest variant stands in.
	private static final String EXALTED_VERSION_LINE = "Exalted version";

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
		java.util.Map<String, String> delveInfusions,
		java.util.Map<String, sts.mod.api.InfusionReader.BasicInfusion> basicInfusions,
		List<JsonObject> unknownItemPayloads
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
		// Equipment the Monumenta dictionary doesn't know about (unreleased /
		// event items): uploaded alongside the build so the site can create
		// custom items for them.
		List<JsonObject> unknownItemPayloads = new ArrayList<>();
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
			if ("None".equals(itemKeys[i]) && !stack.isEmpty()) {
				JsonObject payload = ItemUploader.buildPayload(stack);
				String displayName = payload.get("name").getAsString();
				if (!displayName.isEmpty()) {
					// The token hashes the item key; the display name is the
					// key the uploaded custom item gets on the site.
					itemKeys[i] = displayName;
					unknownItemPayloads.add(payload);
				}
			}
		}

		// Infusions: the equipment icons carry the real item lore, so the
		// applied delve infusion and the basic (normal) infusion can be read
		// from the same tooltips the /ps view uses. When an item has no delve
		// infusion applied, fall back to its "Preferred Delve Infusion: Y"
		// line ("any" means no preference) so the builder's dropdowns still
		// show the player's preference.
		java.util.Map<String, String> delveInfusions = new java.util.LinkedHashMap<>();
		java.util.Map<String, sts.mod.api.InfusionReader.BasicInfusion> basicInfusions = new java.util.LinkedHashMap<>();
		Player player = Minecraft.getInstance().player;
		for (int i = 0; i < 6; i++) {
			ItemStack stack = menu.slots.get(resolvedSlots[i]).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String slotName = SLOT_NAMES[i];
			sts.mod.api.InfusionReader.BasicInfusion basic = sts.mod.api.InfusionReader.basicInfusionOn(stack, player);
			if (basic == null) {
				// No level on the icon: use the loadout's preferred infusion
				// with the assumed level.
				basic = sts.mod.api.InfusionReader.preferredBasicInfusionOn(
					stack,
					player,
					ASSUMED_BASIC_INFUSION_LEVEL
				);
			}
			if (basic != null) {
				basicInfusions.put(slotName, basic);
			}
			String applied = sts.mod.api.InfusionReader.delveInfusionOn(stack, player);
			if (applied != null) {
				delveInfusions.put(slotName, applied);
				continue;
			}
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
			} else {
				// Unknown charm: upload it so it exists as a custom charm on
				// the site (the build token can't reference it, but the
				// builder's charm picker will list it).
				JsonObject payload = ItemUploader.buildPayload(stack);
				payload.addProperty("type", "Charm");
				if (!payload.get("name").getAsString().isEmpty()) {
					unknownItemPayloads.add(payload);
				}
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
				specName = knownSpecName(classes, className, specName);
				parseSkills(classItem, className, specName, classes, skills, specSkills, enhancements);
			}
		}
		if (className == null) {
			for (int slot = 45; slot < menu.slots.size() && className == null; slot++) {
				ItemStack candidate = menu.slots.get(slot).getItem();
				if (candidate.isEmpty()) {
					continue;
				}
				Matcher candidateMatcher = CLASS_NAME.matcher(candidate.getHoverName().getString());
				if (candidateMatcher.matches()) {
					className = candidateMatcher.group(1).trim();
					specName = candidateMatcher.group(2).trim();
					if (!classes.isEmpty()) {
						specName = knownSpecName(classes, className, specName);
						parseSkills(candidate, className, specName, classes, skills, specSkills, enhancements);
					}
				}
			}
		}

		return new Loadout(name, itemKeys, charmKeys, className, specName, skills, specSkills, enhancements, delveInfusions, basicInfusions, unknownItemPayloads);
	}

	// Matches an armoury icon back to a dictionary entry by vanilla base item +
	// display name; the in-game name never carries the site's "EX " prefix, so
	// both sides are compared without it and the tooltip's "Masterwork : ★N"
	// line (real items) or "Exalted version" line (armory icons) selects the
	// masterwork variant. Also used by the viewed-player readers (/ps
	// equipment), which show the same items.
	public static String matchItemKey(ItemStack stack, List<MonumentaItemDefinition> items) {
		if (stack.isEmpty() || items.isEmpty()) {
			return "None";
		}
		String rawName = stack.getHoverName().getString().trim();
		boolean exNamed = !stripExPrefix(rawName).equals(rawName);
		String name = stripExPrefix(rawName);
		String baseItem = vanillaBaseName(stack);
		int wantedLevel = masterworkLevelFromStack(stack);
		if (exNamed && wantedLevel == 0) {
			// Marked EX but the level is unreadable: take the highest variant
			// instead of the base item.
			wantedLevel = -1;
		}
		return resolveItemKey(name, baseItem, wantedLevel, items);
	}

	/**
	 * Picks the dictionary key for an item name. {@code wantedLevel} is the
	 * masterwork level to resolve: a positive level selects that variant, 0
	 * selects the base item (the item's lowest variant when the dictionary has
	 * no base entry), and -1 selects the highest variant (the armory's exalted
	 * version, whose exact level the GUI does not state).
	 */
	static String resolveItemKey(String name, String baseItem, int wantedLevel, List<MonumentaItemDefinition> items) {
		MonumentaItemDefinition best = null; // highest masterwork level seen
		int bestLevel = -1;
		MonumentaItemDefinition lowest = null; // lowest masterwork level seen
		int lowestLevel = Integer.MAX_VALUE;
		MonumentaItemDefinition exact = null;
		// Pass 1 requires the vanilla base item to match; pass 2 is a fallback
		// for icons whose material spelling differs from the dictionary's
		// base_item.
		for (int pass = 0; pass < 2 && best == null; pass++) {
			for (MonumentaItemDefinition item : items) {
				if (item.type() == null || item.type().equals("Charm")) {
					continue;
				}
				if (!stripExPrefix(item.name()).equalsIgnoreCase(name)) {
					continue;
				}
				if (pass == 0 && !item.baseItemName().equalsIgnoreCase(baseItem)) {
					continue;
				}
				int level = masterworkLevel(item.key());
				if (level > bestLevel) {
					best = item;
					bestLevel = level;
				}
				if (level < lowestLevel) {
					lowest = item;
					lowestLevel = level;
				}
				if (level == wantedLevel) {
					exact = item;
				}
			}
		}
		if (exact != null) {
			return exact.key();
		}
		if (best == null) {
			return "None";
		}
		// Exalted without a level: the highest variant stands in. A base item
		// whose dictionary only has masterwork entries resolves to its lowest
		// variant - the state in which it drops.
		if (wantedLevel == 0) {
			return lowest.key();
		}
		return best.key();
	}

	// In-game names have no "EX " prefix (the site adds it when it processes
	// masterwork variants); stripping it from both sides lets an item match
	// whichever side carries it.
	private static String stripExPrefix(String name) {
		if (name.length() > 3
			&& (name.charAt(0) == 'E' || name.charAt(0) == 'e')
			&& (name.charAt(1) == 'X' || name.charAt(1) == 'x')
			&& name.charAt(2) == ' ') {
			return name.substring(3).trim();
		}
		return name;
	}

	// The item's masterwork level: real items carry it in their Monumenta NBT,
	// while the synthetic GUI icons state it in their tooltip (the armory's
	// "Exalted version" line or, when present, the "Masterwork : ★N" stars).
	// No level means the base (non-masterwork) version.
	private static int masterworkLevelFromStack(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag != null && tag.contains("Monumenta")) {
			CompoundTag monumenta = tag.getCompound("Monumenta");
			if (monumenta.contains("Masterwork")) {
				try {
					int level = Integer.parseInt(monumenta.getString("Masterwork").trim());
					if (level > 0) {
						return level;
					}
				} catch (NumberFormatException ignored) {
					// fall through to the tooltip
				}
			}
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		List<String> lines = new ArrayList<>();
		for (var line : stack.getTooltipLines(player, TooltipFlag.NORMAL)) {
			lines.add(line.getString());
		}
		return masterworkLevelFromLines(lines);
	}

	/**
	 * Classifies an item tooltip's masterwork markers: a "Masterwork : ★N"
	 * line gives the exact level, the armory icons' "Exalted version" line
	 * marks the exalted (Ring) variant whose level the GUI does not state
	 * (returned as -1, resolved to the highest variant), and no marker at all
	 * means the base version (0).
	 */
	static int masterworkLevelFromLines(List<String> lines) {
		boolean exalted = false;
		for (String text : lines) {
			Matcher matcher = MASTERWORK_STARS.matcher(text);
			if (matcher.find()) {
				return matcher.group(1).length();
			}
			if (text.contains(EXALTED_VERSION_LINE)) {
				exalted = true;
			}
		}
		return exalted ? -1 : 0;
	}

	public static String matchCharmKey(ItemStack stack, List<MonumentaItemDefinition> items) {
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

	// The class item's hover names the spec as "(No Spec)" before one is
	// chosen (and the catalog can lag behind a rename); only a spec the catalog
	// knows survives, so the token never carries a spec name the site cannot
	// resolve (which would make it drop the whole spec/spec-skill state).
	private static String knownSpecName(List<StsApiClient.GameClass> classes, String className, String specName) {
		if (specName == null || specName.isEmpty()) {
			return null;
		}
		StsApiClient.GameClass gameClass = findClass(classes, className);
		return gameClass != null && findSpec(gameClass, specName) != null ? specName : null;
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
