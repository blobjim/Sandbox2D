package ritzow.solomon.engine.graphics;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;

public final class Textures {
	public static Texture GRASS;
	public static Texture BLUE_SQUARE;
	public static Texture RED_SQUARE;
	public static Texture GREEN_FACE;
	public static Texture CLOUDS;
	public static Texture DIRT;
	
	public static void loadAll(File directory) throws IOException {
		Textures.BLUE_SQUARE = loadTexture(new File(directory, "blueSquare.png"));
		Textures.RED_SQUARE = loadTexture(new File(directory, "redSquare.png"));
		Textures.GREEN_FACE = loadTexture(new File(directory, "greenFace.png"));
		Textures.DIRT = loadTexture(new File(directory, "dirt.png"));
		Textures.GRASS = loadTexture(new File(directory, "grass.png"));
		Textures.CLOUDS = loadTexture(new File(directory, "clouds.png"));
	}
	
	public static Texture loadTexture(File file) throws IOException {
		InputStream data = new FileInputStream(file);
		PNGDecoder decoder = new PNGDecoder(data);
		ByteBuffer pixels = BufferUtils.createByteBuffer(decoder.getWidth() * decoder.getHeight() * 4);
		decoder.decodeFlipped(pixels, decoder.getWidth() * 4, Format.RGBA);
		pixels.flip();
		data.close();
		return new Texture(pixels, decoder.getWidth(), decoder.getHeight());
	}
}