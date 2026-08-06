package rift.launcher.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JComponent;
import rift.launcher.ui.theme.RiftTheme;

/**
 * A sidebar navigation entry: icon, label, and a selected state.
 *
 * <p>Selection is shown three ways at once — a filled panel, an accent bar on the leading edge, and
 * brighter text — because in a dark UI any one of them alone is easy to miss, and "which screen am I
 * on?" should never need a second look.
 */
public class NavButton extends JComponent
{
	private final String label;
	private final Icon icon;
	private final Runnable onClick;
	private boolean selected;
	private boolean hovered;

	public NavButton(String label, Icon icon, Runnable onClick)
	{
		this.label = label;
		this.icon = icon;
		this.onClick = onClick;
		setFont(RiftTheme.bold(14));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}
		});
	}

	public void setSelected(boolean selected)
	{
		this.selected = selected;
		repaint();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(RiftTheme.SIDEBAR_WIDTH - 24, 48);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, 48);
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

		if (selected)
		{
			g.setColor(RiftTheme.ACCENT_GLOW);
			g.fillRoundRect(0, 0, w, h, arc, arc);
			g.setColor(RiftTheme.ACCENT);
			g.fillRoundRect(0, 10, 3, h - 20, 3, 3);
		}
		else if (hovered)
		{
			g.setColor(RiftTheme.SURFACE_HOVER);
			g.fillRoundRect(0, 0, w, h, arc, arc);
		}

		int iconX = 16;
		if (icon != null)
		{
			icon.paintIcon(this, g, iconX, (h - icon.getIconHeight()) / 2);
		}

		Color textColor = selected ? RiftTheme.TEXT : hovered ? RiftTheme.TEXT : RiftTheme.TEXT_MUTED;
		g.setColor(textColor);
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		int textX = iconX + (icon == null ? 0 : icon.getIconWidth() + 14);
		g.drawString(label, textX, (h + fm.getAscent() - fm.getDescent()) / 2);
		g.dispose();
	}
}
