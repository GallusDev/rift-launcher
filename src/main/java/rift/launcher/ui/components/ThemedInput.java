package rift.launcher.ui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.Icon;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;
import rift.launcher.ui.theme.RiftTheme;

/**
 * Themed text fields and checkboxes.
 *
 * <p>Colours alone are not enough for these two: the look-and-feel draws the field's border and the
 * checkbox's box itself, so both keep their platform chrome no matter what foreground and background
 * are set. Each needs its own painting to sit on a card without looking pasted in from another app.
 */
public final class ThemedInput
{
	private ThemedInput()
	{
	}

	/**
	 * Wraps a text component in a rounded, themed surround and returns the wrapper to add to a layout.
	 *
	 * <p>The fill is painted by the wrapper rather than by a border on the field itself: Swing paints
	 * borders <i>after</i> the component, so a border that fills its own background paints straight
	 * over the text -- which looks exactly like a field that refuses to accept typing.
	 */
	public static JComponent field(JTextComponent component)
	{
		component.setForeground(RiftTheme.TEXT);
		component.setCaretColor(RiftTheme.ACCENT_BRIGHT);
		component.setSelectionColor(new Color(0x8B, 0x5C, 0xF6, 90));
		component.setSelectedTextColor(RiftTheme.TEXT);
		component.setFont(RiftTheme.regular(13));
		component.setOpaque(false);
		component.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
		return new FieldSurround(component);
	}

	/** The rounded surface a text field sits on, painted before the field draws its text. */
	private static final class FieldSurround extends JPanel
	{
		private final JTextComponent field;

		FieldSurround(JTextComponent field)
		{
			this.field = field;
			setOpaque(false);
			setLayout(new java.awt.BorderLayout());
			setBorder(BorderFactory.createEmptyBorder(8, 11, 8, 11));
			add(field, java.awt.BorderLayout.CENTER);

			// The surround draws the focus ring, so it has to repaint when the field gains or loses
			// focus -- the field's own repaint does not reach its parent.
			field.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusGained(FocusEvent e)
				{
					repaint();
				}

				@Override
				public void focusLost(FocusEvent e)
				{
					repaint();
				}
			});
			// Clicking the padding should put the caret in the field, as it would in a real input.
			addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mousePressed(java.awt.event.MouseEvent e)
				{
					field.requestFocusInWindow();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int arc = RiftTheme.RADIUS_SMALL;
			int w = getWidth();
			int h = getHeight();

			g.setColor(RiftTheme.SURFACE);
			g.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

			boolean focused = field.hasFocus();
			g.setColor(focused ? RiftTheme.ACCENT : RiftTheme.BORDER);
			g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
			if (focused)
			{
				// A second, softer ring reads as a glow without needing a real blur.
				g.setColor(RiftTheme.BORDER_ACCENT);
				g.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
			}
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Replaces the platform box with a painted one, and keeps the label in theme colours. */
	public static JCheckBox checkBox(JCheckBox box)
	{
		box.setOpaque(false);
		box.setFocusPainted(false);
		box.setFont(RiftTheme.regular(13));
		box.setForeground(RiftTheme.TEXT);
		box.setIcon(new CheckBoxIcon(false));
		box.setSelectedIcon(new CheckBoxIcon(true));
		box.setDisabledIcon(new CheckBoxIcon(false));
		box.setDisabledSelectedIcon(new CheckBoxIcon(true));
		box.setIconTextGap(10);
		return box;
	}

	/** A rounded box with a drawn tick: filled with the accent when on, outlined when off. */
	private static final class CheckBoxIcon implements Icon
	{
		private static final int SIZE = 18;
		private final boolean checked;

		CheckBoxIcon(boolean checked)
		{
			this.checked = checked;
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

		@Override
		public void paintIcon(Component c, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			boolean enabled = c.isEnabled();
			int arc = 6;

			if (checked)
			{
				g.setColor(enabled ? RiftTheme.ACCENT : RiftTheme.SURFACE_HOVER);
				g.fillRoundRect(x, y, SIZE, SIZE, arc, arc);
				g.setColor(enabled ? RiftTheme.TEXT : RiftTheme.TEXT_FAINT);
				g.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND,
					java.awt.BasicStroke.JOIN_ROUND));
				g.drawLine(x + 4, y + 9, x + 7, y + 12);
				g.drawLine(x + 7, y + 12, x + 14, y + 5);
			}
			else
			{
				g.setColor(RiftTheme.SURFACE);
				g.fillRoundRect(x, y, SIZE, SIZE, arc, arc);
				g.setColor(enabled ? RiftTheme.BORDER : RiftTheme.SURFACE_HOVER);
				g.drawRoundRect(x, y, SIZE - 1, SIZE - 1, arc, arc);
			}
			g.dispose();
		}
	}
}
