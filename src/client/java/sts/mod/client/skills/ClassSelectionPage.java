package sts.mod.client.skills;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Identifies the page of Monumenta's Class Selection GUI from its client slots. */
public final class ClassSelectionPage {
	public enum Page {
		CLASS,
		SKILLS,
		SPEC,
		OTHER
	}

	private static final String TITLE = "Class Selection GUI";

	private ClassSelectionPage() {
	}

	public static boolean isClassSelection(Screen screen) {
		if (!(screen instanceof ContainerScreen container)) return false;
		if (!TITLE.equals(container.getTitle().getString())) return false;
		AbstractContainerMenu menu = container.getMenu();
		return menu != null && menu.slots.size() >= 54;
	}

	/** The page texture key (gui_class_2_2 etc.), or null without GUI textures. */
	public static String pageKey(Screen screen) {
		if (!(screen instanceof ContainerScreen container) || !isClassSelection(screen)) return null;
		AbstractContainerMenu menu = container.getMenu();
		String texture = guiTexture(menu.getSlot(45).getItem());
		if (texture == null) texture = guiTexture(menu.getSlot(53).getItem());
		return texture == null ? null : texture.replaceAll("_[lr]$", "");
	}

	public static Page page(Screen screen) {
		if (!(screen instanceof ContainerScreen container) || !isClassSelection(screen)) return Page.OTHER;
		AbstractContainerMenu menu = container.getMenu();

		// The real GUI's texture identifier is stored on the bottom-left filler
		// item: gui_class_1 (class list), gui_class_2_1/2_2 (skills), gui_class_3
		// (spec skills). Read the right one as a fallback for resource packs.
		String texture = guiTexture(menu.getSlot(45).getItem());
		if (texture == null) texture = guiTexture(menu.getSlot(53).getItem());
		if (texture != null) {
			String key = texture.replaceAll("_[lr]$", "");
			if (key.startsWith("gui_class_2_")) return Page.SKILLS;
			if (key.equals("gui_class_3")) return Page.SPEC;
			if (key.equals("gui_class_1")) return Page.CLASS;
		}

		// Players who have GUI textures turned off do not get the texture NBT,
		// so fall back to the level icons the pages always render. SkillPage's
		// first class ability is on row 1; SpecPage's first spec ability is on
		// row 2. The class overview has no level icons at all.
		boolean anyLevel = false;
		boolean firstRow = false;
		for (int i = 0; i < 54; i++) {
			int level = levelOf(menu.getSlot(i).getItem());
			if (level > 0) {
				anyLevel = true;
				if (i / 9 == 1) firstRow = true;
			}
		}
		if (firstRow) return Page.SKILLS;
		if (anyLevel) return Page.SPEC;
		return Page.OTHER;
	}

	/** Level encoded by a level icon's {@code [Lv. N]} name, or 0 if not one. */
	public static int levelOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0;
		String name = stack.getHoverName().getString().trim();
		int marker = name.lastIndexOf(" [Lv. ");
		if (marker <= 0 || !name.endsWith("]")) return 0;
		String level = name.substring(marker + 6, name.length() - 1);
		if (!"1".equals(level) && !"2".equals(level)) return 0;
		return level.charAt(0) - '0';
	}

	/**
	 * True for the editable class overview. With GUI textures disabled the
	 * overview has no texture tag, so its populated, level-icon-free slot grid
	 * is used as the fallback signal (an empty container is not treated as it).
	 */
	public static boolean isClassPage(Screen screen) {
		if (!isClassSelection(screen)) return false;
		Page page = page(screen);
		if (page == Page.CLASS) return true;
		if (page != Page.OTHER) return false;
		ContainerScreen container = (ContainerScreen) screen;
		AbstractContainerMenu menu = container.getMenu();
		// Skill/spec pages have a Return arrow at slot 0; the class overview
		// does not. That distinguishes a not-yet-populated skill page from the
		// class overview when the texture tag is unavailable.
		if (menu.getSlot(0).getItem().is(Items.ARROW)) return false;
		int nonEmpty = 0;
		for (int i = 0; i < menu.slots.size(); i++) {
			if (!menu.getSlot(i).getItem().isEmpty()) nonEmpty++;
		}
		return nonEmpty >= 5;
	}

	public static boolean editable(Screen screen) {
		Page page = page(screen);
		return page == Page.SKILLS || page == Page.SPEC;
	}

	public static String headerName(ContainerScreen container) {
		AbstractContainerMenu menu = container.getMenu();
		if (menu == null || menu.slots.size() <= 4) return "";
		return menu.getSlot(4).getItem().getHoverName().getString().trim();
	}

	public static String guiTexture(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains("Gui")) return null;
		String texture = tag.getCompound("Gui").getString("texture");
		return texture == null || texture.isEmpty() ? null : texture;
	}
}
