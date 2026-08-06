package rift.launcher.ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import rift.launcher.account.Account;
import rift.launcher.ui.theme.Assets;
import rift.launcher.ui.theme.RiftIcons;
import rift.launcher.ui.theme.RiftTheme;

/**
 * One account, as a card: avatar, name, live status, session age, and its own Launch button.
 *
 * <p>A card per account rather than a table row because the launcher is a small list of important
 * things, not a spreadsheet — and it puts Launch next to the account it launches, removing the
 * "select a row first" step a table forces.
 */
public class ClientCard extends JPanel
{
	private final Account account;
	private final StatusBadge badge = new StatusBadge("Ready", RiftTheme.OK);
	private final JLabel meta = new JLabel();
	private final PrimaryButton launch;
	private boolean hovered;

	public ClientCard(Account account, String sessionAge, Runnable onLaunch)
	{
		this.account = account;
		setLayout(new BorderLayout(14, 0));
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

		// Avatar
		JLabel avatar = new JLabel();
		Image profile = null;
		javax.swing.Icon icon = Assets.icon("profile", 44);
		if (icon != null)
		{
			avatar.setIcon(icon);
		}
		else
		{
			avatar.setIcon(RiftIcons.of(RiftIcons.Kind.HOME, 44, RiftTheme.ACCENT));
		}
		avatar.setPreferredSize(new Dimension(48, 48));
		add(avatar, BorderLayout.WEST);

		// Name + badge on one line, meta beneath -- the two things you scan for, then the detail.
		JPanel centre = new JPanel();
		centre.setLayout(new BoxLayout(centre, BoxLayout.Y_AXIS));
		centre.setOpaque(false);

		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		titleRow.setOpaque(false);
		JLabel name = new JLabel(account.getDisplayName());
		name.setFont(RiftTheme.bold(16));
		name.setForeground(RiftTheme.TEXT);
		titleRow.add(name);
		titleRow.add(badge);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);

		meta.setFont(RiftTheme.regular(12));
		meta.setForeground(RiftTheme.TEXT_FAINT);
		meta.setAlignmentX(LEFT_ALIGNMENT);
		meta.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));
		setSessionAge(sessionAge);

		centre.add(Box.createVerticalGlue());
		centre.add(titleRow);
		centre.add(meta);
		centre.add(Box.createVerticalGlue());
		add(centre, BorderLayout.CENTER);

		launch = new PrimaryButton("Launch",
			RiftIcons.of(RiftIcons.Kind.ROCKET, 16, RiftTheme.TEXT), true);
		launch.addActionListener(e -> onLaunch.run());
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		right.setOpaque(false);
		right.add(launch);
		add(right, BorderLayout.EAST);

		MouseAdapter hover = new MouseAdapter()
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
				// Only clear when the pointer has actually left the card, not merely crossed onto a
				// child component -- otherwise the highlight flickers as the mouse moves across it.
				if (!contains(e.getPoint()))
				{
					hovered = false;
					repaint();
				}
			}
		};
		addMouseListener(hover);
	}

	public Account getAccount()
	{
		return account;
	}

	/** Reflects launch state: the button locks while a client is starting, so it can't be double-run. */
	public void setStatus(String status)
	{
		String value = status == null ? "Ready" : status;
		Color color = RiftTheme.OK;
		if (value.toLowerCase().contains("launch"))
		{
			color = RiftTheme.WARN;
		}
		else if (value.toLowerCase().contains("playing"))
		{
			color = RiftTheme.ACCENT_BRIGHT;
		}
		badge.set(value, color);
		launch.setEnabled(!value.toLowerCase().contains("launch"));
	}

	public void setSessionAge(String age)
	{
		meta.setText("Session age: " + (age == null ? "unknown" : age));
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, 76);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(super.getPreferredSize().width, 76);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int arc = RiftTheme.RADIUS;
		g.setColor(hovered ? RiftTheme.SURFACE_HOVER : RiftTheme.SURFACE_RAISED);
		g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
		g.setColor(hovered ? RiftTheme.BORDER_ACCENT : RiftTheme.BORDER);
		g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
		g.dispose();
		super.paintComponent(graphics);
	}
}
