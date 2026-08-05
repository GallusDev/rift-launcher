package rift.shim;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * The "which client?" dialog shown when the Jagex Launcher starts us and the user has not remembered a
 * choice. Modal and self-contained: it blocks until a button is pressed, then reports the choice.
 *
 * <p>RuneLite is always offered — it is the safe fallback and the thing the user's Play button
 * nominally launches. Vortex appears only when it is already installed.
 */
public final class Chooser
{
	public static final String RIFT = "rift";
	public static final String RUNELITE = "runelite";
	public static final String VORTEX = "vortex";

	private String choice;
	private boolean remember;

	/** Shows the dialog and blocks. Returns one of the constants, or null if the user closed it. */
	public String prompt(boolean riftAvailable, String riftUnavailableReason, boolean vortexAvailable)
	{
		JDialog dialog = new JDialog((java.awt.Frame) null, "Rift", true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		JLabel title = new JLabel("Which client would you like to launch?", SwingConstants.CENTER);
		title.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));

		JButton rift = new JButton("Launch Rift");
		rift.setEnabled(riftAvailable);
		if (!riftAvailable)
		{
			rift.setToolTipText(riftUnavailableReason);
		}
		JButton runelite = new JButton("Launch RuneLite");
		JButton vortex = new JButton("Launch Vortex");

		rift.addActionListener(e ->
		{
			choice = RIFT;
			dialog.dispose();
		});
		runelite.addActionListener(e ->
		{
			choice = RUNELITE;
			dialog.dispose();
		});
		vortex.addActionListener(e ->
		{
			choice = VORTEX;
			dialog.dispose();
		});

		JPanel buttons = new JPanel(new GridLayout(1, vortexAvailable ? 3 : 2, 10, 0));
		buttons.setBorder(BorderFactory.createEmptyBorder(0, 18, 4, 18));
		buttons.add(rift);
		buttons.add(runelite);
		if (vortexAvailable)
		{
			buttons.add(vortex);
		}

		JCheckBox rememberBox = new JCheckBox("Remember my choice");
		rememberBox.addActionListener(e -> remember = rememberBox.isSelected());
		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
		bottom.add(rememberBox);
		JLabel hint = new JLabel("You can change this in the Rift launcher.", SwingConstants.CENTER);
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		JPanel south = new JPanel(new BorderLayout());
		south.add(bottom, BorderLayout.NORTH);
		south.add(hint, BorderLayout.SOUTH);

		dialog.setLayout(new BorderLayout());
		dialog.add(title, BorderLayout.NORTH);
		dialog.add(buttons, BorderLayout.CENTER);
		dialog.add(south, BorderLayout.SOUTH);
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setAlwaysOnTop(true);
		dialog.setVisible(true);

		return choice;
	}

	public boolean isRemember()
	{
		return remember;
	}
}
