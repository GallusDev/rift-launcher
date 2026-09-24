package rift.launcher.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import rift.launcher.ui.PluginRows;
import rift.launcher.ui.theme.RiftTheme;
import rift.launcher.ui.theme.SvgIcons;

/**
 * One plugin on the Plugins page: its name, how it is available, and the detail that matters for
 * that -- when access ends, or when a local build was deployed.
 *
 * <p>Styled after the account cards so the two lists read as one family, but without their hover
 * highlight: nothing here is clickable, and a highlight that leads nowhere invites a click that does
 * nothing.
 */
public class PluginCard extends JPanel
{
	private static final int HEIGHT = 64;

	public PluginCard(PluginRows.Row row)
	{
		setLayout(new BorderLayout(14, 0));
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

		JLabel icon = new JLabel(SvgIcons.of(SvgIcons.Glyph.PLUGINS, 28, RiftTheme.ACCENT_BRIGHT));
		icon.setPreferredSize(new Dimension(32, 32));
		add(icon, BorderLayout.WEST);

		JPanel centre = new JPanel();
		centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
		centre.setOpaque(false);

		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		titleRow.setOpaque(false);
		JLabel name = new JLabel(row.getTitle());
		// Plugin names are set by whoever publishes the plugin, and a JLabel renders any text that
		// opens with <html> -- including images, fetched from wherever the markup says. A name is
		// text; show it as text.
		name.putClientProperty("html.disable", Boolean.TRUE);
		name.setFont(RiftTheme.bold(15));
		name.setForeground(RiftTheme.TEXT);
		titleRow.add(name);
		titleRow.add(badgeFor(row.getTag()));
		titleRow.setAlignmentX(LEFT_ALIGNMENT);

		JLabel detail = new JLabel(row.getDetail());
		detail.setFont(RiftTheme.regular(12));
		detail.setForeground(row.needsAttention() ? RiftTheme.WARN : RiftTheme.TEXT_FAINT);
		detail.setAlignmentX(LEFT_ALIGNMENT);
		detail.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));

		centre.add(Box.createVerticalGlue());
		centre.add(titleRow);
		centre.add(detail);
		centre.add(Box.createVerticalGlue());
		add(centre, BorderLayout.CENTER);
	}

	/**
	 * Green for owned, amber for a trial because it runs out, and the brand purple for a developer's
	 * own build -- three colours so the kind of access can be read without reading the word.
	 */
	private static StatusBadge badgeFor(PluginRows.Tag tag)
	{
		switch (tag)
		{
			case TRIAL:
				return new StatusBadge("Trial", RiftTheme.WARN);
			case DEV:
				return new StatusBadge("Dev plugin", RiftTheme.ACCENT_BRIGHT);
			case OWNED:
			default:
				return new StatusBadge("Owned", RiftTheme.OK);
		}
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, HEIGHT);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(super.getPreferredSize().width, HEIGHT);
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
