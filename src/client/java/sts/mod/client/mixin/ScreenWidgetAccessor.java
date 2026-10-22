package sts.mod.client.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the class-selection skill picker manage its widgets on any Screen. */
@Mixin(Screen.class)
public interface ScreenWidgetAccessor {
	@Invoker("addRenderableWidget")
	<T extends GuiEventListener & Renderable & NarratableEntry> T sparethesympathy$addWidget(T widget);

	@Invoker("removeWidget")
	void sparethesympathy$removeWidget(GuiEventListener widget);
}
