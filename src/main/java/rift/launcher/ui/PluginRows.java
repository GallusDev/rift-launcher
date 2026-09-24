package rift.launcher.ui;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import rift.launcher.web.OwnedPlugin;

/**
 * What the Plugins page shows, worked out without touching Swing.
 *
 * <p>Two sources, each with its own gate. The account's plugins come from the server and need a
 * signed-in session; developer plugins are jars in the local dev-plugins folder and need developer
 * access -- a verified key, or the offline unlock. The gates are applied by the caller; this turns
 * whatever made it through into rows.
 *
 * <p>Pure so the wording can be tested, which matters here because the wording carries meaning: "no
 * expiry" and "trial" both arrive from the server as a null expiry date.
 */
public final class PluginRows
{
	/** How the plugin is available, shown as a tag on its row. */
	public enum Tag
	{
		OWNED,
		TRIAL,
		DEV
	}

	/** Access ending within this many days is flagged, so a renewal is not a surprise. */
	static final int SOON_DAYS = 7;

	private PluginRows()
	{
	}

	/** One line on the page. */
	public static final class Row
	{
		private final String title;
		private final String detail;
		private final Tag tag;
		private final boolean attention;

		Row(String title, String detail, Tag tag, boolean attention)
		{
			this.title = title;
			this.detail = detail;
			this.tag = tag;
			this.attention = attention;
		}

		public String getTitle()
		{
			return title;
		}

		public String getDetail()
		{
			return detail;
		}

		public Tag getTag()
		{
			return tag;
		}

		/** Whether the detail needs noticing -- access about to end, or already ended. */
		public boolean needsAttention()
		{
			return attention;
		}
	}

	/** A jar in the dev-plugins folder: just its name and when it was last written. */
	public static final class DevJar
	{
		private final String fileName;
		private final long lastModified;

		public DevJar(String fileName, long lastModified)
		{
			this.fileName = fileName;
			this.lastModified = lastModified;
		}
	}

	/**
	 * The account's plugins, then developer plugins, each group alphabetical.
	 *
	 * <p>A plugin that is both owned and present as a local jar appears twice, deliberately: they are
	 * different builds, and the tag says which is which.
	 */
	public static List<Row> build(List<OwnedPlugin> owned, List<DevJar> dev, Instant now, ZoneId zone,
		Locale locale)
	{
		List<Row> rows = new ArrayList<>();

		List<OwnedPlugin> sortedOwned = new ArrayList<>(owned == null ? Collections.emptyList() : owned);
		sortedOwned.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
		for (OwnedPlugin p : sortedOwned)
		{
			rows.add(owned(p, now, zone, locale));
		}

		// Already in the client's load order: findDevJars sorts the same way the client does.
		for (DevJar jar : dev == null ? Collections.<DevJar>emptyList() : dev)
		{
			rows.add(dev(jar, now, zone, locale));
		}
		return rows;
	}

	static Row owned(OwnedPlugin p, Instant now, ZoneId zone, Locale locale)
	{
		// Checked before the date: a trial's expiry is always null, and read as a purchase it would
		// claim the trial never ends -- the opposite of the truth.
		if (p.isTrial())
		{
			return new Row(p.getName(), "Free trial, limited by play time", Tag.TRIAL, false);
		}

		Instant expires = parse(p.getExpiresAt());
		if (p.getExpiresAt() == null)
		{
			return new Row(p.getName(), "No expiry", Tag.OWNED, false);
		}
		if (expires == null)
		{
			// Unreadable date. The server only sends unexpired entitlements, so the plugin is usable;
			// say so rather than invent a date or alarm the user over a formatting problem.
			return new Row(p.getName(), "Active", Tag.OWNED, false);
		}

		// Whole calendar days in the user's zone, not 24-hour periods: something ending at 1am
		// tomorrow ends "tomorrow", even though it is less than a day away.
		LocalDate today = now.atZone(zone).toLocalDate();
		LocalDate ends = expires.atZone(zone).toLocalDate();
		long days = ChronoUnit.DAYS.between(today, ends);
		String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(ends);

		if (!expires.isAfter(now))
		{
			// The server filters these out, so seeing one means this clock is ahead of the server's.
			return new Row(p.getName(), "Expired " + date, Tag.OWNED, true);
		}
		if (days <= 0)
		{
			String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
				.format(expires.atZone(zone));
			return new Row(p.getName(), "Expires today at " + time, Tag.OWNED, true);
		}
		if (days == 1)
		{
			return new Row(p.getName(), "Expires tomorrow, " + date, Tag.OWNED, true);
		}
		return new Row(p.getName(), "Expires " + date + " · " + days + " days left", Tag.OWNED,
			days <= SOON_DAYS);
	}

	static Row dev(DevJar jar, Instant now, ZoneId zone, Locale locale)
	{
		String name = jar.fileName;
		String title = name.toLowerCase(Locale.ROOT).endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
		return new Row(title, "Local build · deployed " + age(Instant.ofEpochMilli(jar.lastModified), now,
			zone, locale), Tag.DEV, false);
	}

	/**
	 * How long ago, in the terms useful for checking that a deploy landed: minutes and hours while it
	 * is recent, a date once it is not.
	 */
	static String age(Instant then, Instant now, ZoneId zone, Locale locale)
	{
		Duration ago = Duration.between(then, now);
		if (ago.isNegative() || ago.getSeconds() < 60)
		{
			return "just now";
		}
		if (ago.toMinutes() < 60)
		{
			return ago.toMinutes() + " min ago";
		}
		if (ago.toHours() < 24)
		{
			return ago.toHours() + " hr ago";
		}
		return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(then.atZone(zone));
	}

	/**
	 * The lines above the list, saying what the list alone cannot: why it is empty, that it is still
	 * loading, or what went wrong. Empty when the list speaks for itself.
	 *
	 * <p>"Signed in, nothing fetched, no error" reads as loading. That is the only way to reach that
	 * state -- the page fetches as soon as it is shown -- so it needs no flag of its own.
	 */
	public static List<String> messages(boolean signedIn, String error, List<OwnedPlugin> owned,
		boolean developerAccess, int devCount, File devDir)
	{
		List<String> lines = new ArrayList<>();
		if (!signedIn)
		{
			lines.add("Sign in to see your plugins.");
		}
		else if (error != null)
		{
			lines.add(error);
		}
		else if (owned == null)
		{
			lines.add("Loading your plugins...");
		}
		else if (owned.isEmpty())
		{
			lines.add("No plugins on this account yet.");
		}
		// Named with its path: the first question from a developer with an empty list is where the
		// jars were supposed to go.
		if (developerAccess && devCount == 0)
		{
			lines.add("No developer plugins in " + (devDir == null ? "the dev-plugins folder" : devDir.getPath()) + ".");
		}
		return lines;
	}

	/**
	 * Reads the server's timestamp, or null if it cannot.
	 *
	 * <p>Via {@link OffsetDateTime} rather than {@code Instant.parse}: Postgres writes offsets as
	 * {@code +00:00}, and Java 11's {@code Instant.parse} accepts only a trailing {@code Z}, so the
	 * obvious call rejects every real timestamp. A value with no offset at all is taken as UTC, which
	 * is what the database stores.
	 */
	static Instant parse(String iso)
	{
		if (iso == null || iso.isEmpty())
		{
			return null;
		}
		try
		{
			return OffsetDateTime.parse(iso).toInstant();
		}
		catch (DateTimeParseException ignored)
		{
			// Fall through to the offset-less form.
		}
		try
		{
			return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ignored)
		{
			return null;
		}
	}

	/**
	 * The jars the game client will load from the dev-plugins folder, in the order it loads them.
	 *
	 * <p>Deliberately the same rule as the client's {@code PluginManager.findRiftPluginJars}: top-level
	 * files ending in {@code .jar}, any case, sorted by lower-cased name. Listing anything else here
	 * would show a developer a plugin that never loads.
	 */
	public static List<DevJar> findDevJars(File dir)
	{
		File[] files = dir == null ? null : dir.listFiles();
		if (files == null)
		{
			return Collections.emptyList();
		}
		List<File> jars = new ArrayList<>();
		for (File f : files)
		{
			if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
			{
				jars.add(f);
			}
		}
		jars.sort(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
		List<DevJar> out = new ArrayList<>();
		for (File f : jars)
		{
			out.add(new DevJar(f.getName(), f.lastModified()));
		}
		return out;
	}
}
