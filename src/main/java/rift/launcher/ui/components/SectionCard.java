package rift.launcher.ui.components;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import rift.launcher.ui.theme.RiftTheme;

/**
 * A titled panel painted like the account cards: rounded, raised, hairline border.
 *
 * <p>Exists so settings and proxies sit on the same surface language as the rest of the window.
 * Grouping controls under a heading on a card is also what makes a settings screen scannable — a flat
 * run of labelled rows gives the eye nowhere to rest.
 */
public class SectionCard extends JPanel
{
	private final JPanel body = new JPanel();

	public SectionCard(String title, String subtitle)
	{
		setLayout(new BorderLayout());
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);

		JLabel heading = new JLabel(title);
		heading.setFont(RiftTheme.bold(15));
		heading.setForeground(RiftTheme.TEXT);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		header.add(heading);

		if (subtitle != null && !subtitle.isEmpty())
		{
			JLabel sub = new JLabel(subtitle);
			sub.setFont(RiftTheme.regular(12));
			sub.setForeground(RiftTheme.TEXT_FAINT);
			sub.setAlignmentX(LEFT_ALIGNMENT);
			header.add(Box.createVerticalStrut(3));
			header.add(sub);
		}
		header.add(Box.createVerticalStrut(12));
		add(header, BorderLayout.NORTH);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		add(body, BorderLayout.CENTER);
	}

	/** Adds a row of content, left-aligned so stacked rows line up. */
	public SectionCard add(JComponent component)
	{
		component.setAlignmentX(LEFT_ALIGNMENT);
		body.add(component);
		return this;
	}

	public SectionCard gap(int height)
	{
		body.add(Box.createVerticalStrut(height));
		return this;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int arc = RiftTheme.RADIUS;
		g.setColor(RiftTheme.SURFACE_RAISED);
		g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
		g.setColor(RiftTheme.BORDER);
		g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
		g.dispose();
		super.paintComponent(graphics);
	}
}
