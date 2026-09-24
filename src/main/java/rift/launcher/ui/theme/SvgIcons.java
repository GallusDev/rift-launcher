package rift.launcher.ui.theme;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.Icon;
import lombok.extern.slf4j.Slf4j;

/**
 * The interface icons, rendered from the SVG art in {@code resources/rift/launcher/icons/}.
 *
 * <p>The art is single-colour black, which would be invisible on this theme, so every icon is
 * recoloured to whatever the caller asks for. That is also what lets one file serve every state: the
 * navigation rail asks for the same glyph muted, bright, and in the accent colour as the pointer and
 * selection move.
 *
 * <p>Vector rather than raster so the icons stay sharp at any display scaling, and so a size change
 * is a number rather than a re-export.
 */
@Slf4j
public final class SvgIcons
{
	/** Each glyph is one file in the icons folder, named after the constant. */
	public enum Glyph
	{
		HOME("home"),
		PROXIES("proxies"),
		PLUGINS("plugins"),
		SETTINGS("settings"),
		USER("user");

		private final String file;

		Glyph(String file)
		{
			this.file = file;
		}
	}

	/** No leading slash: FlatSVGIcon resolves this through a ClassLoader, not Class.getResource. */
	private static final String ROOT = "rift/launcher/icons/";

	private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();

	private SvgIcons()
	{
	}

	/**
	 * The glyph at {@code size} pixels square, drawn entirely in {@code color}.
	 *
	 * <p>Never null. A missing file yields a blank icon of the right size rather than an exception or a
	 * gap, matching how the rest of the launcher treats absent art: layout holds, and one lost icon
	 * cannot stop the window opening.
	 */
	public static Icon of(Glyph glyph, int size, Color color)
	{
		return CACHE.computeIfAbsent(glyph + "@" + size + "#" + color.getRGB(), k -> create(glyph, size, color));
	}

	private static Icon create(Glyph glyph, int size, Color color)
	{
		FlatSVGIcon icon = new FlatSVGIcon(ROOT + glyph.file + ".svg", size, size, SvgIcons.class.getClassLoader());
		if (!icon.hasFound())
		{
			log.warn("Icon art missing: {}{}.svg", ROOT, glyph.file);
			return new Blank(size);
		}
		// Every colour maps to the requested one, not only black. The sources happen to be pure #000
		// today, but art re-exported in a dark grey would otherwise be recoloured only where it
		// matched, and render near-invisible everywhere else. The source alpha is kept so any
		// deliberately translucent detail stays translucent.
		icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> new Color(color.getRed(), color.getGreen(),
			color.getBlue(), c.getAlpha() * color.getAlpha() / 255)));
		return icon;
	}

	/** Holds the space an icon would have taken. */
	private static final class Blank implements Icon
	{
		private final int size;

		Blank(int size)
		{
			this.size = size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
		}

		@Override
		public int getIconWidth()
		{
			return size;
		}

		@Override
		public int getIconHeight()
		{
			return size;
		}
	}
}
