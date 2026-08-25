package sts.mod.client.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public final class SpritesheetRenderer {
	private final int spriteSize;
	private final BufferedImage image;

	public SpritesheetRenderer(int width, int height, int spriteSize) {
		this.spriteSize = spriteSize;
		this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}

	public void writeCell(byte[] rgba, int x, int y) {
		for (int row = 0; row < this.spriteSize; row++) {
			for (int col = 0; col < this.spriteSize; col++) {
				int index = (row * this.spriteSize + col) * 4;
				int argb = (rgba[index + 3] & 0xFF) << 24
					| (rgba[index] & 0xFF) << 16
					| (rgba[index + 1] & 0xFF) << 8
					| (rgba[index + 2] & 0xFF);
				this.image.setRGB(x + col, y + row, argb);
			}
		}
	}

	public void writeFrame(byte[] rgba, int width, int height, int x, int y) {
		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				int index = (row * width + col) * 4;
				int argb = (rgba[index + 3] & 0xFF) << 24
					| (rgba[index] & 0xFF) << 16
					| (rgba[index + 1] & 0xFF) << 8
					| (rgba[index + 2] & 0xFF);
				this.image.setRGB(x + col, y + row, argb);
			}
		}
	}

	public void writeToPng(Path pngPath) throws IOException {
		ImageIO.write(this.image, "png", pngPath.toFile());
	}
}
