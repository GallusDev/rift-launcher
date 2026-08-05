package rift.launcher.ui;

import java.awt.Component;
import java.util.function.IntConsumer;
import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * Turns one table column into a column of buttons, so each row can be actioned directly instead of
 * selecting a row and using a control elsewhere in the window.
 *
 * <p>Swing has no button column: a cell can only receive clicks if the model reports it editable, so
 * this doubles as both the renderer (what you see) and the editor (what receives the click). The
 * action is dispatched with {@code invokeLater} after editing stops, because handlers here typically
 * mutate the same table and doing that mid-edit leaves the table in an inconsistent state.
 */
public class ButtonColumn extends AbstractCellEditor implements TableCellRenderer, TableCellEditor
{
	private final JButton renderButton = new JButton();
	private final JButton editButton = new JButton();
	private final IntConsumer onClick;
	private int editingRow = -1;

	/** @param onClick receives the model row index of the clicked button. */
	public ButtonColumn(JTable table, int column, IntConsumer onClick)
	{
		this.onClick = onClick;
		editButton.addActionListener(e ->
		{
			int row = editingRow;
			fireEditingStopped();
			if (row >= 0)
			{
				SwingUtilities.invokeLater(() -> onClick.accept(row));
			}
		});
		table.getColumnModel().getColumn(column).setCellRenderer(this);
		table.getColumnModel().getColumn(column).setCellEditor(this);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
		boolean hasFocus, int row, int column)
	{
		renderButton.setText(value == null ? "" : value.toString());
		return renderButton;
	}

	@Override
	public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
		int row, int column)
	{
		editingRow = table.convertRowIndexToModel(row);
		editButton.setText(value == null ? "" : value.toString());
		return editButton;
	}

	@Override
	public Object getCellEditorValue()
	{
		return editButton.getText();
	}
}
