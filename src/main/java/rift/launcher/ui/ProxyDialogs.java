package rift.launcher.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import rift.launcher.proxy.ProxyEntry;
import rift.launcher.proxy.ProxyParser;

/**
 * Add / bulk-add dialogs for proxies.
 *
 * <p>Both are built around pasting, because that is how proxies actually arrive — copied from a
 * provider, in whatever format that provider chose. The single-add dialog shows what it understood
 * <i>before</i> saving, so a mistyped entry is caught at the point of entry rather than at launch.
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
		JTextField nickname = new JTextField(24);
		JTextField connection = new JTextField(24);
		JLabel preview = new JLabel(" ");
		preview.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		Runnable updatePreview = () ->
		{
			String text = connection.getText().trim();
			if (text.isEmpty())
			{
				preview.setText(" ");
				return;
			}
			ProxyEntry parsed = ProxyParser.parse(text);
			preview.setText(parsed == null
				? "Not a proxy yet - expected host:port"
				: "Host " + parsed.getHost() + "   Port " + parsed.getPort()
					+ (parsed.hasAuth() ? "   Auth as " + parsed.getUsername() : "   No auth"));
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

		JPanel fields = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
		fields.add(labelled("Nickname (optional):", nickname));
		fields.add(labelled("Proxy:", connection));
		fields.add(new JLabel("host:port, host:port:user:pass, user:pass@host:port, or socks5://..."));
		fields.add(preview);

		int choice = JOptionPane.showConfirmDialog(parent, fields, "Add proxy",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
		{
			return null;
		}

		ProxyEntry parsed = ProxyParser.parse(connection.getText());
		if (parsed == null)
		{
			JOptionPane.showMessageDialog(parent,
				"That doesn't look like a proxy. Expected something like 1.2.3.4:1080",
				"Add proxy", JOptionPane.WARNING_MESSAGE);
			return null;
		}
		String name = nickname.getText().trim();
		parsed.setNickname(name.isEmpty() ? parsed.endpoint() : name);
		return parsed;
	}

	/**
	 * A whole pasted list. This is the case the comparable launchers don't handle at all — providers
	 * sell proxies in blocks, and adding fifty through a one-at-a-time dialog is nobody's idea of a
	 * good time.
	 */
	static List<ProxyEntry> addMany(Component parent)
	{
		JTextArea area = new JTextArea(12, 44);
		area.setLineWrap(false);
		JLabel hint = new JLabel("One per line. Blank lines and # comments are ignored.");
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(hint, BorderLayout.NORTH);
		panel.add(new JScrollPane(area), BorderLayout.CENTER);
		panel.setPreferredSize(new Dimension(520, 300));

		int choice = JOptionPane.showConfirmDialog(parent, panel, "Bulk add proxies",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
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
				summary.append("\n  line ").append(failures.get(i).getLineNumber())
					.append(": ").append(trim(failures.get(i).getLine()));
			}
			if (failures.size() > 8)
			{
				summary.append("\n  ...and ").append(failures.size() - 8).append(" more");
			}
		}
		JOptionPane.showMessageDialog(parent, summary.toString(), "Bulk add proxies",
			result.getFailures().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);

		return new ArrayList<>(result.getParsed());
	}

	private static String trim(String line)
	{
		String s = line.trim();
		return s.length() > 48 ? s.substring(0, 45) + "..." : s;
	}

	private static JPanel labelled(String label, Component field)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		JLabel l = new JLabel(label);
		l.setPreferredSize(new Dimension(150, 22));
		row.add(l);
		row.add(field);
		return row;
	}
}
