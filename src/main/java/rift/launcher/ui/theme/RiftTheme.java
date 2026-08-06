package rift.launcher.ui.theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * The launcher's visual language in one place: colours, type and metrics.
 *
 * <p>Centralised so the look stays consistent as screens are added — every panel, badge and button
 * reads from here rather than hard-coding a hex value, which is what makes a UI drift into looking
 * assembled rather than designed.
 */
public final class RiftTheme
{
	// --- Surfaces. A near-black base with progressively lighter panels gives depth without borders.
	public static final Color BG = new Color(0x0B, 0x0A, 0x0F);
	public static final Color SIDEBAR = new Color(0x0E, 0x0C, 0x14);
	public static final Color SURFACE = new Color(0x14, 0x11, 0x1D);
	public static final Color SURFACE_RAISED = new Color(0x1A, 0x16, 0x25);
	public static final Color SURFACE_HOVER = new Color(0x22, 0x1C, 0x30);

	// --- Accent. One purple, used sparingly, so it still reads as emphasis where it appears.
	public static final Color ACCENT = new Color(0x8B, 0x5C, 0xF6);
	public static final Color ACCENT_BRIGHT = new Color(0xA7, 0x8B, 0xFA);
	public static final Color ACCENT_DEEP = new Color(0x6D, 0x35, 0xD1);
	public static final Color ACCENT_GLOW = new Color(0x8B, 0x5C, 0xF6, 40);

	// --- Text. Three weights of emphasis is enough; more turns into mush.
	public static final Color TEXT = new Color(0xF3, 0xF1, 0xF8);
	public static final Color TEXT_MUTED = new Color(0x9C, 0x96, 0xAD);
	public static final Color TEXT_FAINT = new Color(0x6B, 0x65, 0x7C);

	// --- Semantic. Deliberately separate from the accent so status never competes with branding.
	public static final Color OK = new Color(0x4A, 0xDE, 0x80);
	public static final Color WARN = new Color(0xFB, 0xBF, 0x24);
	public static final Color ERROR = new Color(0xF8, 0x71, 0x71);

	public static final Color BORDER = new Color(0x2A, 0x24, 0x38);
	public static final Color BORDER_ACCENT = new Color(0x8B, 0x5C, 0xF6, 90);

	// --- Metrics.
	public static final int RADIUS = 12;
	public static final int RADIUS_SMALL = 8;
	public static final int PAD = 16;
	public static final int SIDEBAR_WIDTH = 232;

	private static final String PREFERRED = "Segoe UI";

	private RiftTheme()
	{
	}

	public static Font font(int size, int style)
	{
		return new Font(available(PREFERRED) ? PREFERRED : Font.SANS_SERIF, style, size);
	}

	public static Font regular(int size)
	{
		return font(size, Font.PLAIN);
	}

	public static Font bold(int size)
	{
		return font(size, Font.BOLD);
	}

	private static boolean available(String name)
	{
		for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
		{
			if (f.equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}

	/** Blends two colours, for hover states and gradients without inventing new constants. */
	public static Color mix(Color a, Color b, float t)
	{
		return new Color(
			Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}
}
