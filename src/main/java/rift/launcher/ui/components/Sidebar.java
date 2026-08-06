package rift.launcher.ui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import rift.launcher.ui.theme.Assets;
import rift.launcher.ui.theme.RiftIcons;
import rift.launcher.ui.theme.RiftTheme;

/**
 * The left rail: brand mark, navigation, and the signed-in user.
 *
 * <p>Navigation lives here rather than in tabs because the launcher has a handful of destinations
 * that each deserve a full pane, and a rail leaves room for the user block and version to sit at the
 * bottom where people expect them.
 */
public class Sidebar extends JPanel
{
	private final Map<String, NavButton> buttons = new LinkedHashMap<>();
	private final JLabel userName = new JLabel("Not signed in");
	private final JLabel userState = new JLabel("Offline");
	private final JLabel avatar = new JLabel();
	private final PrimaryButton authButton;

	public Sidebar(String version, List<String> pages, java.util.function.Consumer<String> onNavigate,
		Runnable onAuthClick)
	{
		setLayout(new BorderLayout());
		setOpaque(false);
		setPreferredSize(new Dimension(RiftTheme.SIDEBAR_WIDTH, 0));
		setBorder(BorderFactory.createEmptyBorder(20, 12, 16, 12));

		add(buildBrand(), BorderLayout.NORTH);

		JPanel nav = new JPanel();
		nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
		nav.setOpaque(false);
		for (String page : pages)
		{
			NavButton button = new NavButton(page, iconFor(page), () -> onNavigate.accept(page));
			buttons.put(page, button);
			nav.add(button);
			nav.add(Box.createVerticalStrut(4));
		}
		JPanel navWrap = new JPanel(new BorderLayout());
		navWrap.setOpaque(false);
		navWrap.add(nav, BorderLayout.NORTH);
		add(navWrap, BorderLayout.CENTER);

		authButton = new PrimaryButton("Sign in", null, false);
		authButton.addActionListener(e -> onAuthClick.run());
		add(buildUserBlock(version), BorderLayout.SOUTH);
	}

	private JPanel buildBrand()
	{
		JPanel brand = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		brand.setOpaque(false);
		brand.setBorder(BorderFactory.createEmptyBorder(0, 0, 24, 0));

		Image logo = Assets.logo();
		if (logo != null)
		{
			int width = RiftTheme.SIDEBAR_WIDTH - 60;
			int height = Math.round(logo.getHeight(null) * (width / (float) logo.getWidth(null)));
			brand.add(new JLabel(new ImageIcon(
				logo.getScaledInstance(width, height, Image.SCALE_SMOOTH))));
		}
		else
		{
			JLabel text = new JLabel("RIFT");
			text.setFont(RiftTheme.bold(28));
			text.setForeground(RiftTheme.ACCENT_BRIGHT);
			brand.add(text);
		}
		return brand;
	}

	private JPanel buildUserBlock(String version)
	{
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setOpaque(false);

		JPanel divider = new JPanel();
		divider.setOpaque(false);
		divider.setPreferredSize(new Dimension(0, 1));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		divider.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RiftTheme.BORDER));
		block.add(divider);
		block.add(Box.createVerticalStrut(14));

		JPanel row = new JPanel(new BorderLayout(10, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

		Icon profile = Assets.icon("profile", 40);
		avatar.setIcon(profile != null ? profile
			: RiftIcons.of(RiftIcons.Kind.HOME, 40, RiftTheme.ACCENT));
		row.add(avatar, BorderLayout.WEST);

		JPanel names = new JPanel();
		names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
		names.setOpaque(false);
		userName.setFont(RiftTheme.bold(13));
		userName.setForeground(RiftTheme.TEXT);
		userName.setAlignmentX(LEFT_ALIGNMENT);
		userState.setFont(RiftTheme.regular(11));
		userState.setForeground(RiftTheme.TEXT_FAINT);
		userState.setAlignmentX(LEFT_ALIGNMENT);
		names.add(Box.createVerticalGlue());
		names.add(userName);
		names.add(userState);
		names.add(Box.createVerticalGlue());
		row.add(names, BorderLayout.CENTER);

		block.add(row);
		block.add(Box.createVerticalStrut(12));

		authButton.setAlignmentX(LEFT_ALIGNMENT);
		authButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		block.add(authButton);
		block.add(Box.createVerticalStrut(12));

		JLabel versionLabel = new JLabel("Rift Launcher  v" + version);
		versionLabel.setFont(RiftTheme.regular(11));
		versionLabel.setForeground(RiftTheme.TEXT_FAINT);
		versionLabel.setAlignmentX(LEFT_ALIGNMENT);
		block.add(versionLabel);
		return block;
	}

	private static Icon iconFor(String page)
	{
		String key = page.toLowerCase();
		// Proxies has no art of its own; the changelog mark reads as "a list of entries", which is
		// closer than reusing the gear that already means Settings.
		Icon art = Assets.icon(key.startsWith("prox") ? "changelog" : key, 26);
		if (art != null)
		{
			return art;
		}
		// The pack may not cover every page; the drawn set fills the gaps so nav never looks broken.
		RiftIcons.Kind kind = key.startsWith("home") ? RiftIcons.Kind.HOME
			: key.startsWith("prox") ? RiftIcons.Kind.PROXY
			: key.startsWith("set") ? RiftIcons.Kind.SETTINGS
			: RiftIcons.Kind.CHANGELOG;
		return RiftIcons.of(kind, 22, RiftTheme.TEXT_MUTED);
	}

	public void setSelected(String page)
	{
		for (Map.Entry<String, NavButton> e : buttons.entrySet())
		{
			e.getValue().setSelected(e.getKey().equals(page));
		}
	}

	/** Updates the user block; a null name means signed out. */
	public void setUser(String name)
	{
		boolean signedIn = name != null;
		userName.setText(signedIn ? name : "Not signed in");
		userName.setForeground(signedIn ? RiftTheme.TEXT : RiftTheme.TEXT_MUTED);
		userState.setText(signedIn ? "Online" : "Offline");
		userState.setForeground(signedIn ? RiftTheme.OK : RiftTheme.TEXT_FAINT);
		authButton.setText(signedIn ? "Sign out" : "Sign in");
	}

	public void setAuthEnabled(boolean enabled)
	{
		authButton.setEnabled(enabled);
	}

	/** Page names in nav order, so the frame can build matching content panes. */
	public List<String> pages()
	{
		return new ArrayList<>(buttons.keySet());
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(RiftTheme.SIDEBAR);
		g.fillRect(0, 0, getWidth(), getHeight());
		// A hairline separating rail from content, rather than a hard edge.
		g.setColor(RiftTheme.BORDER);
		g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
		g.dispose();
	}
}
