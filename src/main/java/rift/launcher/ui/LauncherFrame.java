package rift.launcher.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import rift.launcher.account.Account;

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

	// Developer section: entering a valid, unrevoked license key is what unlocks developer mode.
	private final JPanel devPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
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
		setSize(720, 460);
		setLocationRelativeTo(null);
		setIconImages(loadIcons());

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
		add(accountBar, BorderLayout.NORTH);

		JTable table = new JTable(model);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowHeight(24);
		table.getColumnModel().getColumn(1).setPreferredWidth(140);
		table.getColumnModel().getColumn(2).setPreferredWidth(110);

		// The session-age column is computed from the clock, so repaint it periodically.
		new Timer(30_000, e -> model.refreshAges()).start();

		JButton launch = new JButton("Launch");
		launch.addActionListener(e ->
		{
			int row = table.getSelectedRow();
			if (row < 0)
			{
				setStatus("Select an account first");
				return;
			}
			Account account = model.accountAt(row);
			model.setStatus(account.getCharacterId(), "Launching...");
			onLaunch.accept(account);
		});

		// Developer key row. Hidden until signed in, since a key is only meaningful for a Rift account.
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
		devPanel.add(new JLabel("License key:"));
		devPanel.add(devKeyField);
		devPanel.add(devVerifyButton);
		devPanel.add(devRemoveButton);
		devPanel.add(devModeBox);
		devPanel.add(devStatusLabel);
		devPanel.setVisible(false);

		JPanel bottom = new JPanel(new BorderLayout());
		JPanel launchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		launchRow.add(launch);
		launchRow.add(status);
		bottom.add(devPanel, BorderLayout.NORTH);
		bottom.add(launchRow, BorderLayout.SOUTH);

		add(new JScrollPane(table), BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
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

	/** Shows/hides the whole developer section — only signed-in users can hold a developer key. */
	public void setDeveloperSectionVisible(boolean visible)
	{
		SwingUtilities.invokeLater(() -> devPanel.setVisible(visible));
	}

	/**
	 * Reflects the verified state of the stored key. A valid key unlocks (and does not auto-select) the
	 * developer-mode checkbox; anything else locks and clears it, so a revoked key can't leave developer
	 * mode armed.
	 */
	public void setDevLicenseVerified(boolean valid, String maskedKey, String tier)
	{
		SwingUtilities.invokeLater(() ->
		{
			devModeBox.setEnabled(valid);
			if (valid)
			{
				devKeyField.setText("");
				devStatusLabel.setText(maskedKey + (tier == null ? "" : "  (" + tier + ")") + " - verified");
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

	/** Updates the account bar: a non-null name shows "Signed in as ..." + a Sign-out button. */
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
			// Signing out must also drop developer mode, so the next user can't inherit it.
			devPanel.setVisible(signedIn);
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
