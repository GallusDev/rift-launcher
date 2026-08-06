package rift.launcher.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JButton;
import rift.launcher.ui.theme.RiftTheme;

/**
 * A flat, rounded button painted from the theme rather than the platform look-and-feel.
 *
 * <p>Two weights: {@code primary} carries a violet gradient and is used once per screen for the
 * action people came to perform; {@code secondary} is outlined and used for everything else. Keeping
 * the filled treatment rare is what makes it read as emphasis.
 */
public class PrimaryButton extends JButton
{
	private final boolean primary;
	private boolean hovered;
	private boolean pressed;

	public PrimaryButton(String text, Icon icon, boolean primary)
	{
		super(text);
		this.primary = primary;
		setIcon(icon);
		setIconTextGap(9);
		setFont(RiftTheme.bold(13));
		setForeground(RiftTheme.TEXT);
		setFocusPainted(false);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setHorizontalAlignment(CENTER);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hovered = false;
				pressed = false;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				pressed = true;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				pressed = false;
				repaint();
			}
		});
	}

	@Override
	public Dimension getPreferredSize()
	{
		FontMetrics fm = getFontMetrics(getFont());
		int textWidth = getText() == null ? 0 : fm.stringWidth(getText());
		int iconWidth = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
		return new Dimension(textWidth + iconWidth + 40, 40);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();
		int arc = RiftTheme.RADIUS_SMALL + 2;

		if (!isEnabled())
		{
			g.setColor(RiftTheme.SURFACE_RAISED);
			g.fillRoundRect(0, 0, w, h, arc, arc);
			g.setColor(RiftTheme.TEXT_FAINT);
		}
		else if (primary)
		{
			// A subtle vertical gradient reads as a lit surface; hover brightens rather than shifts hue,
			// so the button never looks like a different control.
			Color top = hovered ? RiftTheme.ACCENT_BRIGHT : RiftTheme.ACCENT;
			Color bottom = hovered ? RiftTheme.ACCENT : RiftTheme.ACCENT_DEEP;
			if (pressed)
			{
				top = RiftTheme.ACCENT_DEEP;
				bottom = RiftTheme.ACCENT_DEEP;
			}
			g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
			g.fillRoundRect(0, 0, w, h, arc, arc);
			g.setColor(RiftTheme.TEXT);
		}
		else
		{
			g.setColor(hovered ? RiftTheme.SURFACE_HOVER : RiftTheme.SURFACE_RAISED);
			g.fillRoundRect(0, 0, w, h, arc, arc);
			g.setColor(hovered ? RiftTheme.BORDER_ACCENT : RiftTheme.BORDER);
			g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
			g.setColor(hovered ? RiftTheme.TEXT : RiftTheme.TEXT_MUTED);
		}

		FontMetrics fm = g.getFontMetrics(getFont());
		int textWidth = getText() == null ? 0 : fm.stringWidth(getText());
		int iconWidth = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
		int x = (w - textWidth - iconWidth) / 2;

		if (getIcon() != null)
		{
			getIcon().paintIcon(this, g, x, (h - getIcon().getIconHeight()) / 2);
			x += iconWidth;
		}
		g.setFont(getFont());
		g.drawString(getText(), x, (h + fm.getAscent() - fm.getDescent()) / 2);
		g.dispose();
	}
}
