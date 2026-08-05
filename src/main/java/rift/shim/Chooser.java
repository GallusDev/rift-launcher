package rift.shim;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
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
 * <p>Styled to match the Rift launcher (same FlatDarkLaf theme and icon set) so the two read as one
 * product — this dialog is usually the first Rift surface a user sees after pressing Play.
 *
 * <p>RuneLite is always offered: it is the safe fallback and nominally what the Play button launches.
 * Vortex appears only when it is actually installed.
 */
public final class Chooser
{
	public static final String RIFT = "rift";
	public static final String RUNELITE = "runelite";
	public static final String VORTEX = "vortex";

	/** Rift accent, matched to the launcher's branding. */
	private static final Color ACCENT = new Color(0x7C, 0x4D, 0xFF);

	private String choice;
	private boolean remember;

	/** Shows the dialog and blocks. Returns one of the constants, or null if the user closed it. */
	public String prompt(boolean riftAvailable, String riftUnavailableReason, boolean vortexAvailable)
	{
		// Match the launcher's theme. Wrapped because a laf failure must never stop a launch.
		try
		{
			FlatDarkLaf.setup();
		}
		catch (Exception ignored)
		{
			// Fall back to the platform default look; the dialog still works.
		}

		JDialog dialog = new JDialog((java.awt.Frame) null, "Rift", true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setIconImages(loadIcons());

		JLabel heading = new JLabel("Rift", SwingConstants.CENTER);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 22f));
		heading.setForeground(ACCENT);
		heading.setBorder(BorderFactory.createEmptyBorder(16, 24, 2, 24));

		JLabel subtitle = new JLabel("Choose a client to launch", SwingConstants.CENTER);
		subtitle.setForeground(new Color(0x9A, 0x9A, 0xA5));
		subtitle.setBorder(BorderFactory.createEmptyBorder(0, 24, 14, 24));

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(heading, BorderLayout.NORTH);
		header.add(subtitle, BorderLayout.SOUTH);

		JButton rift = accent(new JButton("Launch Rift"));
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
		buttons.setOpaque(false);
		buttons.setBorder(BorderFactory.createEmptyBorder(0, 24, 6, 24));
		buttons.add(rift);
		buttons.add(runelite);
		if (vortexAvailable)
		{
			buttons.add(vortex);
		}

		JCheckBox rememberBox = new JCheckBox("Remember my choice");
		rememberBox.setOpaque(false);
		rememberBox.addActionListener(e -> remember = rememberBox.isSelected());
		JPanel rememberRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
		rememberRow.setOpaque(false);
		rememberRow.add(rememberBox);

		JLabel hint = new JLabel("You can change this in the Rift launcher.", SwingConstants.CENTER);
		hint.setForeground(new Color(0x77, 0x77, 0x80));
		hint.setFont(hint.getFont().deriveFont(11f));
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

		JPanel south = new JPanel(new BorderLayout());
		south.setOpaque(false);
		south.add(rememberRow, BorderLayout.NORTH);
		south.add(hint, BorderLayout.SOUTH);

		JPanel root = new JPanel(new BorderLayout());
		root.add(header, BorderLayout.NORTH);
		root.add(buttons, BorderLayout.CENTER);
		root.add(south, BorderLayout.SOUTH);

		dialog.setContentPane(root);
		dialog.pack();
		dialog.setMinimumSize(new Dimension(vortexAvailable ? 460 : 360, dialog.getHeight()));
		dialog.setResizable(false);
		dialog.setLocationRelativeTo(null);
		dialog.setAlwaysOnTop(true);
		dialog.setVisible(true);

		return choice;
	}

	/** Marks the primary action so Rift reads as the default without disabling the others. */
	private static JButton accent(JButton button)
	{
		button.setBackground(ACCENT);
		button.setForeground(Color.WHITE);
		button.setFont(button.getFont().deriveFont(Font.BOLD));
		return button;
	}

	/** The same icon set the launcher uses, so the taskbar entry matches. */
	private static List<Image> loadIcons()
	{
		List<Image> icons = new ArrayList<>();
		for (int size : new int[]{16, 32, 64, 256})
		{
			URL url = Chooser.class.getResource("/rift/launcher/icon/icon-" + size + ".png");
			if (url != null)
			{
				icons.add(new ImageIcon(url).getImage());
			}
		}
		return icons;
	}

	public boolean isRemember()
	{
		return remember;
	}
}
