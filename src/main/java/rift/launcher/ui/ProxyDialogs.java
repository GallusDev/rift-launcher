package rift.launcher.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import rift.launcher.proxy.ProxyEntry;
import rift.launcher.proxy.ProxyParser;
import rift.launcher.ui.components.RiftDialog;
import rift.launcher.ui.components.ThemedInput;
import rift.launcher.ui.theme.RiftTheme;

/**
 * Add / bulk-add dialogs for proxies.
 *
 * <p>Both are built around pasting, because that is how proxies actually arrive — copied from a
 * provider, in whatever format that provider chose. The single-add dialog shows what it understood
 * <i>before</i> saving, so a mistyped entry is caught at the point of entry rather than at launch.
 *
 * <p>Built on {@link RiftDialog} rather than {@code JOptionPane}: the platform dialog cannot be
 * themed far enough, and a popup that arrives in a different visual language undoes the rest of the
 * window.
 */
final class ProxyDialogs
{
	private ProxyDialogs()
	{
	}

	/**
	 * One proxy from a pasted string, or {@code null} if cancelled.
	 *
	 * <p>A single field rather than separate host/port/user/pass boxes: providers hand out one string,
	 * and splitting it by hand is work the parser can do. The live preview is what makes that safe.
	 */
	static ProxyEntry addOne(Component parent)
	{
		JTextField nickname = new JTextField();
		JTextField connection = new JTextField();

		JLabel preview = new JLabel(" ");
		preview.setFont(RiftTheme.regular(12));
		preview.setForeground(RiftTheme.TEXT_FAINT);
		preview.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		Runnable updatePreview = () ->
		{
			String text = connection.getText().trim();
			if (text.isEmpty())
			{
				preview.setText(" ");
				preview.setForeground(RiftTheme.TEXT_FAINT);
				return;
			}
			ProxyEntry parsed = ProxyParser.parse(text);
			if (parsed == null)
			{
				preview.setText("Not a proxy yet - expected host:port");
				preview.setForeground(RiftTheme.WARN);
			}
			else
			{
				preview.setText("Host " + parsed.getHost() + "    Port " + parsed.getPort()
					+ (parsed.hasAuth() ? "    Auth as " + parsed.getUsername() : "    No auth"));
				preview.setForeground(RiftTheme.OK);
			}
		};
		connection.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updatePreview.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updatePreview.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updatePreview.run();
			}
		});

		JPanel fields = column();
		fields.add(fieldLabel("Proxy"));
		fields.add(Box.createVerticalStrut(6));
		fields.add(sized(ThemedInput.field(connection), 430));
		fields.add(Box.createVerticalStrut(7));
		fields.add(hint("host:port  |  host:port:user:pass  |  user:pass@host:port  |  socks5://..."));
		fields.add(Box.createVerticalStrut(5));
		fields.add(preview);
		fields.add(Box.createVerticalStrut(16));
		fields.add(fieldLabel("Nickname (optional)"));
		fields.add(Box.createVerticalStrut(6));
		fields.add(sized(ThemedInput.field(nickname), 430));

		RiftDialog dialog = new RiftDialog(parent, "Add proxy",
			"Paste it in whatever format your provider gave you.");
		dialog.setBody(fields);
		dialog.addCancelAndConfirm("Add proxy");
		if (!dialog.showDialog())
		{
			return null;
		}

		ProxyEntry parsed = ProxyParser.parse(connection.getText());
		if (parsed == null)
		{
			message(parent, "Add proxy",
				"That does not look like a proxy.\nExpected something like 1.2.3.4:1080");
			return null;
		}
		String name = nickname.getText().trim();
		parsed.setNickname(name.isEmpty() ? parsed.endpoint() : name);
		return parsed;
	}

	/**
	 * A whole pasted list. This is the case the comparable launchers do not handle at all — providers
	 * sell proxies in blocks, and adding fifty through a one-at-a-time dialog is nobody's idea of a
	 * good time.
	 */
	static List<ProxyEntry> addMany(Component parent)
	{
		JTextArea area = new JTextArea(14, 46);
		area.setLineWrap(false);
		// Monospaced so columns of host:port line up and a malformed line stands out while pasting.
		area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		area.setForeground(RiftTheme.TEXT);
		area.setBackground(RiftTheme.SURFACE);
		area.setCaretColor(RiftTheme.ACCENT_BRIGHT);
		area.setSelectionColor(new java.awt.Color(0x8B, 0x5C, 0xF6, 90));
		area.setSelectedTextColor(RiftTheme.TEXT);
		area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		JScrollPane scroll = new JScrollPane(area);
		scroll.setBorder(BorderFactory.createLineBorder(RiftTheme.BORDER));
		scroll.getViewport().setBackground(RiftTheme.SURFACE);
		scroll.setPreferredSize(new Dimension(560, 300));
		scroll.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		JPanel panel = column();
		panel.add(hint("One per line. Blank lines and # comments are ignored, duplicates are skipped."));
		panel.add(Box.createVerticalStrut(10));
		panel.add(scroll);

		RiftDialog dialog = new RiftDialog(parent, "Bulk add proxies",
			"Paste a whole provider list - every common format is understood.");
		dialog.setBody(panel);
		dialog.addCancelAndConfirm("Add proxies");
		if (!dialog.showDialog())
		{
			return Collections.emptyList();
		}

		ProxyParser.BulkResult result = ProxyParser.parseAll(area.getText());
		if (result.getParsed().isEmpty() && result.getFailures().isEmpty())
		{
			return Collections.emptyList();
		}

		// Report exactly what happened. Silently dropping unreadable lines is how someone ends up
		// wondering why 48 of their 50 proxies are missing.
		StringBuilder summary = new StringBuilder();
		summary.append("Parsed ").append(result.getParsed().size()).append(" proxies.");
		if (result.getDuplicatesSkipped() > 0)
		{
			summary.append("\nSkipped ").append(result.getDuplicatesSkipped()).append(" duplicates.");
		}
		if (!result.getFailures().isEmpty())
		{
			summary.append("\n\nCouldn't read ").append(result.getFailures().size()).append(" line(s):");
			List<ProxyParser.Failure> failures = result.getFailures();
			for (int i = 0; i < Math.min(8, failures.size()); i++)
			{
				summary.append("\n    line ").append(failures.get(i).getLineNumber())
					.append(":  ").append(trim(failures.get(i).getLine()));
			}
			if (failures.size() > 8)
			{
				summary.append("\n    ...and ").append(failures.size() - 8).append(" more");
			}
		}
		message(parent, "Bulk add proxies", summary.toString());

		return new ArrayList<>(result.getParsed());
	}

	/** A themed stand-in for {@code JOptionPane.showMessageDialog}. */
	private static void message(Component parent, String title, String text)
	{
		JTextArea body = new JTextArea(text);
		body.setEditable(false);
		body.setOpaque(false);
		body.setFont(RiftTheme.regular(13));
		body.setForeground(RiftTheme.TEXT_MUTED);
		body.setBorder(null);

		RiftDialog dialog = new RiftDialog(parent, title, null);
		dialog.setBody(body);
		dialog.addAction("OK", true, dialog::confirm);
		dialog.showDialog();
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		return panel;
	}

	private static JLabel fieldLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(RiftTheme.regular(12));
		label.setForeground(RiftTheme.TEXT_MUTED);
		label.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel hint(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(RiftTheme.regular(11));
		label.setForeground(RiftTheme.TEXT_FAINT);
		label.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		return label;
	}

	/** Pins a field's size so BoxLayout does not stretch it to fill the dialog's height. */
	private static JComponent sized(JComponent component, int width)
	{
		component.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		component.setPreferredSize(new Dimension(width, 38));
		component.setMaximumSize(new Dimension(width, 38));
		return component;
	}

	private static String trim(String line)
	{
		String s = line.trim();
		return s.length() > 48 ? s.substring(0, 45) + "..." : s;
	}
}
