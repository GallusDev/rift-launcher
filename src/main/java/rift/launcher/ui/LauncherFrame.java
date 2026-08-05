package rift.launcher.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import rift.launcher.account.Account;

/**
 * The launcher window: a <b>Home</b> tab listing accounts with a Launch button on each row, and a
 * <b>Settings</b> tab holding the Jagex Launcher integration and developer controls.
 *
 * <p>Launching per row rather than from a single button at the bottom means the action sits with the
 * thing it acts on, and there is no "select a row first" step. Settings are tabbed away because they
 * are configured rarely but were previously taking up permanent space beneath the account list.
 */
public class LauncherFrame extends JFrame
{
	private final AccountTableModel model = new AccountTableModel();
	private final JLabel status = new JLabel("Ready");
	private final JLabel riftAccountLabel = new JLabel("Rift: not signed in");
	private final JButton riftAuthButton = new JButton("Sign in to Rift");
	private boolean signedIn;
	private Consumer<Account> onLaunch = account -> { };
	private Runnable onSignIn = () -> { };
	private Runnable onSignOut = () -> { };

	// Jagex Launcher integration: current state plus manual repair/remove. Repair must live here
	// because once the config reverts, the Jagex Launcher boots RuneLite rather than Rift -- so the
	// launcher has to be able to fix itself when started from its own shortcut.
	private final JPanel jagexPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private final JLabel jagexStatusLabel = new JLabel("Jagex Launcher: checking...");
	private final JButton jagexRepairButton = new JButton("Repair");
	private final JButton jagexRemoveButton = new JButton("Remove");
	private Runnable onRepairJagex = () -> { };
	private Runnable onRemoveJagex = () -> { };

	// Developer section: entering a valid, unrevoked license key is what unlocks developer mode.
	// One row per logical control group -- a single FlowLayout row would wrap on a narrow window and
	// FlowLayout reports only one row's height, which silently clips whatever wrapped.
	private final JPanel devPanel = new JPanel(new GridLayout(0, 1));
	private final JPasswordField devKeyField = new JPasswordField(26);
	private final JButton devVerifyButton = new JButton("Verify & save");
	private final JButton devRemoveButton = new JButton("Remove");
	private final JCheckBox devModeBox = new JCheckBox("Launch in developer mode");
	private final JLabel devStatusLabel = new JLabel("No developer key");
	private Consumer<String> onVerifyDevKey = key -> { };
	private Runnable onRemoveDevKey = () -> { };

	public LauncherFrame()
	{
		super("Rift Launcher");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(880, 560);
		setLocationRelativeTo(null);
		setIconImages(loadIcons());

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Home", buildHomeTab());
		tabs.addTab("Settings", buildSettingsTab());

		// The status line sits outside the tabs so feedback stays visible whichever tab is open.
		JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statusBar.add(status);

		add(tabs, BorderLayout.CENTER);
		add(statusBar, BorderLayout.SOUTH);
	}

	private JPanel buildHomeTab()
	{
		// Rift-account bar: shows the signed-in Discord name and the sign-in / sign-out control.
		JPanel accountBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
		riftAuthButton.addActionListener(e ->
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
		accountBar.add(riftAuthButton);
		accountBar.add(riftAccountLabel);

		JTable table = new JTable(model);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowHeight(28);
		table.getColumnModel().getColumn(0).setPreferredWidth(220);
		table.getColumnModel().getColumn(1).setPreferredWidth(160);
		table.getColumnModel().getColumn(2).setPreferredWidth(120);
		table.getColumnModel().getColumn(AccountTableModel.LAUNCH_COLUMN).setPreferredWidth(110);
		table.getColumnModel().getColumn(AccountTableModel.LAUNCH_COLUMN).setMaxWidth(140);

		// One Launch button per row, so the action sits with the account it launches.
		new ButtonColumn(table, AccountTableModel.LAUNCH_COLUMN, row ->
		{
			Account account = model.accountAt(row);
			model.setStatus(account.getCharacterId(), "Launching...");
			onLaunch.accept(account);
		});

		// The session-age column is computed from the clock, so repaint it periodically.
		new Timer(30_000, e -> model.refreshAges()).start();

		JPanel home = new JPanel(new BorderLayout());
		home.add(accountBar, BorderLayout.NORTH);
		home.add(new JScrollPane(table), BorderLayout.CENTER);
		return home;
	}

	private JPanel buildSettingsTab()
	{
		jagexPanel.setBorder(BorderFactory.createTitledBorder("Jagex Launcher integration"));
		jagexRepairButton.addActionListener(e -> onRepairJagex.run());
		jagexRemoveButton.addActionListener(e -> onRemoveJagex.run());
		jagexPanel.add(jagexStatusLabel);
		jagexPanel.add(jagexRepairButton);
		jagexPanel.add(jagexRemoveButton);

		// Developer key row. Hidden until signed in as a developer, since a key is only meaningful then.
		devPanel.setBorder(BorderFactory.createTitledBorder("Developer"));
		devKeyField.setToolTipText("Your rift_dev_... license key from the developer dashboard");
		devVerifyButton.addActionListener(e ->
		{
			char[] chars = devKeyField.getPassword();
			String key = new String(chars).trim();
			java.util.Arrays.fill(chars, '\0');
			if (key.isEmpty())
			{
				setDevStatus("Enter your developer license key first");
				return;
			}
			onVerifyDevKey.accept(key);
		});
		devRemoveButton.addActionListener(e -> onRemoveDevKey.run());
		// Developer mode stays locked until a key actually verifies.
		devModeBox.setEnabled(false);

		JPanel devKeyRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		devKeyRow.add(new JLabel("License key:"));
		devKeyRow.add(devKeyField);
		devKeyRow.add(devVerifyButton);
		devKeyRow.add(devRemoveButton);

		JPanel devModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		devModeRow.add(devModeBox);
		devModeRow.add(devStatusLabel);

		devPanel.add(devKeyRow);
		devPanel.add(devModeRow);
		devPanel.setVisible(false);

		// Stacked top-down so each section keeps its natural height and the rest is empty space.
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		jagexPanel.setAlignmentX(LEFT_ALIGNMENT);
		devPanel.setAlignmentX(LEFT_ALIGNMENT);
		column.add(jagexPanel);
		column.add(devPanel);
		column.add(Box.createVerticalGlue());

		JPanel settings = new JPanel(new BorderLayout());
		settings.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		settings.add(column, BorderLayout.NORTH);
		return settings;
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
				devModeBox.setEnabled(false);
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
			devModeBox.setEnabled(valid);
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

	/** Updates the account bar: a non-null name shows "signed in as ..." + a Sign-out button. */
	public void setRiftAccount(String userName)
	{
		SwingUtilities.invokeLater(() ->
		{
			signedIn = userName != null;
			if (signedIn)
			{
				riftAccountLabel.setText("Rift: signed in as " + userName);
				riftAuthButton.setText("Sign out");
			}
			else
			{
				riftAccountLabel.setText("Rift: not signed in");
				riftAuthButton.setText("Sign in to Rift");
			}
			// Hide by default and stay hidden until the license check confirms this account is a
			// developer (setDeveloperSectionVisible). Signing out must also drop developer mode, so
			// the next account signed in on this machine can't inherit it.
			devPanel.setVisible(false);
			if (!signedIn)
			{
				devModeBox.setSelected(false);
				devModeBox.setEnabled(false);
				devKeyField.setText("");
				devStatusLabel.setText("No developer key");
			}
		});
	}

	/** Enables/disables the sign-in button (e.g. while a sign-in is in progress). */
	public void setRiftAuthEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() -> riftAuthButton.setEnabled(enabled));
	}

	public void setAccounts(List<Account> accounts)
	{
		SwingUtilities.invokeLater(() -> model.setAccounts(accounts));
	}

	public void setAccountStatus(String characterId, String status)
	{
		SwingUtilities.invokeLater(() -> model.setStatus(characterId, status));
	}

	public void setStatus(String text)
	{
		SwingUtilities.invokeLater(() -> status.setText(text));
	}
}
