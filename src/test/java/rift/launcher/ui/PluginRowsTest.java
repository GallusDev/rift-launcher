package rift.launcher.ui;

import com.google.gson.Gson;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import rift.launcher.web.OwnedPlugin;

public class PluginRowsTest
{
	private static final ZoneId NY = ZoneId.of("America/New_York");
	/** 2026-09-24 12:00 in New York. */
	private static final Instant NOW = Instant.parse("2026-09-24T16:00:00Z");

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Built the way the real response arrives, so the tests exercise the actual field mapping. */
	private static OwnedPlugin plugin(String name, String expiresAt, String source)
	{
		String entitlement = "{\"expires_at\":" + (expiresAt == null ? "null" : "\"" + expiresAt + "\"")
			+ ",\"source\":" + (source == null ? "null" : "\"" + source + "\"") + "}";
		return new Gson().fromJson("{\"slug\":\"" + name.toLowerCase(Locale.ROOT).replace(' ', '-')
			+ "\",\"name\":\"" + name + "\",\"entitlement\":" + entitlement + "}", OwnedPlugin.class);
	}

	private static PluginRows.Row row(OwnedPlugin p)
	{
		return PluginRows.owned(p, NOW, NY, Locale.US);
	}

	@Test
	public void aTrialIsATrialNotAPluginThatNeverExpires()
	{
		// The server sends every trial with expires_at null. Read as a purchase, that is "no expiry"
		// -- telling a trial user their access lasts forever.
		PluginRows.Row r = row(plugin("Rift Combat", null, "trial"));
		assertEquals(PluginRows.Tag.TRIAL, r.getTag());
		assertFalse(r.getDetail().toLowerCase(Locale.ROOT).contains("no expiry"));
	}

	@Test
	public void aPurchaseWithNoEndDateSaysSo()
	{
		PluginRows.Row r = row(plugin("Rift Combat", null, "purchase"));
		assertEquals(PluginRows.Tag.OWNED, r.getTag());
		assertEquals("No expiry", r.getDetail());
		assertFalse(r.needsAttention());
	}

	@Test
	public void postgresOffsetTimestampsParse()
	{
		// Java 11's Instant.parse rejects "+00:00" and accepts only "Z"; Postgres writes "+00:00".
		// Getting this wrong turns every real expiry date into "Active".
		assertEquals(Instant.parse("2026-10-24T12:00:00Z"), PluginRows.parse("2026-10-24T12:00:00+00:00"));
		assertEquals(Instant.parse("2026-10-24T12:00:00.123456Z"),
			PluginRows.parse("2026-10-24T12:00:00.123456+00:00"));
		assertEquals(Instant.parse("2026-10-24T12:00:00Z"), PluginRows.parse("2026-10-24T12:00:00Z"));
		assertEquals("no offset is taken as UTC, which is what the database stores",
			Instant.parse("2026-10-24T12:00:00Z"), PluginRows.parse("2026-10-24T12:00:00"));
		assertNull(PluginRows.parse("next tuesday"));
		assertNull(PluginRows.parse(""));
	}

	@Test
	public void aDistantExpiryShowsTheDateAndDaysLeftWithoutAlarm()
	{
		PluginRows.Row r = row(plugin("Rift Combat", "2026-10-24T16:00:00+00:00", "purchase"));
		assertEquals("Expires Oct 24, 2026 \u00b7 30 days left", r.getDetail());
		assertFalse(r.needsAttention());
	}

	@Test
	public void expiryWithinAWeekIsFlagged()
	{
		assertTrue(row(plugin("A", "2026-10-01T16:00:00+00:00", "purchase")).needsAttention());   // 7 days
		assertFalse(row(plugin("A", "2026-10-02T16:00:00+00:00", "purchase")).needsAttention());  // 8 days
	}

	@Test
	public void tomorrowMeansTheNextCalendarDayNotTwentyFourHours()
	{
		// 01:00 tomorrow in New York is only 13 hours away, but it is tomorrow.
		PluginRows.Row r = row(plugin("A", "2026-09-25T05:00:00+00:00", "purchase"));
		assertEquals("Expires tomorrow, Sep 25, 2026", r.getDetail());
		assertTrue(r.needsAttention());
	}

	@Test
	public void laterTodayGivesTheTime()
	{
		PluginRows.Row r = row(plugin("A", "2026-09-24T22:00:00+00:00", "purchase"));   // 18:00 NY
		assertTrue(r.getDetail(), r.getDetail().startsWith("Expires today at "));
		assertTrue(r.getDetail(), r.getDetail().contains("6:00"));
		assertTrue(r.needsAttention());
	}

	@Test
	public void anExpiryAlreadyPastIsFlaggedNotHidden()
	{
		// The server filters these, so one showing up means this clock runs ahead of the server's.
		PluginRows.Row r = row(plugin("A", "2026-09-20T12:00:00+00:00", "purchase"));
		assertEquals("Expired Sep 20, 2026", r.getDetail());
		assertTrue(r.needsAttention());
	}

	@Test
	public void anUnreadableDateStillShowsThePluginAsUsable()
	{
		PluginRows.Row r = row(plugin("A", "garbage", "purchase"));
		assertEquals("Active", r.getDetail());
		assertFalse(r.needsAttention());
	}

	@Test
	public void ownedPluginsComeFirstAlphabeticallyThenDevPluginsInLoadOrder()
	{
		List<OwnedPlugin> owned = Arrays.asList(plugin("rift yama", null, "purchase"),
			plugin("Rift Combat", null, "purchase"));
		List<PluginRows.DevJar> dev = Arrays.asList(new PluginRows.DevJar("a-first.jar", NOW.toEpochMilli()),
			new PluginRows.DevJar("b-second.jar", NOW.toEpochMilli()));

		List<PluginRows.Row> rows = PluginRows.build(owned, dev, NOW, NY, Locale.US);

		assertEquals(4, rows.size());
		assertEquals("Rift Combat", rows.get(0).getTitle());
		assertEquals("rift yama", rows.get(1).getTitle());
		assertEquals("a-first", rows.get(2).getTitle());
		assertEquals(PluginRows.Tag.DEV, rows.get(2).getTag());
		assertEquals("b-second", rows.get(3).getTitle());
	}

	@Test
	public void aDevPluginIsNamedByItsJarAndDatedByItsDeploy()
	{
		PluginRows.Row r = PluginRows.dev(new PluginRows.DevJar("Rift-Forager.JAR",
			NOW.minusSeconds(5 * 60).toEpochMilli()), NOW, NY, Locale.US);
		assertEquals("Rift-Forager", r.getTitle());
		assertEquals("Local build \u00b7 deployed 5 min ago", r.getDetail());
	}

	@Test
	public void deployAgeReadsInUsefulUnits()
	{
		assertEquals("just now", PluginRows.age(NOW.minusSeconds(20), NOW, NY, Locale.US));
		assertEquals("a clock skew into the future is not negative time", "just now",
			PluginRows.age(NOW.plusSeconds(90), NOW, NY, Locale.US));
		assertEquals("59 min ago", PluginRows.age(NOW.minusSeconds(59 * 60), NOW, NY, Locale.US));
		assertEquals("3 hr ago", PluginRows.age(NOW.minusSeconds(3 * 3600), NOW, NY, Locale.US));
		assertEquals("Sep 20, 2026", PluginRows.age(Instant.parse("2026-09-20T16:00:00Z"), NOW, NY, Locale.US));
	}

	@Test
	public void signedOutShowsOnlyTheSignInPrompt()
	{
		assertEquals(Collections.singletonList("Sign in to see your plugins."),
			PluginRows.messages(false, null, null, false, 0, null));
	}

	@Test
	public void offlineDevUnlockWhileSignedOutStillPointsAtTheDevFolder()
	{
		File dir = new File("C:/dev-plugins");
		List<String> lines = PluginRows.messages(false, null, null, true, 0, dir);
		assertEquals(2, lines.size());
		assertEquals("No developer plugins in " + dir.getPath() + ".", lines.get(1));
		assertEquals("with jars present the list speaks for itself",
			1, PluginRows.messages(false, null, null, true, 3, dir).size());
	}

	@Test
	public void signedInMessagesTrackTheFetch()
	{
		assertEquals("Loading your plugins...", PluginRows.messages(true, null, null, false, 0, null).get(0));
		assertEquals("boom", PluginRows.messages(true, "boom", null, false, 0, null).get(0));
		assertEquals("No plugins on this account yet.",
			PluginRows.messages(true, null, Collections.emptyList(), false, 0, null).get(0));
		assertTrue(PluginRows.messages(true, null,
			Collections.singletonList(plugin("A", null, "purchase")), false, 0, null).isEmpty());
	}

	@Test
	public void devJarsMatchWhatTheClientLoads() throws Exception
	{
		File dir = tmp.newFolder("dev-plugins");
		Files.write(new File(dir, "zeta.jar").toPath(), new byte[0]);
		Files.write(new File(dir, "Alpha.JAR").toPath(), new byte[0]);
		Files.write(new File(dir, "notes.txt").toPath(), new byte[0]);
		// A folder named like a jar is not a jar; the client skips it, so must the list.
		assertTrue(new File(dir, "folder.jar").mkdir());

		List<PluginRows.DevJar> jars = PluginRows.findDevJars(dir);

		List<PluginRows.Row> rows = PluginRows.build(null, jars, NOW, NY, Locale.US);
		assertEquals(2, rows.size());
		assertEquals("sorted by lower-cased name, as the client loads them", "Alpha", rows.get(0).getTitle());
		assertEquals("zeta", rows.get(1).getTitle());
	}

	@Test
	public void aMissingDevFolderIsEmptyNotAnError()
	{
		assertTrue(PluginRows.findDevJars(new File(tmp.getRoot(), "does-not-exist")).isEmpty());
		assertTrue(PluginRows.findDevJars(null).isEmpty());
	}
}
