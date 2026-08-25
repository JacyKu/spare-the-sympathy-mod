package sts.mod.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import sts.mod.SpareTheSympathy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

/**
 * Renders an item stack exactly like an inventory GUI icon into the game's
 * own main framebuffer (the vanilla render path, with correct lighting and
 * transforms) and reads the pixels back. The icon space uses a wide ortho so
 * nothing clips; the content is fitted into the uniform 64px sheet cell by
 * the sheet writer.
 */
public final class IconRenderer {
	/**
	 * Fixed capture size: the icon space is rendered with a wide ortho
	 * (48 units, 3x the icon box) so no model can ever clip.
	 */
	public static final int CAPTURE_SIZE = 192;

	/** Returns CAPTURE_SIZE*CAPTURE_SIZE ints in NativeImage RGBA packing. */
	public int[] render(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		BakedModel model = minecraft.getItemRenderer().getModel(stack, null, null, 0);
		return render(stack, model);
	}

	/** Renders with a custom baked model (used for per-frame flat item models). */
	public int[] render(ItemStack stack, BakedModel model) {
		Minecraft minecraft = Minecraft.getInstance();
		RenderTarget main = minecraft.getMainRenderTarget();
		Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
		VertexSorting savedSorting = RenderSystem.getVertexSorting();
		try {
			main.bindWrite(false);
			main.bindRead();
			GlStateManager._viewport(0, 0, CAPTURE_SIZE, CAPTURE_SIZE);
			GlStateManager._clearColor(0.0F, 0.0F, 0.0F, 0.0F);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

			// Wide ortho: 48 units around the 16-unit icon space (3x), so no
			// model can ever be clipped by the capture.
			RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-16.0F, 32.0F, 32.0F, -16.0F, 1000.0F, 3000.0F), VertexSorting.ORTHOGRAPHIC_Z);
			PoseStack modelViewStack = RenderSystem.getModelViewStack();
			modelViewStack.pushPose();
			modelViewStack.setIdentity();
			modelViewStack.translate(0.0F, 0.0F, -2000.0F);
			RenderSystem.applyModelViewMatrix();

			GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
			if (FrameItemModel.isFrameModel(model)) {
				renderFlatQuads(graphics, model);
			} else {
				renderItemVanilla(graphics, stack, model);
			}

			modelViewStack.popPose();
			RenderSystem.applyModelViewMatrix();

			return readPixels(CAPTURE_SIZE);
		} finally {
			RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
			GlStateManager._viewport(0, 0, main.width, main.height);
			minecraft.getMainRenderTarget().bindWrite(false);
		}
	}

	/**
	 * Renders a baked model with an explicit pose through the vanilla item
	 * renderer (3D models: directional quads, transforms, block lighting).
	 * The flush must happen while the flat lighting is active (the lighting
	 * state is applied when the buffered quads are drawn).
	 */
	private static void renderItemVanilla(GuiGraphics graphics, ItemStack stack, BakedModel model) {
		Minecraft minecraft = Minecraft.getInstance();
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(8.0F, 8.0F, 150.0F);
		pose.mulPoseMatrix(new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
		pose.scale(16.0F, 16.0F, 16.0F);
		boolean flat = !model.usesBlockLight();
		if (flat) {
			Lighting.setupForFlatItems();
		}
		minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, graphics.bufferSource(),
			15728880, OverlayTexture.NO_OVERLAY, model);
		graphics.flush();
		if (flat) {
			Lighting.setupFor3DItems();
		}
		pose.popPose();
	}

	/**
	 * Draws the flat per-frame model's quads directly into the buffer. The
	 * FrameModel only emits quads for the null direction; the item render
	 * type binds the block atlas in its setup state.
	 */
	private static void renderFlatQuads(GuiGraphics graphics, BakedModel model) {
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(8.0F, 8.0F, 150.0F);
		pose.mulPoseMatrix(new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
		pose.scale(16.0F, 16.0F, 16.0F);
		Lighting.setupForFlatItems();
		VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.itemEntityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
		for (BakedQuad quad : model.getQuads(null, null, RandomSource.create())) {
			consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 15728880, OverlayTexture.NO_OVERLAY);
		}
		graphics.flush();
		Lighting.setupFor3DItems();
		pose.popPose();
	}

	public static void writeDebugPng(String fileName, int[] pixels, int size) {
		try (NativeImage image = new NativeImage(size, size, true)) {
			for (int row = 0; row < size; row++) {
				for (int col = 0; col < size; col++) {
					image.setPixelRGBA(col, row, pixels[row * size + col]);
				}
			}
			image.writeToFile(Path.of(fileName));
		} catch (IOException exception) {
			SpareTheSympathy.LOGGER.warn("Failed to write debug png", exception);
		}
	}

	private static int[] readPixels(int size) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder());
		GlStateManager._readPixels(0, 0, size, size, GlConst.GL_RGBA, GlConst.GL_UNSIGNED_BYTE, buffer);
		int[] pixels = new int[size * size];
		buffer.rewind();
		for (int row = 0; row < size; row++) {
			int srcRow = size - 1 - row;
			for (int col = 0; col < size; col++) {
				int index = (srcRow * size + col) * 4;
				int red = buffer.get(index) & 0xFF;
				int green = buffer.get(index + 1) & 0xFF;
				int blue = buffer.get(index + 2) & 0xFF;
				int alpha = buffer.get(index + 3) & 0xFF;
				pixels[row * size + col] = red | green << 8 | blue << 16 | alpha << 24;
			}
		}
		return pixels;
	}
}
