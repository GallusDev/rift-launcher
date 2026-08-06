package rift.launcher.ui.components;

import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import rift.launcher.ui.theme.RiftTheme;

/**
 * A JTable dressed to match the rest of the window.
 *
 * <p>Swing's defaults — light grid lines, a raised header, a blue selection — read as a different
 * application dropped into the middle of this one. This strips the chrome back to flat rows, a quiet
 * header and an accent-tinted selection, so a table can sit on a card without breaking the surface.
 */
public final class ThemedTable
{
	private ThemedTable()
	{
	}

	public static JTable create(TableModel model)
	{
		JTable table = new JTable(model);
		table.setFont(RiftTheme.regular(13));
		table.setForeground(RiftTheme.TEXT);
		table.setBackground(RiftTheme.SURFACE);
		table.setRowHeight(30);
		table.setShowGrid(false);
		table.setIntercellSpacing(new java.awt.Dimension(0, 0));
		table.setSelectionBackground(new Color(0x8B, 0x5C, 0xF6, 45));
		table.setSelectionForeground(RiftTheme.TEXT);
		table.setBorder(null);
		table.setFillsViewportHeight(true);

		JTableHeader header = table.getTableHeader();
		header.setFont(RiftTheme.bold(11));
		header.setForeground(RiftTheme.TEXT_MUTED);
		header.setBackground(RiftTheme.SURFACE);
		header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, RiftTheme.BORDER));
		header.setReorderingAllowed(false);

		// Padding has to come from the renderer: a JTable gives cells no inset of their own, so text
		// otherwise sits flush against the row edge.
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
				boolean focus, int row, int column)
			{
				Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
				if (c instanceof JLabel)
				{
					((JLabel) c).setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
				}
				if (!selected)
				{
					// Alternating rows, very faintly -- enough to follow a row across, not enough to
					// turn the table into stripes.
					c.setBackground(row % 2 == 0 ? RiftTheme.SURFACE : RiftTheme.SURFACE_RAISED);
					c.setForeground(RiftTheme.TEXT);
				}
				return c;
			}
		});
		return table;
	}

	/** Wraps a themed table in a scroll pane with no border of its own. */
	public static JScrollPane scroll(JTable table)
	{
		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createLineBorder(RiftTheme.BORDER));
		scroll.getViewport().setBackground(RiftTheme.SURFACE);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}
}
