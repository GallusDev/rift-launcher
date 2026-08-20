package rift.launcher.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * The offline developer unlock prompt, opened with Ctrl+D.
 *
 * <p>Two modes, chosen by whether a credential already exists on this machine:
 * <ul>
 * <li><b>Entry</b> — username and password, checked against the stored hash.</li>
 * <li><b>Setup</b> — first run: choose the credential, confirming the password so a typo does not
 *     lock the developer out of their own offline unlock.</li>
 * </ul>
 *
 * <p>Passwords live in {@code char[]} and are zeroed as soon as they have been used, rather than
 * sitting in an interned {@link String} until the next GC. That is standard practice for a Swing
 * password field and costs nothing here.
 */
final class OfflineDevDialog extends JDialog
{
	/** What the user entered, or null if they cancelled. Password is the caller's to clear. */
	static final class Result
	{
		final String username;
		final char[] password;
		final boolean setup;

		Result(String username, char[] password, boolean setup)
		{
			this.username = username;
			this.password = password;
			this.setup = setup;
		}

		void clear()
		{
			Arrays.fill(password, '\0');
		}
	}

	private final JTextField userField = new JTextField(18);
	private final JPasswordField passField = new JPasswordField(18);
	private final JPasswordField confirmField = new JPasswordField(18);
	private final JLabel error = new JLabel(" ");
	private final boolean setup;

	private Result result;

	private OfflineDevDialog(Window owner, boolean setup)
	{
		super(owner, setup ? "Set up offline developer unlock" : "Offline developer unlock",
			ModalityType.APPLICATION_MODAL);
		this.setup = setup;

		JPanel form = new JPanel(new GridBagLayout());
		form.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;

		int row = 0;
		c.gridx = 0;
		c.gridy = row;
		form.add(new JLabel(setup
			? "<html>No offline credential is set on this machine.<br>"
			+ "Choose one to unlock developer mode while the Rift server is unreachable.</html>"
			: "<html>Unlocks developer mode for this launcher session,<br>"
			+ "while the Rift server is unreachable.</html>"), c);
		c.gridwidth = 1;

		row++;
		c.gridx = 0;
		c.gridy = row;
		form.add(new JLabel("Username"), c);
		c.gridx = 1;
		form.add(userField, c);

		row++;
		c.gridx = 0;
		c.gridy = row;
		form.add(new JLabel("Password"), c);
		c.gridx = 1;
		form.add(passField, c);

		if (setup)
		{
			row++;
			c.gridx = 0;
			c.gridy = row;
			form.add(new JLabel("Confirm"), c);
			c.gridx = 1;
			form.add(confirmField, c);
		}

		row++;
		c.gridx = 0;
		c.gridy = row;
		c.gridwidth = 2;
		error.setForeground(new java.awt.Color(220, 90, 90));
		form.add(error, c);

		JButton ok = new JButton(setup ? "Save" : "Unlock");
		JButton cancel = new JButton("Cancel");
		JPanel buttons = new JPanel();
		buttons.add(cancel);
		buttons.add(ok);

		ok.addActionListener(e -> submit());
		cancel.addActionListener(e -> dispose());
		// Enter submits, Escape cancels -- a modal prompt that ignores both is irritating to use.
		getRootPane().setDefaultButton(ok);
		getRootPane().registerKeyboardAction(e -> dispose(),
			javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
			JPanel.WHEN_IN_FOCUSED_WINDOW);

		setLayout(new BorderLayout());
		add(form, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	private void submit()
	{
		String user = userField.getText() == null ? "" : userField.getText().trim();
		char[] pass = passField.getPassword();
		if (user.isEmpty() || pass.length == 0)
		{
			error.setText("Username and password are required");
			Arrays.fill(pass, '\0');
			return;
		}
		if (setup)
		{
			char[] confirm = confirmField.getPassword();
			boolean same = Arrays.equals(pass, confirm);
			Arrays.fill(confirm, '\0');
			if (!same)
			{
				error.setText("Passwords do not match");
				Arrays.fill(pass, '\0');
				return;
			}
		}
		result = new Result(user, pass, setup);
		dispose();
	}

	/** Shows the prompt and returns what was entered, or null if cancelled. */
	static Result prompt(Window owner, boolean setup)
	{
		OfflineDevDialog dialog = new OfflineDevDialog(owner, setup);
		dialog.setVisible(true);
		Result r = dialog.result;
		SwingUtilities.invokeLater(() ->
		{
			Arrays.fill(dialog.passField.getPassword(), '\0');
			dialog.passField.setText("");
			dialog.confirmField.setText("");
		});
		return r;
	}
}
