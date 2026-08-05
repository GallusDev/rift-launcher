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
	private static final String[] COLUMNS = {"Name", "Status", "Session age", "Proxy", "Launch"};
	public static final int PROXY_COLUMN = 3;
	public static final int LAUNCH_COLUMN = 4;

	/** Shown when an account connects directly. Also the dropdown's first option. */
	public static final String NO_PROXY = "Direct";
	private static final String DEFAULT_STATUS = "Ready";
	private static final long MINUTE_MS = 60_000L;

	private final List<Account> accounts = new ArrayList<>();
	private final List<rift.launcher.proxy.ProxyEntry> proxies = new ArrayList<>();
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
			case PROXY_COLUMN:
				return proxyNameFor(a.getProxyId());
			case LAUNCH_COLUMN:
				return "Launch";
			default:
				return "";
		}
	}

	/** Launch (a button) and Proxy (a dropdown) are the interactive cells. */
	@Override
	public boolean isCellEditable(int row, int column)
	{
		return column == LAUNCH_COLUMN || column == PROXY_COLUMN;
	}

	/** The proxies offered in the per-account dropdown. */
	public void setProxies(List<rift.launcher.proxy.ProxyEntry> newProxies)
	{
		proxies.clear();
		proxies.addAll(newProxies);
		fireTableDataChanged();
	}

	/** Dropdown options: "Direct" plus every saved proxy, by nickname. */
	public String[] proxyChoices()
	{
		String[] choices = new String[proxies.size() + 1];
		choices[0] = NO_PROXY;
		for (int i = 0; i < proxies.size(); i++)
		{
			choices[i + 1] = proxies.get(i).getNickname();
		}
		return choices;
	}

	/** The proxy id behind a dropdown label, or null for "Direct". */
	public String proxyIdForChoice(String choice)
	{
		for (rift.launcher.proxy.ProxyEntry p : proxies)
		{
			if (p.getNickname() != null && p.getNickname().equals(choice))
			{
				return p.getId();
			}
		}
		return null;
	}

	/**
	 * The label for an assigned proxy id. An id with no matching proxy reads as "missing" rather than
	 * "Direct" -- the account will refuse to launch, and showing "Direct" would hide why.
	 */
	String proxyNameFor(String proxyId)
	{
		if (proxyId == null)
		{
			return NO_PROXY;
		}
		for (rift.launcher.proxy.ProxyEntry p : proxies)
		{
			if (proxyId.equals(p.getId()))
			{
				return p.getNickname();
			}
		}
		return "(missing)";
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
