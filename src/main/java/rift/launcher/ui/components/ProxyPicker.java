package rift.launcher.ui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JList;
import rift.launcher.proxy.ProxyEntry;
import rift.launcher.ui.theme.RiftTheme;

/**
 * An account's proxy dropdown: a direct connection, each configured proxy with a dot for its last
 * test result, or -- if the account points at one that has been deleted -- that, flagged.
 *
 * <p>Choosing an option assigns it at once; there is no Save. It takes effect on the next launch,
 * because the proxy is handed to the client as it starts, so the card locks the dropdown while its
 * client is running rather than let a change look as though it applied to the live session.
 */
public class ProxyPicker extends JComboBox<ProxyChoice>
{
	static final int WIDTH = 190;

	private final String assigned;

	public ProxyPicker(ProxyChoice.Options options, String assignedProxyId, Consumer<String> onAssign)
	{
		super(options.getChoices().toArray(new ProxyChoice[0]));
		this.assigned = assignedProxyId;
		// Before the listener: building the dropdown must not assign anything.
		setSelectedIndex(options.getSelected());

		setFont(RiftTheme.regular(13));
		setForeground(RiftTheme.TEXT);
		setBackground(RiftTheme.SURFACE);
		setRenderer(new ChoiceRenderer());
		setMaximumRowCount(10);
		putClientProperty("FlatLaf.style", style());
		refreshState();

		addActionListener(e ->
		{
			ProxyChoice chosen = (ProxyChoice) getSelectedItem();
			// JComboBox fires on every pick, including re-picking the option already shown, so compare
			// against the stored assignment: only a real change is worth a write.
			if (chosen != null && chosen.differsFrom(assigned))
			{
				onAssign.accept(chosen.getId());
			}
			refreshState();
		});
	}

	/**
	 * Colour and tooltip for the current choice.
	 *
	 * <p>The colour goes on the dropdown itself, not only through the renderer: FlatLaf paints the
	 * closed box's text in the dropdown's own foreground and overrides whatever the renderer set, so a
	 * deleted proxy read in plain white there -- in the one place it most needs to stand out.
	 */
	void refreshState()
	{
		setForeground(textColor((ProxyChoice) getSelectedItem()));
		// The tooltip: what the chosen option points at, so the address is a hover away without
		// widening the card.
		if (!isEnabled())
		{
			setToolTipText("Close this client to change its proxy - it applies from the next launch");
			return;
		}
		ProxyChoice chosen = (ProxyChoice) getSelectedItem();
		setToolTipText(chosen == null || chosen.getDetail() == null ? "Proxy for this account"
			: chosen.getDetail());
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		refreshState();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(WIDTH, super.getPreferredSize().height);
	}

	/**
	 * FlatLaf styling, in the launcher's colours. Every key here was checked against the
	 * FlatComboBoxUI and FlatRoundBorder fields of the bundled FlatLaf: an unknown key is not an error
	 * at compile time, only a warning in the log once the dropdown first paints.
	 */
	private static String style()
	{
		return "arc: 8"
			+ "; borderColor: " + hex(RiftTheme.BORDER)
			+ "; disabledBorderColor: " + hex(RiftTheme.BORDER)
			+ "; focusedBorderColor: " + hex(RiftTheme.ACCENT)
			+ "; focusWidth: 0; innerFocusWidth: 0"
			+ "; focusedBackground: " + hex(RiftTheme.SURFACE)
			+ "; disabledBackground: " + hex(RiftTheme.SURFACE)
			+ "; disabledForeground: " + hex(RiftTheme.TEXT_FAINT)
			+ "; buttonBackground: " + hex(RiftTheme.SURFACE)
			+ "; buttonSeparatorWidth: 0"
			+ "; buttonArrowColor: " + hex(RiftTheme.TEXT_MUTED)
			+ "; buttonHoverArrowColor: " + hex(RiftTheme.TEXT)
			+ "; buttonDisabledArrowColor: " + hex(RiftTheme.TEXT_FAINT)
			+ "; popupBackground: " + hex(RiftTheme.SURFACE_RAISED);
	}

	private static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	/** Amber for a deleted proxy, muted for Direct, normal text for a proxy. */
	static Color textColor(ProxyChoice choice)
	{
		if (choice == null)
		{
			return RiftTheme.TEXT;
		}
		switch (choice.getKind())
		{
			case MISSING:
				return RiftTheme.WARN;
			case DIRECT:
				return RiftTheme.TEXT_MUTED;
			default:
				return RiftTheme.TEXT;
		}
	}

	/** Green for a proxy that last tested fine, red for one that failed, faint for untested. */
	static Color dotColor(ProxyChoice choice)
	{
		if (choice.getKind() == ProxyChoice.Kind.MISSING)
		{
			return RiftTheme.WARN;
		}
		if (choice.getKind() == ProxyChoice.Kind.DIRECT)
		{
			return null;
		}
		ProxyEntry.Status status = choice.getStatus();
		if (status == ProxyEntry.Status.OK)
		{
			return RiftTheme.OK;
		}
		if (status == ProxyEntry.Status.AUTH_FAILED || status == ProxyEntry.Status.UNREACHABLE)
		{
			return RiftTheme.ERROR;
		}
		return RiftTheme.TEXT_FAINT;
	}

	private static final class ChoiceRenderer extends DefaultListCellRenderer
	{
		ChoiceRenderer()
		{
			// Here rather than per cell: a JLabel decides whether to render HTML as its text is set,
			// and the superclass sets the text before a per-cell call could get in first. Nicknames are
			// typed or pasted in from a provider's list, and a JLabel otherwise renders <html> and
			// fetches whatever images it names.
			putClientProperty("html.disable", Boolean.TRUE);
		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
			boolean focused)
		{
			super.getListCellRendererComponent(list, value, index, selected, focused);
			ProxyChoice choice = (ProxyChoice) value;
			setText(choice == null ? "" : choice.getLabel());
			setIcon(new Dot(choice == null ? null : dotColor(choice)));
			setIconTextGap(8);
			setFont(RiftTheme.regular(13));
			setForeground(textColor(choice));
			if (index < 0)
			{
				// The closed dropdown: sit on the dropdown's own surface, not a list row's.
				setOpaque(false);
				setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
			}
			else
			{
				setOpaque(true);
				setBackground(selected ? RiftTheme.SURFACE_HOVER : RiftTheme.SURFACE_RAISED);
				setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
			}
			return this;
		}
	}

	/** A status dot, or the same space left empty so labels stay aligned. */
	private static final class Dot implements Icon
	{
		private static final int SIZE = 8;
		private final Color color;

		Dot(Color color)
		{
			this.color = color;
		}

		@Override
		public void paintIcon(Component c, Graphics graphics, int x, int y)
		{
			if (color == null)
			{
				return;
			}
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(color);
			g.fillOval(x, y, SIZE, SIZE);
			g.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}
}
