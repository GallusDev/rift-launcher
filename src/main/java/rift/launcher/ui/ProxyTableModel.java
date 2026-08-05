package rift.launcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import javax.swing.table.AbstractTableModel;
import rift.launcher.proxy.ProxyEntry;

/**
 * The Proxies table.
 *
 * <p>Shows the test results rather than just the endpoint, because those are what decide whether a
 * proxy is usable: how slow it is, and — the column no comparable launcher has — the exit IP the game
 * server will actually see. Results are timestamped, since a proxy that worked an hour ago may be dead.
 */
public class ProxyTableModel extends AbstractTableModel
{
	private static final String[] COLUMNS =
		{"Nickname", "Address", "Auth", "Status", "Latency", "Exit IP", "Tested"};

	public static final int STATUS_COLUMN = 3;

	private static final long MINUTE_MS = 60_000L;

	private final List<ProxyEntry> proxies = new ArrayList<>();
	private final LongSupplier clock;

	public ProxyTableModel()
	{
		this(System::currentTimeMillis);
	}

	/** Test seam: lets a test pin "now" so the tested-ago column is deterministic. */
	ProxyTableModel(LongSupplier clock)
	{
		this.clock = clock;
	}

	public void setProxies(List<ProxyEntry> newProxies)
	{
		proxies.clear();
		proxies.addAll(newProxies);
		fireTableDataChanged();
	}

	public ProxyEntry proxyAt(int row)
	{
		return proxies.get(row);
	}

	public List<ProxyEntry> all()
	{
		return new ArrayList<>(proxies);
	}

	@Override
	public int getRowCount()
	{
		return proxies.size();
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
		ProxyEntry p = proxies.get(row);
		switch (column)
		{
			case 0:
				return p.getNickname();
			case 1:
				return p.endpoint();
			case 2:
				// Whether credentials are set, never the credentials themselves.
				return p.hasAuth() ? "yes" : "-";
			case STATUS_COLUMN:
				return describe(p.getLastStatus());
			case 4:
				return p.getLastLatencyMs() == null ? "-" : p.getLastLatencyMs() + " ms";
			case 5:
				return p.getLastExitIp() == null ? "-" : p.getLastExitIp();
			case 6:
				return testedAgo(p.getLastTestedAt());
			default:
				return "";
		}
	}

	static String describe(ProxyEntry.Status status)
	{
		switch (status)
		{
			case OK:
				return "Working";
			case AUTH_FAILED:
				// Says what to fix. "Failed" would send someone to debug their network instead.
				return "Bad credentials";
			case UNREACHABLE:
				return "Unreachable";
			default:
				return "Not tested";
		}
	}

	/** "just now", "5m ago", "2h ago", "3d ago" — staleness matters more than the exact time. */
	String testedAgo(Long testedAt)
	{
		if (testedAt == null)
		{
			return "never";
		}
		long minutes = Math.max(0L, clock.getAsLong() - testedAt) / MINUTE_MS;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		long hours = minutes / 60;
		if (hours < 24)
		{
			return hours + "h ago";
		}
		return (hours / 24) + "d ago";
	}
}
