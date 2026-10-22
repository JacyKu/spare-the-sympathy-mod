package sts.mod.client.skills;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import sts.mod.api.BuildSkills;
import sts.mod.api.StsApiClient;
import sts.mod.api.StsConfig;
import sts.mod.client.armoury.ArmouryTracker;
import sts.mod.client.mixin.ScreenWidgetAccessor;
import sts.mod.client.view.ViewedPlayers;
import sts.mod.client.view.ViewedPlayersTracker;

/**
 * Build-link helper for Monumenta's Class Selection GUI.
 * <p>
 * The build link is only a reference: the mod never clicks the GUI or changes
 * skills (that would be a macro). It highlights the level/enhancement slots
 * that still need work, and draws a plain-text status list beside the GUI, so
 * the player can make the changes manually.
 */
public final class SkillPickerOverlay {
	private static final int LINK_WIDTH = 170;
	private static final int LINK_HEIGHT = 20;
	private static final int LOAD_WIDTH = 40;

	private static Screen screen;
	private static Screen seenScreen;
	private static Screen readOnlyScreen;
	private static EditBox linkInput;
	private static Button loadButton;
	private static BuildSkills payload;
	private static List<StsApiClient.GameClass> catalog = List.of();
	private static String linkValue = "";
	private static boolean loading;
	private static ClassSelectionPage.Page firstPage;

	// Ctrl+drag only moves the build-link input now; the old skill buttons are
	// gone because clicking them would perform macro-like edits.
	private static boolean dragging;
	private static double dragStartMouseX;
	private static double dragStartMouseY;
	private static int dragStartLinkX;
	private static int dragStartLinkY;
	private static int dragLinkX;
	private static int dragLinkY;

	private static boolean draggingText;
	private static double textDragStartMouseX;
	private static double textDragStartMouseY;
	private static int textDragStartX;
	private static int textDragStartY;
	private static int textDragX;
	private static int textDragY;
	private static int textX;
	private static int textY;
	private static int textWidth;
	private static int textHeight;

	private SkillPickerOverlay() {
	}

	/** Keeps container hotkeys (including the inventory key) from firing while the link field is focused. */
	public static boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (linkInput == null || !linkInput.isFocused() || keyCode == GLFW.GLFW_KEY_ESCAPE) {
			return false;
		}
		linkInput.keyPressed(keyCode, scanCode, modifiers);
		return true;
	}

	/** Whether the cursor is over the build-link input controls. */
	public static boolean isOverButton(double mouseX, double mouseY) {
		if (screen == null || Minecraft.getInstance().screen != screen) {
			return false;
		}
		return over(linkInput, mouseX, mouseY) || over(loadButton, mouseX, mouseY) || overText(mouseX, mouseY);
	}

	/** Ctrl+drag the input or the text overlay. */
	public static boolean dragIfOver(double mouseX, double mouseY, int button) {
		if (button != 0 || screen == null || Minecraft.getInstance().screen != screen) {
			return false;
		}
		if (!Screen.hasControlDown()) {
			if (dragging || draggingText) {
				endDrag();
			}
			return false;
		}
		if (!dragging && !draggingText) {
			if (over(linkInput, mouseX, mouseY) || over(loadButton, mouseX, mouseY)) {
				dragging = true;
				dragStartMouseX = mouseX;
				dragStartMouseY = mouseY;
				dragStartLinkX = StsConfig.skillPickerLinkX();
				dragStartLinkY = StsConfig.skillPickerLinkY();
				dragLinkX = dragStartLinkX;
				dragLinkY = dragStartLinkY;
			} else if (overText(mouseX, mouseY)) {
				draggingText = true;
				textDragStartMouseX = mouseX;
				textDragStartMouseY = mouseY;
				textDragStartX = StsConfig.skillPickerRowX();
				textDragStartY = StsConfig.skillPickerRowY();
				textDragX = textDragStartX;
				textDragY = textDragStartY;
			} else {
				return false;
			}
		}
		if (dragging) {
			dragLinkX = dragStartLinkX + (int) Math.round(mouseX - dragStartMouseX);
			dragLinkY = dragStartLinkY + (int) Math.round(mouseY - dragStartMouseY);
			if (linkInput != null) {
				linkInput.setX(dragLinkX);
				linkInput.setY(dragLinkY);
			}
			if (loadButton != null) {
				loadButton.setX(dragLinkX + LINK_WIDTH + 4);
				loadButton.setY(dragLinkY);
			}
		} else {
			textDragX = textDragStartX + (int) Math.round(mouseX - textDragStartMouseX);
			textDragY = textDragStartY + (int) Math.round(mouseY - textDragStartMouseY);
		}
		return true;
	}

	public static void endDrag() {
		if (dragging) {
			dragging = false;
			StsConfig.setSkillPickerLinkPosition(dragLinkX, dragLinkY);
			applyInputPosition();
		}
		if (draggingText) {
			draggingText = false;
			StsConfig.setSkillPickerRowPosition(textDragX, textDragY);
		}
	}

	public static void tick(Minecraft mc) {
		if (!StsConfig.skillPickerEnabled()) {
			detach();
			return;
		}
		Screen current = mc.screen;
		if (current != seenScreen) {
			seenScreen = current;
			readOnlyScreen = null;
			firstPage = null;
		}
		if (ClassSelectionPage.isClassSelection(current) && firstPage == null) {
			ClassSelectionPage.Page observed = ClassSelectionPage.page(current);
			if (observed == ClassSelectionPage.Page.CLASS
				|| (observed == ClassSelectionPage.Page.OTHER && ClassSelectionPage.isClassPage(current))) {
				// Editable class selection starts on the class overview. Clear
				// a stale /pa marker from an earlier lookup.
				firstPage = ClassSelectionPage.Page.CLASS;
				readOnlyScreen = null;
				if (ViewedPlayers.isAbilitiesView()) {
					ViewedPlayers.clearAbilitiesView();
				}
			} else if (observed == ClassSelectionPage.Page.SKILLS
				|| observed == ClassSelectionPage.Page.SPEC) {
				// /pa opens directly on a skill/spec page. Keep it read-only,
				// but still show the comparison overlay on your own /pa view.
				firstPage = observed;
				readOnlyScreen = current;
			}
		}
		if (!eligible(mc)) {
			detach();
			return;
		}
		if (current != screen) {
			detach();
			attach(current);
		} else if (linkInput == null || !current.children().contains(linkInput)) {
			clearWidgets();
			attach(current);
		}
	}

	/** Called from ContainerScreenMixin after the container and item tooltips. */
	public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
		if (!StsConfig.skillPickerEnabled()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (payload == null || !visible(mc)) {
			clearTextBounds();
			return;
		}
		ContainerScreen container = (ContainerScreen) mc.screen;
		ClassSelectionPage.Page page = ClassSelectionPage.page(container);
		Map<String, SkillTarget> targets = buildTargets(container, page);
		if (targets.isEmpty()) {
			clearTextBounds();
			return;
		}
		boolean matches = matchesPage(container, page);
		int color = parseColor(StsConfig.skillPickerHighlightColor());
		int fill = (color & 0x00FFFFFF) | 0x88000000;
		graphics.flush();
		RenderSystem.disableDepthTest();
		try {
			drawHighlights(graphics, container, targets, matches, fill, color);
			// Flush the slot overlays before drawing the font layer; both use
			// different render types and this keeps the highlight quads from
			// being ordered behind the text/item buffers.
			graphics.flush();
			drawTextList(graphics, mc, container, targets, color);
		} finally {
			graphics.flush();
			RenderSystem.enableDepthTest();
		}
	}

	private static boolean eligible(Minecraft mc) {
		Screen current = mc.screen;
		if (current == null) {
			return false;
		}
		ClassSelectionPage.Page page = ClassSelectionPage.page(current);
		if (page != ClassSelectionPage.Page.SKILLS && page != ClassSelectionPage.Page.SPEC) {
			return false;
		}
		boolean readOnlyView = current == readOnlyScreen;
		boolean otherView = (readOnlyView && viewingOther(mc))
			|| (ViewedPlayers.isAbilitiesView() && !ViewedPlayers.isAbilitiesViewSelf());
		return !otherView;
	}

	private static boolean visible(Minecraft mc) {
		return screen != null && mc.screen == screen && eligible(mc);
	}

	private static boolean viewingOther(Minecraft mc) {
		if (mc.player == null) {
			return false;
		}
		String self = mc.player.getGameProfile().getName();
		if (ViewedPlayers.isAbilitiesView()) {
			return !ViewedPlayers.isAbilitiesViewSelf();
		}
		String viewed = ViewedPlayersTracker.activeName();
		return viewed != null && !viewed.equalsIgnoreCase(self);
	}

	private static void attach(Screen current) {
		if (!(current instanceof ScreenWidgetAccessor access)) {
			return;
		}
		screen = current;
		Minecraft mc = Minecraft.getInstance();
		int linkX = StsConfig.skillPickerLinkX();
		int linkY = StsConfig.skillPickerLinkY();
		linkInput = new LinkEditBox(mc.font, linkX, linkY, LINK_WIDTH, LINK_HEIGHT, Component.literal("Build link"));
		linkInput.setMaxLength(2048);
		linkInput.setValue(linkValue);
		linkInput.setHint(Component.literal("Build link or token"));
		linkInput.setResponder(value -> linkValue = value);
		access.sparethesympathy$addWidget(linkInput);

		loadButton = Button.builder(Component.literal("Load"), button -> loadLink())
			.bounds(linkX + LINK_WIDTH + 4, linkY, LOAD_WIDTH, LINK_HEIGHT)
			.build();
		loadButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
			Component.literal("Load a build link or v1_ token")
		));
		access.sparethesympathy$addWidget(loadButton);
	}

	private static void detach() {
		clearWidgets();
		screen = null;
		payload = null;
		loading = false;
		linkValue = "";
		dragging = false;
		draggingText = false;
		clearTextBounds();
	}

	private static void clearWidgets() {
		if (screen instanceof ScreenWidgetAccessor access) {
			if (linkInput != null && screen.children().contains(linkInput)) {
				access.sparethesympathy$removeWidget(linkInput);
			}
			if (loadButton != null && screen.children().contains(loadButton)) {
				access.sparethesympathy$removeWidget(loadButton);
			}
		}
		linkInput = null;
		loadButton = null;
	}

	private static void applyInputPosition() {
		int x = StsConfig.skillPickerLinkX();
		int y = StsConfig.skillPickerLinkY();
		if (linkInput != null) {
			linkInput.setX(x);
			linkInput.setY(y);
		}
		if (loadButton != null) {
			loadButton.setX(x + LINK_WIDTH + 4);
			loadButton.setY(y);
		}
	}

	private static void loadLink() {
		if (loading || linkInput == null || Screen.hasControlDown()) {
			return;
		}
		String value = linkInput.getValue().trim();
		if (value.isEmpty()) {
			message("Paste a build link first.", ChatFormatting.YELLOW);
			return;
		}
		linkValue = value;
		loading = true;
		if (loadButton != null) {
			loadButton.active = false;
			loadButton.setMessage(Component.literal("..."));
		}
		Minecraft mc = Minecraft.getInstance();
		CompletableFuture.supplyAsync(() -> {
			try {
				BuildSkills loaded = BuildLink.load(value);
				List<StsApiClient.GameClass> classes = ArmouryTracker.classes();
				if (classes.isEmpty()) {
					try {
						classes = StsApiClient.fetchSkills();
					} catch (Exception ignored) {
						// IDs remain usable labels if the catalog is offline.
					}
				}
				return new Loaded(loaded, classes);
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}).whenComplete((loaded, error) -> mc.execute(() -> {
			loading = false;
			if (screen != mc.screen) {
				return;
			}
			if (loadButton != null) {
				loadButton.active = true;
				loadButton.setMessage(Component.literal("Load"));
			}
			if (error != null) {
				Throwable cause = error instanceof CompletionException && error.getCause() != null
					? error.getCause()
					: error;
				message("Could not load build: " + (cause.getMessage() == null ? "unknown error" : cause.getMessage()),
					ChatFormatting.RED);
				return;
			}
			payload = loaded.skills();
			catalog = loaded.catalog();
			message(
				"Loaded " + describeClass(payload) + " - " + sum(payload.skills()) + " class / "
					+ sum(payload.specSkills()) + " spec points.",
				ChatFormatting.GREEN
			);
		}));
	}

	private static Map<String, SkillTarget> buildTargets(ContainerScreen container, ClassSelectionPage.Page page) {
		Map<String, SkillTarget> targets = new LinkedHashMap<>();
		if (payload == null) {
			return targets;
		}
		boolean specPage = page == ClassSelectionPage.Page.SPEC;
		Map<String, Integer> points = specPage ? payload.specSkills() : payload.skills();
		Set<String> enhancedNames = new HashSet<>();
		if (!specPage && payload.enhancements() != null) {
			for (String id : payload.enhancements()) {
				String name = displayName(id, false);
				if (!name.isEmpty()) {
					enhancedNames.add(name);
				}
			}
		}
		for (Map.Entry<String, Integer> entry : points.entrySet()) {
			String name = displayName(entry.getKey(), specPage);
			if (!name.isEmpty()) {
				targets.merge(name, new SkillTarget(
					Math.max(1, Math.min(2, entry.getValue())),
					enhancedNames.contains(name)
				), (left, right) -> new SkillTarget(
					Math.max(left.points(), right.points()),
					left.enhanced() || right.enhanced()
				));
			}
		}
		// Include skills currently selected that the build omits, so the text
		// overlay and highlights can show extras that should be removed.
		for (String name : currentSkillNames(container)) {
			targets.putIfAbsent(name, new SkillTarget(0, false));
		}
		return targets;
	}

	private static List<String> currentSkillNames(ContainerScreen container) {
		List<String> names = new ArrayList<>();
		AbstractContainerMenu menu = container.getMenu();
		if (menu == null) {
			return names;
		}
		for (int i = 0; i < menu.slots.size(); i++) {
			LevelIcon icon = levelIcon(menu.getSlot(i).getItem());
			if (icon != null && !icon.skill.isEmpty() && !names.contains(icon.skill)) {
				names.add(icon.skill);
			}
		}
		return names;
	}

	private static void drawHighlights(GuiGraphics graphics, ContainerScreen container,
		Map<String, SkillTarget> targets, boolean matches, int fill, int border) {
		for (Map.Entry<String, SkillTarget> entry : targets.entrySet()) {
			SkillTarget target = entry.getValue();
			if (!matches && target.points() == 0) {
				// Don't ask the player to remove their whole current page when
				// the imported build is for another class/spec.
				continue;
			}
			SkillSlots slots = findSlots(container, entry.getKey());
			if (slots == null) {
				continue;
			}
			boolean changed = false;
			if (target.points() > slots.currentLevel) {
				for (int level = slots.currentLevel + 1; level <= target.points(); level++) {
					highlightSlot(graphics, container, level == 1 ? slots.level1Slot : slots.level2Slot, fill, border);
				}
				changed = true;
			} else if (target.points() < slots.currentLevel) {
				int slot = target.points() == 0 ? slots.abilitySlot
					: target.points() == 1 ? slots.level1Slot : slots.level2Slot;
				highlightSlot(graphics, container, slot, fill, border);
				changed = true;
			}
			if (slots.enhanced != target.enhanced()) {
				highlightSlot(graphics, container, slots.enhancementSlot, fill, border);
				changed = true;
			}
			if (changed) {
				// Also outline the skill's own icon so the whole row is easy
				// to spot, not just the small level pane.
				highlightSlot(graphics, container, slots.abilitySlot, fill, border);
			}
		}
	}

	private static void highlightSlot(GuiGraphics graphics, ContainerScreen container, int index, int fill, int border) {
		if (index < 0 || index >= container.getMenu().slots.size()) {
			return;
		}
		Slot slot = container.getMenu().getSlot(index);
		int x = container.leftPos + slot.x - 1;
		int y = container.topPos + slot.y - 1;
		graphics.fill(x, y, x + 18, y + 18, fill);
		graphics.fill(x, y, x + 18, y + 2, border);
		graphics.fill(x, y + 16, x + 18, y + 18, border);
		graphics.fill(x, y, x + 2, y + 18, border);
		graphics.fill(x + 16, y, x + 18, y + 18, border);
	}

	private static void drawTextList(GuiGraphics graphics, Minecraft mc, ContainerScreen container,
		Map<String, SkillTarget> targets, int highlightColor) {
		int x = draggingText ? textDragX : StsConfig.skillPickerRowX();
		int y = draggingText ? textDragY : StsConfig.skillPickerRowY();
		int startX = x;
		int startY = y;
		int lineHeight = mc.font.lineHeight + 2;
		int maxWidth = 0;
		int lines = 0;
		int mismatchColor = 0xFF000000 | (highlightColor & 0x00FFFFFF);
		for (Map.Entry<String, SkillTarget> entry : targets.entrySet()) {
			SkillSlots slots = findSlots(container, entry.getKey());
			int current = slots == null ? 0 : slots.currentLevel;
			boolean currentEnhanced = slots != null && slots.enhanced;
			SkillTarget target = entry.getValue();
			if (target.points() == 0 && current == 0 && !target.enhanced()) {
				continue;
			}
			boolean match = current == target.points() && currentEnhanced == target.enhanced();
			String label = entry.getKey() + " " + target.points() + (target.enhanced() ? "+" : "")
				+ (current > 0 ? " [x]" : " [ ]");
			if (!match) {
				label += " (" + current + (currentEnhanced ? "E" : "") + ")";
			}
			maxWidth = Math.max(maxWidth, mc.font.width(label));
			graphics.drawString(mc.font, label, x, y, match ? 0xFF7EE787 : mismatchColor, true);
			y += lineHeight;
			lines++;
		}
		textX = startX;
		textY = startY;
		textWidth = lines == 0 ? 0 : maxWidth;
		textHeight = lines * lineHeight;
	}

	private static boolean overText(double mouseX, double mouseY) {
		return textWidth > 0 && textHeight > 0
			&& mouseX >= textX && mouseX <= textX + textWidth
			&& mouseY >= textY && mouseY <= textY + textHeight;
	}

	private static void clearTextBounds() {
		textWidth = 0;
		textHeight = 0;
	}

	private static boolean matchesPage(ContainerScreen container, ClassSelectionPage.Page page) {
		if (payload == null) {
			return false;
		}
		String header = ClassSelectionPage.headerName(container);
		if (header.isEmpty()) {
			return true;
		}
		if (page == ClassSelectionPage.Page.SKILLS) {
			return payload.className() != null && header.equalsIgnoreCase(payload.className());
		}
		if (page == ClassSelectionPage.Page.SPEC) {
			return payload.specName() != null && header.equalsIgnoreCase(payload.specName());
		}
		return false;
	}

	private static SkillSlots findSlots(ContainerScreen container, String displayName) {
		AbstractContainerMenu menu = container.getMenu();
		if (menu == null || displayName == null) {
			return null;
		}
		int level1 = -1;
		int level2 = -1;
		boolean lit1 = false;
		boolean lit2 = false;
		int ability = -1;
		int enhancement = -1;
		boolean enhanced = false;
		String enhancementName = displayName + " [Enhancement]";
		for (int i = 0; i < menu.slots.size(); i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			LevelIcon icon = levelIcon(stack);
			if (icon != null && displayName.equals(icon.skill)) {
				if (icon.level == 1) {
					level1 = i;
					lit1 = icon.lit;
				} else if (icon.level == 2) {
					level2 = i;
					lit2 = icon.lit;
				}
			}
			String hover = stack.getHoverName().getString().trim();
			if (ability < 0 && displayName.equals(hover)) {
				ability = i;
			}
			if (enhancementName.equals(hover)) {
				enhancement = i;
				enhanced = enhanced(stack);
			}
		}
		if (level1 < 0 && level2 < 0) {
			// Names in level icons can be affected by resource packs. The class
			// GUI always lays ability -> level 1 -> level 2 out contiguously;
			// first try the ability slot, then the page's known grid layout.
			if (ability >= 0 && ability + 2 < menu.slots.size()) {
				level1 = ability + 1;
				level2 = ability + 2;
				lit1 = levelLit(menu.getSlot(level1).getItem());
				lit2 = levelLit(menu.getSlot(level2).getItem());
			} else {
				SkillSlots layout = findSlotsByLayout(container, displayName);
				if (layout != null) {
					return layout;
				}
			}
		}
		if (ability < 0 && level1 > 0) {
			ability = level1 - 1;
		}
		int current = lit2 ? 2 : lit1 ? 1 : 0;
		return new SkillSlots(ability, level1, level2, enhancement, current, enhanced);
	}

	/**
	 * Resolves a skill from the page's known grid when its item names/NBT are
	 * unavailable (for example a resource pack rewriting icon names). This is
	 * only a fallback; normal name-based lookup stays authoritative.
	 */
	private static SkillSlots findSlotsByLayout(ContainerScreen container, String displayName) {
		AbstractContainerMenu menu = container.getMenu();
		if (menu == null || displayName == null) {
			return null;
		}
		ClassSelectionPage.Page page = ClassSelectionPage.page(container);
		if (page != ClassSelectionPage.Page.SKILLS && page != ClassSelectionPage.Page.SPEC) {
			return null;
		}
		String header = ClassSelectionPage.headerName(container);
		String wantedClass = header.isEmpty() ? (payload == null ? null : payload.className()) : header;
		String wantedSpec = header.isEmpty() ? (payload == null ? null : payload.specName()) : header;
		for (StsApiClient.GameClass gameClass : catalog) {
			if (page == ClassSelectionPage.Page.SKILLS && wantedClass != null
				&& !gameClass.className().equalsIgnoreCase(wantedClass)) {
				continue;
			}
			if (page == ClassSelectionPage.Page.SKILLS) {
				for (int i = 0; i < gameClass.skills().size(); i++) {
					if (!gameClass.skills().get(i).displayName().equals(displayName)) {
						continue;
					}
					String key = ClassSelectionPage.pageKey(container);
					boolean ring;
					if (key != null) {
						ring = key.endsWith("_2_2");
					} else {
						ItemStack left = menu.getSlot(9).getItem();
						ItemStack right = menu.getSlot(10).getItem();
						ring = looksLikeIcon(left) || !looksLikeIcon(right);
					}
					int row = i / 2 + 1;
					int column = i % 2 == 0 ? (ring ? 0 : 1) : 5;
					int ability = row * 9 + column;
					int level1 = ability + 1;
					int level2 = ability + 2;
					int enhancement = ring ? ability + 3 : -1;
					return new SkillSlots(
						ability,
						level1,
						level2,
						enhancement,
						levelLit(menu.getSlot(level1).getItem()) ? (levelLit(menu.getSlot(level2).getItem()) ? 2 : 1) : 0,
						enhancement >= 0 && enhanced(menu.getSlot(enhancement).getItem())
					);
				}
			} else {
				for (StsApiClient.Spec spec : gameClass.specs()) {
					if (wantedSpec != null && !spec.specName().equalsIgnoreCase(wantedSpec)) {
						continue;
					}
					for (int i = 0; i < spec.specSkills().size(); i++) {
						if (!spec.specSkills().get(i).displayName().equals(displayName)) {
							continue;
						}
						int row = i + 2;
						int column = i + 2;
						int ability = row * 9 + column;
						int level1 = ability + 1;
						int level2 = ability + 2;
						return new SkillSlots(
							ability,
							level1,
							level2,
							-1,
							levelLit(menu.getSlot(level1).getItem()) ? (levelLit(menu.getSlot(level2).getItem()) ? 2 : 1) : 0,
							false
						);
					}
				}
			}
		}
		return null;
	}

	private static boolean looksLikeIcon(ItemStack stack) {
		return stack != null && !stack.isEmpty() && !stack.getHoverName().getString().trim().isEmpty();
	}

	private static boolean levelLit(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag != null && tag.contains("Gui")) {
			String texture = tag.getCompound("Gui").getString("texture");
			if (!texture.isEmpty()) {
				return texture.endsWith("_lit");
			}
		}
		return stack.is(Items.LIME_STAINED_GLASS_PANE);
	}

	private static boolean enhanced(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag != null && tag.contains("Gui")) {
			String texture = tag.getCompound("Gui").getString("texture");
			if (!texture.isEmpty()) {
				return texture.endsWith("en_lit");
			}
		}
		return stack.is(Items.YELLOW_STAINED_GLASS_PANE);
	}

	private static LevelIcon levelIcon(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag != null && tag.contains("Gui")) {
			CompoundTag gui = tag.getCompound("Gui");
			String skill = gui.getString("skill");
			String level = gui.getString("level");
			if (!skill.isEmpty() && ("1".equals(level) || "2".equals(level))) {
				String texture = gui.getString("texture");
				boolean lit = texture == null || texture.isEmpty()
					? stack.is(Items.LIME_STAINED_GLASS_PANE)
					: texture.endsWith("_lit");
				return new LevelIcon(skill, level.charAt(0) - '0', lit);
			}
		}
		int level = ClassSelectionPage.levelOf(stack);
		if (level == 0) {
			return null;
		}
		String name = stack.getHoverName().getString().trim();
		int marker = name.lastIndexOf(" [Lv. ");
		String skill = marker > 0 ? name.substring(0, marker).trim() : "";
		return new LevelIcon(skill, level, stack.is(Items.LIME_STAINED_GLASS_PANE));
	}

	private static String displayName(String id, boolean spec) {
		for (StsApiClient.GameClass gameClass : catalog) {
			if (!spec && payload != null && payload.className() != null
				&& !gameClass.className().equalsIgnoreCase(payload.className())) {
				continue;
			}
			if (!spec) {
				for (StsApiClient.Ability ability : gameClass.skills()) {
					if (ability.scoreboardId().equals(id)) {
						return ability.displayName();
					}
				}
			}
			for (StsApiClient.Spec gameSpec : gameClass.specs()) {
				if (spec && payload != null && payload.specName() != null
					&& !gameSpec.specName().equalsIgnoreCase(payload.specName())) {
					continue;
				}
				for (StsApiClient.Ability ability : gameSpec.specSkills()) {
					if (ability.scoreboardId().equals(id)) {
						return ability.displayName();
					}
				}
			}
		}
		return id;
	}

	private static String describeClass(BuildSkills skills) {
		if (skills == null) {
			return "build";
		}
		String className = skills.className() == null ? "?" : capitalize(skills.className());
		return skills.specName() == null || skills.specName().isEmpty()
			? className
			: className + " / " + capitalize(skills.specName());
	}

	private static String capitalize(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
	}

	private static int sum(Map<String, Integer> points) {
		int total = 0;
		for (int value : points.values()) {
			total += value;
		}
		return total;
	}

	private static int parseColor(String raw) {
		String value = raw == null ? "" : raw.trim();
		if (value.startsWith("#")) {
			value = value.substring(1);
		}
		if (value.length() == 6) {
			value = "FF" + value;
		}
		if (value.length() != 8) {
			return 0xFFFFD24A;
		}
		try {
			return (int) Long.parseLong(value, 16);
		} catch (NumberFormatException e) {
			return 0xFFFFD24A;
		}
	}

	private static boolean over(AbstractWidget widget, double mouseX, double mouseY) {
		return widget != null
			&& mouseX >= widget.getX() && mouseX <= widget.getX() + widget.getWidth()
			&& mouseY >= widget.getY() && mouseY <= widget.getY() + widget.getHeight();
	}

	private static void message(String text, ChatFormatting color) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("STS: " + text).withStyle(color), false);
		}
	}

	private record Loaded(BuildSkills skills, List<StsApiClient.GameClass> catalog) {
	}

	private record SkillSlots(int abilitySlot, int level1Slot, int level2Slot,
		int enhancementSlot, int currentLevel, boolean enhanced) {
	}

	private record SkillTarget(int points, boolean enhanced) {
	}

	private record LevelIcon(String skill, int level, boolean lit) {
	}

	private static final class LinkEditBox extends EditBox {
		private LinkEditBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, Component message) {
			super(font, x, y, width, height, message);
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				loadLink();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
	}
}
