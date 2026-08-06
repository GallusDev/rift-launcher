package rift.launcher.ui;

/**
 * Renders how long ago something happened, compactly.
 *
 * <p>Lives on its own rather than inside a table model because the account cards need it too, and a
 * formatter tied to a widget cannot be reused by the widget that replaced it.
 */
public final class AccountAge
{
	private static final long MINUTE_MS = 60_000L;

	private AccountAge()
	{
	}

	/**
	 * "just now", "5m", "1h 30m", "1d 2h". Negative values (clock skew) clamp to zero rather than
	 * rendering a nonsense future duration.
	 */
	public static String format(long millis)
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
