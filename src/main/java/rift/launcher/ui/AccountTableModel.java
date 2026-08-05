package rift.launcher.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import javax.swing.table.AbstractTableModel;
import rift.launcher.account.Account;

public class AccountTableModel extends AbstractTableModel
{
	/** The trailing column holds a per-row Launch button rather than text. */
	private static final String[] COLUMNS = {"Name", "Status", "Session age", "Launch"};
	public static final int LAUNCH_COLUMN = 3;
	private static final String DEFAULT_STATUS = "Ready";
	private static final long MINUTE_MS = 60_000L;

	private final List<Account> accounts = new ArrayList<>();
	private final Map<String, String> statuses = new HashMap<>();
	private final LongSupplier clock;

	public AccountTableModel()
	{
		this(System::currentTimeMillis);
	}

	/** Test seam: lets a test pin "now" so the session-age column is deterministic. */
	AccountTableModel(LongSupplier clock)
	{
		this.clock = clock;
	}

	public void setAccounts(List<Account> newAccounts)
	{
		accounts.clear();
		accounts.addAll(newAccounts);
		fireTableDataChanged();
	}

	public Account accountAt(int row)
	{
		return accounts.get(row);
	}

	public void setStatus(String characterId, String status)
	{
		statuses.put(characterId, status);
		for (int i = 0; i < accounts.size(); i++)
		{
			if (accounts.get(i).getCharacterId().equals(characterId))
			{
				fireTableRowsUpdated(i, i);
				return;
			}
		}
	}

	@Override
	public int getRowCount()
	{
		return accounts.size();
	}

	@Override
	public int getColumnCount()
	{
		return COLUMNS.length;
	}

	@Override
	public String getColumnName(int column)
	{
		return COLUMNS[column];
	}

	@Override
	public Object getValueAt(int row, int column)
	{
		Account a = accounts.get(row);
		switch (column)
		{
			case 0:
				return a.getDisplayName();
			case 1:
				return statuses.getOrDefault(a.getCharacterId(), DEFAULT_STATUS);
			case 2:
				return formatAge(clock.getAsLong() - a.getAddedAt());
			case LAUNCH_COLUMN:
				return "Launch";
			default:
				return "";
		}
	}

	/** Only the Launch column is "editable" — that is how Swing routes clicks to a cell editor. */
	@Override
	public boolean isCellEditable(int row, int column)
	{
		return column == LAUNCH_COLUMN;
	}

	/** Repaints the rows so the session-age column keeps up with the clock. */
	public void refreshAges()
	{
		if (!accounts.isEmpty())
		{
			fireTableRowsUpdated(0, accounts.size() - 1);
		}
	}

	/**
	 * Renders an elapsed duration compactly: "just now", "5m", "1h 30m", "1d 2h". Negative values
	 * (clock skew) clamp to zero.
	 */
	static String formatAge(long millis)
	{
		long minutes = Math.max(0L, millis) / MINUTE_MS;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}

		long hours = minutes / 60;
		if (hours < 24)
		{
			long remainingMinutes = minutes % 60;
			return remainingMinutes == 0 ? hours + "h" : hours + "h " + remainingMinutes + "m";
		}

		long days = hours / 24;
		long remainingHours = hours % 24;
		return remainingHours == 0 ? days + "d" : days + "d " + remainingHours + "h";
	}
}
