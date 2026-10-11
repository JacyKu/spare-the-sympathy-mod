package sts.mod.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import sts.mod.api.BuildNames;

/**
 * The naming prompt used by the upload/export buttons (the Mechanical Armory
 * and the /ps, /pa, /vc view screens). It is pre-filled with the default
 * build name; Enter (or Save) confirms, Esc cancels. Either way the screen
 * that opened the prompt comes back - the server-side container stayed open
 * the whole time, so re-opening the parent screen restores it.
 */
public final class BuildNameScreen extends Screen {
	private final Screen parent;
	private final String defaultName;
	private final Consumer<String> onConfirm;
	private EditBox nameField;

	private BuildNameScreen(Screen parent, String defaultName, Consumer<String> onConfirm) {
		super(Component.translatable("sts.naming.title"));
		this.parent = parent;
		this.defaultName = defaultName == null ? "" : defaultName;
		this.onConfirm = onConfirm;
	}

	/** Opens the prompt over the current screen. Does nothing without one. */
	public static void open(String defaultName, Consumer<String> onConfirm) {
		Minecraft mc = Minecraft.getInstance();
		Screen parent = mc.screen;
		if (parent == null) {
			return;
		}
		mc.setScreen(new BuildNameScreen(parent, defaultName, onConfirm));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		nameField = new EditBox(
			this.font,
			centerX - 100,
			centerY - 34,
			200,
			20,
			Component.translatable("sts.naming.field")
		);
		nameField.setMaxLength(BuildNames.MAX_NAME);
		nameField.setValue(defaultName);
		addRenderableWidget(nameField);
		setInitialFocus(nameField);
		addRenderableWidget(
			Button.builder(Component.translatable("sts.naming.save"), b -> confirm())
				.bounds(centerX - 102, centerY - 4, 100, 20)
				.build()
		);
		addRenderableWidget(
			Button.builder(Component.translatable("sts.naming.cancel"), b -> onClose())
				.bounds(centerX + 2, centerY - 4, 100, 20)
				.build()
		);
	}

	private void confirm() {
		String name = BuildNames.resolve(nameField == null ? null : nameField.getValue(), defaultName);
		Minecraft.getInstance().setScreen(parent);
		onConfirm.accept(name);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			confirm();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		int centerX = this.width / 2;
		int centerY = this.height / 2;
		graphics.drawCenteredString(this.font, this.title, centerX, centerY - 64, 0xFFFFFF);
		graphics.drawCenteredString(
			this.font,
			Component.translatable("sts.naming.hint"),
			centerX,
			centerY - 50,
			0xA0A0A0
		);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
