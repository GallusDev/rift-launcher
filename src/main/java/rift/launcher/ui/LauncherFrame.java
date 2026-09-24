package rift.launcher.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import rift.launcher.account.Account;
import rift.launcher.proxy.ProxyEntry;
import rift.launcher.ui.components.ClientCard;
import rift.launcher.ui.components.PluginCard;
import rift.launcher.ui.components.PrimaryButton;
import rift.launcher.ui.components.ProxyChoice;
import rift.launcher.ui.components.SectionCard;
import rift.launcher.ui.components.Sidebar;
import rift.launcher.ui.components.ThemedInput;
import rift.launcher.ui.components.ThemedTable;
import rift.launcher.ui.components.WindowChrome;
import rift.launcher.ui.components.WindowControls;
import rift.launcher.ui.theme.Assets;
import rift.launcher.ui.theme.RiftTheme;
import rift.launcher.web.OwnedPlugin;

/**
 * The launcher window: a navigation rail on the left, a switchable content pane, and a status bar.
 *
 * <p>Accounts are shown as cards rather than table rows -- this is a short list of important things,
 * not a spreadsheet -- and each card carries its own Launch button, so the action sits with the thing
 * it acts on and there is no "select a row first" step.
 *
 * <p>Painting comes from {@link RiftTheme} rather than the platform look-and-feel, so the window
 * looks the same on every machine and matches the brand. The Proxies and Settings panes keep their
 * existing controls unchanged: this is the shell around them, not their behaviour.
 */
public class LauncherFrame extends JFrame
{
	/** Corner radius for the window itself; a touch larger than the cards inside it. */
	private static final int WINDOW_RADIUS = 16;

	private static final String HOME = "Home";
	private static final String PROXIES = "Proxies";
	private static final String PLUGINS = "Plugins";
	private static final String SETTINGS = "Settings";

	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final Sidebar sidebar;

	// Home
	private final JPanel clientList = new JPanel();
	private final Map<String, ClientCard> clientCards = new LinkedHashMap<>();
	/**
	 * Each account's last status ("Launching...", "Playing"), by character id.
	 *
	 * <p>Cards are rebuilt whenever accounts or proxies change, and a new card starts "Ready". Without
	 * this, assigning a proxy while a client was running reset its card to Ready and re-enabled its
	 * Launch button -- an open invitation to launch the same account twice.
	 */
	private final Map<String, String> accountStatuses = new HashMap<>();
	/** The configured proxies, for each account card's dropdown. */
	private final List<ProxyEntry> proxies = new ArrayList<>();
	private final List<Account> accounts = new ArrayList<>();
	private final JLabel welcome = new JLabel("Welcome to Rift");
	private final JLabel emptyState =
		new JLabel("No accounts yet - launch once from the Jagex Launcher to import one.");

	private final JLabel status = new JLabel("Ready");
	private boolean signedIn;

	private Consumer<Account> onLaunch = account -> { };
	private Runnable onSignIn = () -> { };
	private Runnable onSignOut = () -> { };

	// Proxies: the table plus the actions that operate on it.
	private final ProxyTableModel proxyModel = new ProxyTableModel();
	private JTable proxyTable;
	private final JLabel proxyStatus = new JLabel("No proxies configured");
	private Consumer<List<ProxyEntry>> onAddProxies = list -> { };
	private Consumer<ProxyEntry> onDeleteProxy = p -> { };
	private Consumer<List<ProxyEntry>> onTestProxies = list -> { };
	private java.util.function.BiConsumer<Account, String> onAssignProxy = (a, id) -> { };

	// Jagex Launcher integration: current state plus manual repair/remove. Repair must live here
	// because once the config reverts, the Jagex Launcher boots RuneLite rather than Rift -- so the
	// launcher has to be able to fix itself when started from its own shortcut.
	private final JPanel jagexPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private final JLabel jagexStatusLabel = new JLabel("Jagex Launcher: checking...");
	private final JButton jagexRepairButton = new JButton("Repair");
	private final JButton jagexRemoveButton = new JButton("Remove");
	private Runnable onRepairJagex = () -> { };
	private Runnable onRemoveJagex = () -> { };

	// Updates: status plus a manual check. The client updates silently; a launcher update needs the
	// installer, because a running process cannot replace the jar it is executing from.
	private final JPanel updatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private final JLabel updateStatusLabel = new JLabel("Updates: not checked");
	private final JButton updateCheckButton = new JButton("Check for updates");
	private final JButton updateInstallButton = new JButton("Install launcher update");
	private Runnable onCheckUpdates = () -> { };
	private Runnable onInstallLauncherUpdate = () -> { };

	// Developer section: entering a valid, unrevoked license key is what unlocks developer mode.
	private final JPanel devPanel = new JPanel(new GridLayout(0, 1));
	private final JPasswordField devKeyField = new JPasswordField(26);
	private final JButton devVerifyButton = new JButton("Verify & save");
	private final JButton devRemoveButton = new JButton("Remove");
	private final JCheckBox devModeBox = new JCheckBox("Launch in developer mode");
	private final JLabel devStatusLabel = new JLabel("No developer key");
	private Consumer<String> onVerifyDevKey = key -> { };
	private Runnable onRemoveDevKey = () -> { };

	/**
	 * Ctrl+D: the offline developer unlock. Deliberately a hidden shortcut rather than a button --
	 * it is a fallback for when the server cannot answer, not a second way to become a developer, and
	 * a visible control would invite exactly that reading.
	 */
	private Runnable onOfflineDevUnlock = () -> { };

	// Plugins
	private final JPanel pluginList = new JPanel();
	private final JLabel pluginMessage = new JLabel();
	/** The account's plugins for this session; null until a fetch has answered. */
	private List<OwnedPlugin> ownedPlugins;
	private String ownedPluginsError;
	/**
	 * Whether dev plugins would load if the client were launched now: a verified developer key, or
	 * the offline unlock. Moved only by {@link #applyDeveloperAccess}, in lockstep with the
	 * developer-mode checkbox, so the Plugins page can never list dev plugins the launch would skip.
	 */
	private boolean developerAccess;
	private File devPluginsDir;
	private Runnable onRefreshPlugins = () -> { };

	public LauncherFrame(String version)
	{
		super("Rift Launcher - v" + version);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		// Frameless, with our own controls inside. The title is still set above because the taskbar
		// and Alt-Tab read it from the window regardless of whether a title bar is drawn.
		setUndecorated(true);
		setSize(1120, 700);
		setMinimumSize(new Dimension(940, 600));
		setLocationRelativeTo(null);
		setIconImages(loadIcons());

		JPanel root = new BackgroundPanel();
		root.setLayout(new BorderLayout());

		sidebar = new Sidebar(version, Arrays.asList(HOME, PROXIES, PLUGINS, SETTINGS), this::showPage, () ->
		{
			if (signedIn)
			{
				onSignOut.run();
			}
			else
			{
				onSignIn.run();
			}
		});
		root.add(sidebar, BorderLayout.WEST);

		JPanel titleBar = new JPanel(new BorderLayout());
		titleBar.setOpaque(false);
		titleBar.add(new WindowControls(this), BorderLayout.EAST);
		WindowChrome.makeDraggable(this, titleBar);

		JPanel contentArea = new JPanel(new BorderLayout());
		contentArea.setOpaque(false);
		contentArea.add(titleBar, BorderLayout.NORTH);

		content.setOpaque(false);
		content.add(buildHomePage(), HOME);
		content.add(wrapPane(buildProxiesTab()), PROXIES);
		content.add(wrapPane(buildPluginsTab()), PLUGINS);
		content.add(wrapPane(buildSettingsTab()), SETTINGS);
		contentArea.add(content, BorderLayout.CENTER);
		root.add(contentArea, BorderLayout.CENTER);

		root.add(buildFooter(), BorderLayout.SOUTH);
		setContentPane(root);
		// Registered on the root pane with WHEN_IN_FOCUSED_WINDOW so it fires wherever focus sits --
		// keying it to a component would mean it only worked while that component had focus.
		getRootPane().registerKeyboardAction(e -> onOfflineDevUnlock.run(),
			KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK),
			JComponent.WHEN_IN_FOCUSED_WINDOW);
		// Undecorated windows lose OS resizing; put it back on every edge and corner.
		WindowChrome.makeResizable(this);
		// Rounded corners: with translucency available the background panel paints them anti-aliased,
		// otherwise this falls back to a clipped window shape.
		WindowChrome.applyRoundedCorners(this, WINDOW_RADIUS);
		// The sidebar is not covered by the title strip, so it needs its own drag handle or the
		// window can only be moved by grabbing the narrow strip above the content.
		WindowChrome.makeDraggable(this, sidebar);
		showPage(HOME);
	}

	private void showPage(String page)
	{
		cards.show(content, page);
		sidebar.setSelected(page);
		if (PLUGINS.equals(page))
		{
			// Fetched on arrival rather than once at sign-in, so a plugin bought on the website a
			// minute ago is there the next time the page is opened, with no restart.
			renderPlugins();
			onRefreshPlugins.run();
		}
	}

	/** Gives the reused Proxies/Settings panes the padding and transparency the new shell expects. */
	private JComponent wrapPane(JPanel pane)
	{
		pane.setOpaque(false);
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
		wrap.add(pane, BorderLayout.CENTER);

		JScrollPane scroll = new JScrollPane(wrap);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private JPanel buildHomePage()
	{
		JPanel page = new JPanel(new BorderLayout());
		page.setOpaque(false);
		page.add(new HeroPanel(), BorderLayout.NORTH);

		clientList.setLayout(new BoxLayout(clientList, BoxLayout.Y_AXIS));
		clientList.setOpaque(false);

		emptyState.setFont(RiftTheme.regular(13));
		emptyState.setForeground(RiftTheme.TEXT_FAINT);
		emptyState.setBorder(BorderFactory.createEmptyBorder(24, 4, 0, 0));

		JLabel heading = new JLabel("Your accounts");
		heading.setFont(RiftTheme.bold(17));
		heading.setForeground(RiftTheme.TEXT);

		JPanel body = new JPanel(new BorderLayout());
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(22, 28, 20, 28));
		body.add(heading, BorderLayout.NORTH);

		JPanel listWrap = new JPanel(new BorderLayout());
		listWrap.setOpaque(false);
		listWrap.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
		listWrap.add(clientList, BorderLayout.NORTH);
		listWrap.add(emptyState, BorderLayout.CENTER);

		JScrollPane scroll = new JScrollPane(listWrap);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		body.add(scroll, BorderLayout.CENTER);

		page.add(body, BorderLayout.CENTER);
		return page;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel(new BorderLayout());
		footer.setOpaque(false);
		footer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, RiftTheme.BORDER),
			BorderFactory.createEmptyBorder(10, 28, 10, 28)));

		// One status line, left-aligned: status text reads from the left, and the signed-in identity
		// already lives in the sidebar.
		status.setFont(RiftTheme.regular(12));
		status.setForeground(RiftTheme.TEXT_MUTED);
		footer.add(status, BorderLayout.WEST);
		return footer;
	}

	/** Rebuilds the card list. Cards are keyed by character id so status updates can find them. */
	private void rebuildClientCards()
	{
		clientList.removeAll();
		clientCards.clear();
		for (Account account : accounts)
		{
			ClientCard card = new ClientCard(account,
				AccountAge.format(System.currentTimeMillis() - account.getAddedAt()),
				() -> onLaunch.accept(account),
				ProxyChoice.forAccount(proxies, account.getProxyId()),
				proxyId -> onAssignProxy.accept(account, proxyId));
			String status = accountStatuses.get(account.getCharacterId());
			if (status != null)
			{
				card.setStatus(status);
			}
			clientCards.put(account.getCharacterId(), card);
			clientList.add(card);
			clientList.add(Box.createVerticalStrut(10));
		}
		emptyState.setVisible(accounts.isEmpty());
		clientList.revalidate();
		clientList.repaint();
	}

	/** The window background: the brand texture, dimmed so content stays readable over it. */
	/**
	 * The window background: the brand texture, dimmed so content stays readable over it.
	 *
	 * <p>Deliberately opaque. A non-opaque root panel stops Swing repainting damaged regions
	 * correctly, which showed up as components staying blank until hovered. The rounded corners come
	 * from the window's shape instead.
	 */
	private static final class BackgroundPanel extends JPanel
	{
		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);

			g.setColor(RiftTheme.BG);
			g.fillRect(0, 0, getWidth(), getHeight());

			Image bg = Assets.background();
			if (bg != null)
			{
				g.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
				// Without this veil the texture competes with the text sitting on top of it.
				g.setColor(new Color(11, 10, 15, 195));
				g.fillRect(0, 0, getWidth(), getHeight());
			}
			g.dispose();
		}
	}

	/** The home banner: the rift artwork, with the greeting laid over its darkened left side. */
	private final class HeroPanel extends JPanel
	{
		HeroPanel()
		{
			setOpaque(false);
			setPreferredSize(new Dimension(0, 168));
			setLayout(new BorderLayout());
			setBorder(BorderFactory.createEmptyBorder(0, 56, 0, 56));

			welcome.setFont(RiftTheme.bold(26));
			welcome.setForeground(RiftTheme.TEXT);

			JLabel sub = new JLabel("Launch your accounts and step back into the Rift.");
			sub.setFont(RiftTheme.regular(13));
			sub.setForeground(RiftTheme.TEXT_MUTED);

			JPanel text = new JPanel();
			text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
			text.setOpaque(false);
			welcome.setAlignmentX(LEFT_ALIGNMENT);
			sub.setAlignmentX(LEFT_ALIGNMENT);
			text.add(Box.createVerticalGlue());
			text.add(welcome);
			text.add(Box.createVerticalStrut(6));
			text.add(sub);
			text.add(Box.createVerticalGlue());
			add(text, BorderLayout.WEST);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);

			int x = 28;
			int y = 8;
			int w = Math.max(0, getWidth() - 56);
			int h = Math.max(0, getHeight() - 16);
			int arc = RiftTheme.RADIUS + 4;

			Shape card = new RoundRectangle2D.Float(x, y, w, h, arc, arc);
			g.setClip(card);

			Image hero = Assets.hero(w, h);
			if (hero != null && hero.getWidth(null) > 0 && hero.getHeight(null) > 0)
			{
				// Cover, not stretch: the source is far wider than this panel, so scale to fill and
				// centre rather than squashing the artwork.
				int iw = hero.getWidth(null);
				int ih = hero.getHeight(null);
				double scale = Math.max(w / (double) iw, h / (double) ih);
				int dw = (int) Math.ceil(iw * scale);
				int dh = (int) Math.ceil(ih * scale);
				g.drawImage(hero, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
			}
			else
			{
				g.setColor(RiftTheme.SURFACE_RAISED);
				g.fillRect(x, y, w, h);
			}

			// Darken the left side so the greeting keeps its contrast whatever the art does there.
			g.setPaint(new GradientPaint(x, 0, new Color(11, 10, 15, 238),
				x + w * 0.62f, 0, new Color(11, 10, 15, 40)));
			g.fillRect(x, y, w, h);

			g.setClip(null);
			g.setColor(RiftTheme.BORDER_ACCENT);
			g.draw(card);
			g.dispose();
			super.paintComponent(graphics);
		}
	}


private JPanel buildProxiesTab()
	{
		proxyTable = ThemedTable.create(proxyModel);
		proxyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		// Colour the status so a dead proxy is visible without reading every row.
		proxyTable.getColumnModel().getColumn(ProxyTableModel.STATUS_COLUMN)
			.setCellRenderer(new javax.swing.table.DefaultTableCellRenderer()
			{
				@Override
				public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
					boolean selected, boolean focus, int row, int column)
				{
					java.awt.Component c = super.getTableCellRendererComponent(
						table, value, selected, focus, row, column);
					((JLabel) c).setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
					if (!selected)
					{
						c.setBackground(row % 2 == 0 ? RiftTheme.SURFACE : RiftTheme.SURFACE_RAISED);
						String text = String.valueOf(value);
						c.setForeground("Working".equals(text) ? RiftTheme.OK
							: "Not tested".equals(text) ? RiftTheme.TEXT_MUTED
							: RiftTheme.ERROR);
					}
					return c;
				}
			});

		PrimaryButton add = new PrimaryButton("Add", null, true);
		add.addActionListener(e ->
		{
			ProxyEntry entry = ProxyDialogs.addOne(this);
			if (entry != null)
			{
				onAddProxies.accept(java.util.Collections.singletonList(entry));
			}
		});

		PrimaryButton bulkAdd = new PrimaryButton("Bulk add", null, false);
		bulkAdd.setToolTipText("Paste a whole provider list at once");
		bulkAdd.addActionListener(e ->
		{
			List<ProxyEntry> entries = ProxyDialogs.addMany(this);
			if (!entries.isEmpty())
			{
				onAddProxies.accept(entries);
			}
		});

		PrimaryButton delete = new PrimaryButton("Delete", null, false);
		delete.addActionListener(e -> selectedProxies().forEach(onDeleteProxy));

		PrimaryButton test = new PrimaryButton("Test", null, false);
		test.addActionListener(e ->
		{
			List<ProxyEntry> selected = selectedProxies();
			onTestProxies.accept(selected.isEmpty() ? proxyModel.all() : selected);
		});

		PrimaryButton testAll = new PrimaryButton("Test all", null, false);
		testAll.addActionListener(e -> onTestProxies.accept(proxyModel.all()));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		actions.setOpaque(false);
		actions.add(add);
		actions.add(bulkAdd);
		actions.add(delete);
		actions.add(test);
		actions.add(testAll);

		proxyStatus.setFont(RiftTheme.regular(12));
		proxyStatus.setForeground(RiftTheme.TEXT_FAINT);

		JScrollPane tableScroll = ThemedTable.scroll(proxyTable);
		tableScroll.setPreferredSize(new Dimension(0, 320));

		SectionCard card = new SectionCard("Proxies",
			"Each account can connect through its own SOCKS5 proxy. Test reports latency and the exit IP.");
		card.add(actions);
		card.gap(14);
		card.add(tableScroll);
		card.gap(10);
		card.add(proxyStatus);

		JPanel tab = new JPanel(new BorderLayout());
		tab.setOpaque(false);
		tab.add(card, BorderLayout.NORTH);
		return tab;
	}

	/** The proxies highlighted in the table, so actions can operate on a multi-selection. */
	private List<ProxyEntry> selectedProxies()
	{
		List<ProxyEntry> selected = new ArrayList<>();
		for (int row : proxyTable.getSelectedRows())
		{
			selected.add(proxyModel.proxyAt(proxyTable.convertRowIndexToModel(row)));
		}
		return selected;
	}

	public void setOnAddProxies(Consumer<List<ProxyEntry>> onAddProxies)
	{
		this.onAddProxies = onAddProxies;
	}

	public void setOnDeleteProxy(Consumer<ProxyEntry> onDeleteProxy)
	{
		this.onDeleteProxy = onDeleteProxy;
	}

	public void setOnTestProxies(Consumer<List<ProxyEntry>> onTestProxies)
	{
		this.onTestProxies = onTestProxies;
	}

	public void setOnAssignProxy(java.util.function.BiConsumer<Account, String> onAssignProxy)
	{
		this.onAssignProxy = onAssignProxy;
	}

	/** Refreshes the proxy table and the account-row dropdown, which lists the same proxies. */
	public void setProxies(List<ProxyEntry> proxies)
	{
		SwingUtilities.invokeLater(() ->
		{
			proxyModel.setProxies(proxies);
			proxyStatus.setText(proxies.isEmpty()
				? "No proxies configured" : proxies.size() + " configured");
			// The account cards' dropdowns list these too, with each one's last test result, so an
			// added, deleted or re-tested proxy has to reach them.
			this.proxies.clear();
			this.proxies.addAll(proxies);
			rebuildClientCards();
		});
	}

	public void setProxyStatus(String text)
	{
		SwingUtilities.invokeLater(() -> proxyStatus.setText(text));
	}

	private JPanel buildPluginsTab()
	{
		pluginList.setLayout(new BoxLayout(pluginList, BoxLayout.Y_AXIS));
		pluginList.setOpaque(false);

		pluginMessage.setFont(RiftTheme.regular(13));
		pluginMessage.setForeground(RiftTheme.TEXT_MUTED);

		// For a purchase made while the page is already open; arriving on the page refreshes anyway.
		PrimaryButton refresh = new PrimaryButton("Refresh", null, false);
		refresh.addActionListener(e -> onRefreshPlugins.run());
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		actions.setOpaque(false);
		actions.add(refresh);

		SectionCard card = new SectionCard("Plugins",
			"What this account can use, and when access ends. Developer plugins are listed while developer access is active.");
		card.add(actions);
		card.gap(14);
		card.add(pluginMessage);
		card.gap(10);
		card.add(pluginList);

		JPanel tab = new JPanel(new BorderLayout());
		tab.setOpaque(false);
		tab.add(card, BorderLayout.NORTH);
		renderPlugins();
		return tab;
	}

	/**
	 * Rebuilds the Plugins page from current state. EDT only.
	 *
	 * <p>Each source is gated here, at the point of display, rather than trusted to have been cleared
	 * upstream: the account's plugins only while signed in, dev plugins only while developer access
	 * holds. A late reply or a missed reset can then at worst leave stale data in a field, never on
	 * screen.
	 */
	private void renderPlugins()
	{
		List<OwnedPlugin> owned = signedIn ? ownedPlugins : null;
		List<PluginRows.DevJar> dev = developerAccess ? PluginRows.findDevJars(devPluginsDir)
			: java.util.Collections.emptyList();

		pluginList.removeAll();
		List<PluginRows.Row> rows = PluginRows.build(owned, dev, Instant.now(), ZoneId.systemDefault(),
			Locale.getDefault());
		for (int i = 0; i < rows.size(); i++)
		{
			if (i > 0)
			{
				pluginList.add(Box.createVerticalStrut(8));
			}
			pluginList.add(new PluginCard(rows.get(i)));
		}

		List<String> lines = PluginRows.messages(signedIn, ownedPluginsError, owned, developerAccess,
			dev.size(), devPluginsDir);
		// HTML only to break lines; every line is escaped, because one of them carries a folder path.
		StringBuilder html = new StringBuilder("<html>");
		for (int i = 0; i < lines.size(); i++)
		{
			html.append(i > 0 ? "<br>" : "").append(escape(lines.get(i)));
		}
		pluginMessage.setText(html.append("</html>").toString());
		pluginMessage.setVisible(!lines.isEmpty());

		pluginList.revalidate();
		pluginList.repaint();
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Grants or withdraws developer access, and with it the developer-mode checkbox. EDT only.
	 *
	 * <p>The one place either moves, so the Plugins page's dev list and the checkbox that decides
	 * whether dev plugins load cannot disagree.
	 */
	private void applyDeveloperAccess(boolean access)
	{
		developerAccess = access;
		devModeBox.setEnabled(access);
		renderPlugins();
	}

	private JPanel buildSettingsTab()
	{
		// --- Updates
		updateCheckButton.addActionListener(e -> onCheckUpdates.run());
		updateInstallButton.addActionListener(e -> onInstallLauncherUpdate.run());
		updateInstallButton.setEnabled(false);
		updateStatusLabel.setFont(RiftTheme.regular(12));
		updateStatusLabel.setForeground(RiftTheme.TEXT_MUTED);

		SectionCard updates = new SectionCard("Updates",
			"The client updates itself silently. A launcher update runs the installer.");
		updates.add(row(themed(updateCheckButton, "Check for updates", false),
			themed(updateInstallButton, "Install launcher update", true)));
		updates.gap(10);
		updates.add(updateStatusLabel);

		// --- Jagex Launcher integration
		jagexStatusLabel.setFont(RiftTheme.regular(12));
		jagexStatusLabel.setForeground(RiftTheme.TEXT_MUTED);

		SectionCard jagex = new SectionCard("Jagex Launcher integration",
			"Pressing Play in the Jagex Launcher opens Rift. Repair restores that if a RuneLite update undoes it.");
		jagex.add(row(themed(jagexRepairButton, "Repair", false),
			themed(jagexRemoveButton, "Remove", false)));
		jagex.gap(10);
		jagex.add(jagexStatusLabel);

		// --- Developer
		devKeyField.setToolTipText("Your rift_dev_... license key from the developer dashboard");
		// field() returns the rounded surround that paints behind the text; that wrapper is what goes
		// in the layout, not the raw field.
		JComponent devKeyInput = ThemedInput.field(devKeyField);
		devKeyInput.setPreferredSize(new Dimension(280, 38));
		devKeyInput.setMaximumSize(new Dimension(280, 38));
		devVerifyButton.addActionListener(e ->
		{
			char[] chars = devKeyField.getPassword();
			String key = new String(chars).trim();
			java.util.Arrays.fill(chars, (char) 0);
			if (key.isEmpty())
			{
				setDevStatus("Enter your developer license key first");
				return;
			}
			onVerifyDevKey.accept(key);
		});
		devRemoveButton.addActionListener(e -> onRemoveDevKey.run());
		ThemedInput.checkBox(devModeBox);
		devModeBox.setEnabled(false);
		devStatusLabel.setFont(RiftTheme.regular(12));
		devStatusLabel.setForeground(RiftTheme.TEXT_FAINT);

		JLabel keyLabel = new JLabel("License key");
		keyLabel.setFont(RiftTheme.regular(12));
		keyLabel.setForeground(RiftTheme.TEXT_MUTED);

		JPanel keyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		keyRow.setOpaque(false);
		keyRow.add(devKeyInput);
		keyRow.add(themed(devVerifyButton, "Verify & save", true));
		keyRow.add(themed(devRemoveButton, "Remove", false));

		SectionCard developer = new SectionCard("Developer",
			"A verified license key unlocks launching with your local development plugins.");
		developer.add(keyLabel);
		developer.gap(6);
		developer.add(keyRow);
		developer.gap(12);
		developer.add(devModeBox);
		developer.gap(8);
		developer.add(devStatusLabel);

		// devPanel stays the thing shown and hidden by setDeveloperSectionVisible, so the card goes
		// inside it rather than replacing it.
		devPanel.removeAll();
		devPanel.setLayout(new BorderLayout());
		devPanel.setOpaque(false);
		devPanel.add(developer, BorderLayout.CENTER);
		devPanel.setVisible(false);

		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		updates.setAlignmentX(LEFT_ALIGNMENT);
		jagex.setAlignmentX(LEFT_ALIGNMENT);
		devPanel.setAlignmentX(LEFT_ALIGNMENT);
		column.add(updates);
		column.add(Box.createVerticalStrut(14));
		column.add(jagex);
		column.add(Box.createVerticalStrut(14));
		column.add(devPanel);

		JPanel tab = new JPanel(new BorderLayout());
		tab.setOpaque(false);
		tab.add(column, BorderLayout.NORTH);
		return tab;
	}

	/**
	 * Presents an existing JButton in the new style.
	 *
	 * <p>The originals are fields that the rest of the class enables and disables, so they stay the
	 * source of truth for state; this mirrors their look and forwards clicks, rather than rewriting
	 * that wiring.
	 */
	private PrimaryButton themed(JButton source, String text, boolean primary)
	{
		PrimaryButton button = new PrimaryButton(text, null, primary);
		button.addActionListener(e -> source.doClick());
		source.addPropertyChangeListener("enabled",
			e -> button.setEnabled(Boolean.TRUE.equals(e.getNewValue())));
		button.setEnabled(source.isEnabled());
		return button;
	}

	private JPanel row(JComponent... items)
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		panel.setOpaque(false);
		for (JComponent item : items)
		{
			panel.add(item);
		}
		return panel;
	}

	/** The Rift logo at several sizes, for the title-bar and taskbar icon. */
	private static List<Image> loadIcons()
	{
		List<Image> icons = new ArrayList<>();
		for (int size : new int[]{16, 32, 64, 256})
		{
			URL url = LauncherFrame.class.getResource("/rift/launcher/icon/icon-" + size + ".png");
			if (url != null)
			{
				icons.add(new ImageIcon(url).getImage());
			}
		}
		return icons;
	}

	public void setOnLaunch(Consumer<Account> onLaunch)
	{
		this.onLaunch = onLaunch;
	}

	public void setOnSignIn(Runnable onSignIn)
	{
		this.onSignIn = onSignIn;
	}

	public void setOnSignOut(Runnable onSignOut)
	{
		this.onSignOut = onSignOut;
	}

	public void setOnCheckUpdates(Runnable onCheckUpdates)
	{
		this.onCheckUpdates = onCheckUpdates;
	}

	public void setOnInstallLauncherUpdate(Runnable onInstallLauncherUpdate)
	{
		this.onInstallLauncherUpdate = onInstallLauncherUpdate;
	}

	/** Shows the update state; the install button only lights up when a launcher update is waiting. */
	public void setUpdateStatus(String text, boolean launcherUpdateAvailable, boolean checkEnabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			updateStatusLabel.setText(text);
			updateInstallButton.setEnabled(launcherUpdateAvailable);
			updateCheckButton.setEnabled(checkEnabled);
		});
	}

	public void setOnRepairJagex(Runnable onRepairJagex)
	{
		this.onRepairJagex = onRepairJagex;
	}

	public void setOnRemoveJagex(Runnable onRemoveJagex)
	{
		this.onRemoveJagex = onRemoveJagex;
	}

	/** Shows the integration state; Repair only means something when RuneLite is present but reverted. */
	public void setJagexStatus(String text, boolean repairEnabled, boolean removeEnabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			jagexStatusLabel.setText(text);
			jagexRepairButton.setEnabled(repairEnabled);
			jagexRemoveButton.setEnabled(removeEnabled);
		});
	}

	public void setOnVerifyDevKey(Consumer<String> onVerifyDevKey)
	{
		this.onVerifyDevKey = onVerifyDevKey;
	}

	public void setOnRemoveDevKey(Runnable onRemoveDevKey)
	{
		this.onRemoveDevKey = onRemoveDevKey;
	}

	/** Whether the user asked for a developer-mode launch. Only meaningful when a key has verified. */
	public void setOnOfflineDevUnlock(Runnable onOfflineDevUnlock)
	{
		this.onOfflineDevUnlock = onOfflineDevUnlock;
	}

	/**
	 * Shows the developer section and arms the developer-mode checkbox after an offline unlock.
	 * <p>
	 * Separate from {@link #setDevLicenseVerified} because the two mean different things and the status
	 * text has to say which: one is the server vouching for a key, the other is this machine vouching
	 * for itself because the server could not be asked.
	 */
	public void setOfflineDevUnlocked(String username)
	{
		SwingUtilities.invokeLater(() ->
		{
			devPanel.setVisible(true);
			applyDeveloperAccess(true);
			devModeBox.setSelected(true);
			devStatusLabel.setText("Offline developer unlock active (" + username + ")");
		});
	}

	/** The offline-unlock prompt, as a modal owned by this window. */
	public OfflineDevPrompt promptOfflineDev(boolean setup)
	{
		OfflineDevDialog.Result r = OfflineDevDialog.prompt(this, setup);
		return r == null ? null : new OfflineDevPrompt(r.username, r.password, r.setup);
	}

	/** What the offline prompt returned. The caller clears the password when done with it. */
	public static final class OfflineDevPrompt
	{
		private final String username;
		private final char[] password;
		private final boolean setup;

		OfflineDevPrompt(String username, char[] password, boolean setup)
		{
			this.username = username;
			this.password = password;
			this.setup = setup;
		}

		public String getUsername()
		{
			return username;
		}

		public char[] getPassword()
		{
			return password;
		}

		public boolean isSetup()
		{
			return setup;
		}

		public void clear()
		{
			java.util.Arrays.fill(password, '\0');
		}
	}

	public boolean isDeveloperModeRequested()
	{
		return devModeBox.isEnabled() && devModeBox.isSelected();
	}

	/**
	 * Shows/hides the developer section, driven by {@code license/check.developer}. Cosmetic: the real
	 * gate is the license key, verified and bound to this account server-side. Hiding also drops any
	 * armed developer mode, so an account that loses developer status can't launch with it still ticked.
	 */
	public void setDeveloperSectionVisible(boolean visible)
	{
		SwingUtilities.invokeLater(() ->
		{
			devPanel.setVisible(visible);
			if (!visible)
			{
				devModeBox.setSelected(false);
				applyDeveloperAccess(false);
			}
		});
	}

	/**
	 * Reflects the verified state of the stored key. A valid key unlocks (and does not auto-select) the
	 * developer-mode checkbox; anything else locks and clears it, so a revoked key can't leave developer
	 * mode armed.
	 *
	 * <p>The key itself is never shown -- not even masked. It is a credential, and displaying part of it
	 * buys nothing: the user only needs to know whether it is working.
	 */
	public void setDevLicenseVerified(boolean valid, String tier)
	{
		SwingUtilities.invokeLater(() ->
		{
			applyDeveloperAccess(valid);
			if (valid)
			{
				devKeyField.setText("");
				devStatusLabel.setText("Licence verified" + (tier == null ? "" : " (" + tier + ")"));
			}
			else
			{
				devModeBox.setSelected(false);
			}
		});
	}

	public void setDevStatus(String text)
	{
		SwingUtilities.invokeLater(() -> devStatusLabel.setText(text));
	}

	public void setDevControlsEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			devVerifyButton.setEnabled(enabled);
			devRemoveButton.setEnabled(enabled);
		});
	}

	/** Where the client loads dev plugins from, so the Plugins page lists exactly those jars. */
	public void setDevPluginsDir(File dir)
	{
		SwingUtilities.invokeLater(() ->
		{
			devPluginsDir = dir;
			renderPlugins();
		});
	}

	/** Run when the Plugins page wants fresh data: on arrival, and on Refresh. */
	public void setOnRefreshPlugins(Runnable onRefreshPlugins)
	{
		this.onRefreshPlugins = onRefreshPlugins;
	}

	/**
	 * A fetch has started. Clears any earlier error, so the page shows "loading" if it has nothing yet
	 * and simply keeps the list it has if it does -- a refresh should not blank a list that is there.
	 */
	public void setOwnedPluginsLoading()
	{
		SwingUtilities.invokeLater(() ->
		{
			ownedPluginsError = null;
			renderPlugins();
		});
	}

	public void setOwnedPlugins(List<OwnedPlugin> plugins)
	{
		SwingUtilities.invokeLater(() ->
		{
			ownedPlugins = plugins;
			ownedPluginsError = null;
			renderPlugins();
		});
	}

	/** A fetch failed. Any list already on screen stays, under the message, as the last known state. */
	public void setOwnedPluginsError(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			ownedPluginsError = message;
			renderPlugins();
		});
	}

	/** Updates the account bar: a non-null name shows "signed in as ..." + a Sign-out button. */
	public void setRiftAccount(String userName)
	{
		SwingUtilities.invokeLater(() ->
		{
			signedIn = userName != null;
			sidebar.setUser(userName);
			// Whatever was fetched belonged to the previous account, or to none; never show it to the
			// next one. The page refetches for the new session.
			ownedPlugins = null;
			ownedPluginsError = null;
			welcome.setText(signedIn ? "Welcome back, " + userName : "Welcome to Rift");

			// Hide by default and stay hidden until the license check confirms this account is a
			// developer (setDeveloperSectionVisible). Signing out must also drop developer mode, so
			// the next account signed in on this machine cannot inherit it.
			devPanel.setVisible(false);
			if (!signedIn)
			{
				devModeBox.setSelected(false);
				applyDeveloperAccess(false);
				devKeyField.setText("");
				devStatusLabel.setText("No developer key");
			}
			renderPlugins();
		});
	}

	/** Enables/disables the sign-in button (e.g. while a sign-in is in progress). */
	public void setRiftAuthEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() -> sidebar.setAuthEnabled(enabled));
	}

	public void setAccounts(List<Account> newAccounts)
	{
		SwingUtilities.invokeLater(() ->
		{
			accounts.clear();
			accounts.addAll(newAccounts);
			rebuildClientCards();
		});
	}

	public void setAccountStatus(String characterId, String accountStatus)
	{
		SwingUtilities.invokeLater(() ->
		{
			accountStatuses.put(characterId, accountStatus);
			ClientCard card = clientCards.get(characterId);
			if (card != null)
			{
				card.setStatus(accountStatus);
			}
		});
	}

	public void setStatus(String text)
	{
		SwingUtilities.invokeLater(() -> status.setText(text));
	}
}
