package rift.launcher.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import rift.launcher.ui.theme.RiftTheme;

/**
 * A pill showing a state: a coloured dot and a short label.
 *
 * <p>Colour carries the meaning at a glance and the text confirms it, so the badge still works for
 * anyone who can't separate the hues — colour alone would make "ready" and "error" identical to them.
 */
public class StatusBadge extends JComponent
{
	private String text;
	private Color color;

	public StatusBadge(String text, Color color)
	{
		set(text, color);
		setFont(RiftTheme.bold(11));
	}

	public void set(String text, Color color)
	{
		this.text = text == null ? "" : text;
		this.color = color == null ? RiftTheme.TEXT_MUTED : color;
		revalidate();
		repaint();
	}

	@Override
	public Dimension getPreferredSize()
	{
		FontMetrics fm = getFontMetrics(getFont());
		return new Dimension(fm.stringWidth(text) + 34, 24);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int h = getHeight();
		int w = getWidth();

		// A tint of the status colour rather than the colour itself: a fully saturated pill would
		// shout louder than the account name next to it.
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
		g.fillRoundRect(0, 0, w, h, h, h);
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
		g.drawRoundRect(0, 0, w - 1, h - 1, h, h);

		g.setColor(color);
		g.fillOval(10, h / 2 - 3, 6, 6);

		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		g.drawString(text, 22, (h + fm.getAscent() - fm.getDescent()) / 2);
		g.dispose();
	}
}
