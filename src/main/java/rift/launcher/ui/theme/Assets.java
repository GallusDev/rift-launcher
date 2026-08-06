package rift.launcher.ui.theme;

import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/**
 * Loads the branding art, scaled and cached.
 *
 * <p>Everything is optional: a missing file falls back to something painted rather than leaving a
 * hole or throwing, so the launcher still opens if an asset is renamed or dropped. Art lives in
 * {@code resources/rift/launcher/brand/} and is picked up on the next build with no code change.
 *
 * <p>Source art is generated at ~1500px on a large transparent canvas; it is cropped and resized
 * into the resource folder ahead of time (see the brand README) rather than at runtime, so startup
 * does no image processing.
 */
public final class Assets
{
	private static final String BRAND = "/rift/launcher/brand/";
	private static final Map<String, Image> IMAGES = new HashMap<>();
	private static final Map<String, Icon> ICONS = new HashMap<>();

	private Assets()
	{
	}

	/** The full "RIFT" wordmark, for the sidebar header. */
	public static Image logo()
	{
		return load("logo.png");
	}

	/** A compact wordmark, for tight spaces such as the footer. */
	public static Image logoSmall()
	{
		return load("logo-small.png");
	}

	/** The atmospheric texture behind the window, or null to fall back to a flat colour. */
	public static Image background()
	{
		return load("background.png");
	}

	/**
	 * A navigation icon scaled to {@code size}, or {@code null} if that art is missing — callers then
	 * fall back to {@link RiftIcons}, so navigation still works without the pack.
	 */
	public static Icon icon(String name, int size)
	{
		String key = name + "@" + size;
		if (ICONS.containsKey(key))
		{
			return ICONS.get(key);
		}
		Image image = load("icons/" + name + ".png");
		Icon icon = image == null ? null
			: new ImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH));
		ICONS.put(key, icon);
		return icon;
	}

	/**
	 * The home banner, or a painted stand-in at the requested size.
	 *
	 * <p>Returned at the exact size asked for: the source is far wider than the window, and scaling to
	 * fit would letterbox it, so callers crop to the region they need.
	 */
	public static Image hero(int width, int height)
	{
		Image supplied = load("hero.png");
		if (supplied != null)
		{
			return supplied;
		}
		if (width <= 0 || height <= 0)
		{
			return null;
		}

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, new java.awt.Color(0x1A, 0x10, 0x2E),
			width, height, new java.awt.Color(0x0B, 0x0A, 0x0F)));
		g.fillRect(0, 0, width, height);

		int cx = (int) (width * 0.62);
		for (int i = 220; i > 0; i -= 4)
		{
			g.setColor(new java.awt.Color(0x8B, 0x5C, 0xF6, Math.max(0, 26 - i / 12)));
			g.fillOval(cx - i / 2, height / 2 - i, i, i * 2);
		}
		g.setColor(new java.awt.Color(0xC4, 0xB5, 0xFD, 150));
		g.fillRect(cx - 1, 0, 2, height);
		g.dispose();
		return img;
	}

	private static Image load(String name)
	{
		if (IMAGES.containsKey(name))
		{
			return IMAGES.get(name);
		}
		Image image = null;
		try
		{
			URL url = Assets.class.getResource(BRAND + name);
			if (url != null)
			{
				image = ImageIO.read(url);
			}
		}
		catch (Exception ex)
		{
			// A broken asset must never stop the launcher opening.
			image = null;
		}
		IMAGES.put(name, image);
		return image;
	}
}
