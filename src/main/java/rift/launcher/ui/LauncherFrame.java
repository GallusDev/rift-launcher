package rift.launcher.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Image;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
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

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
		bottom.add(launch);
		bottom.add(status);

		add(new JScrollPane(table), BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
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
