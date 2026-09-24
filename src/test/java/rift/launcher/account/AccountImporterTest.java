package rift.launcher.account;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class AccountImporterTest
{
	private Map<String, String> fullEnv()
	{
		Map<String, String> env = new HashMap<>();
		env.put("JX_CHARACTER_ID", "char-1");
		env.put("JX_DISPLAY_NAME", "Zezima");
		env.put("JX_SESSION_ID", "sess-1");
		return env;
	}

	@Test
	public void mapsEnvToAccount()
	{
		Account a = AccountImporter.fromEnvironment(fullEnv(), 999L);
		assertEquals("char-1", a.getCharacterId());
		assertEquals("Zezima", a.getDisplayName());
		assertEquals("sess-1", a.getSessionId());
		assertEquals(999L, a.getAddedAt());
	}

	@Test(expected = IllegalStateException.class)
	public void missingSessionIdThrows()
	{
		Map<String, String> env = fullEnv();
		env.remove("JX_SESSION_ID");
		AccountImporter.fromEnvironment(env, 1L);
	}

	@Test(expected = IllegalStateException.class)
	public void missingCharacterIdThrows()
	{
		Map<String, String> env = fullEnv();
		env.remove("JX_CHARACTER_ID");
		AccountImporter.fromEnvironment(env, 1L);
	}

	@Test
	public void missingDisplayNameDefaultsToCharacterId()
	{
		Map<String, String> env = fullEnv();
		env.remove("JX_DISPLAY_NAME");
		Account a = AccountImporter.fromEnvironment(env, 1L);
		assertEquals("char-1", a.getDisplayName());
	}

	@Test
	public void reimportingAnAccountKeepsItsProxyAndItsPlace()
	{
		// The Jagex Launcher re-imports on every start. Replacing the entry wholesale cleared the proxy
		// assignment each time, so a proxied account quietly went direct the next day.
		List<Account> stored = Arrays.asList(
			new Account("111", "Gallus", "old-session", 1L, "proxy-1"),
			new Account("222", "Alt", "s2", 2L, null));
		Account imported = new Account("111", "Gallus Renamed", "new-session", 99L, null);

		List<Account> merged = AccountImporter.merge(stored, imported);

		assertEquals(2, merged.size());
		Account first = merged.get(0);
		assertEquals("kept its place in the list", "111", first.getCharacterId());
		assertEquals("proxy-1", first.getProxyId());
		assertEquals("new-session", first.getSessionId());
		assertEquals("Gallus Renamed", first.getDisplayName());
		assertEquals(99L, first.getAddedAt());
		assertEquals(stored.get(1), merged.get(1));
	}

	@Test
	public void importingANewCharacterAppendsIt()
	{
		List<Account> stored = Collections.singletonList(new Account("111", "Gallus", "s", 1L, "proxy-1"));
		Account imported = new Account("333", "New", "s3", 5L, null);

		List<Account> merged = AccountImporter.merge(stored, imported);

		assertEquals(Arrays.asList(stored.get(0), imported), merged);
	}

	@Test
	public void storedDuplicatesCollapseIntoOneEntryKeepingTheFirstProxy()
	{
		List<Account> stored = Arrays.asList(
			new Account("111", "Gallus", "a", 1L, "proxy-1"),
			new Account("222", "Alt", "b", 2L, null),
			new Account("111", "Gallus", "c", 3L, "proxy-2"));

		List<Account> merged = AccountImporter.merge(stored, new Account("111", "Gallus", "d", 4L, null));

		assertEquals(2, merged.size());
		assertEquals("proxy-1", merged.get(0).getProxyId());
		assertEquals("222", merged.get(1).getCharacterId());
	}
}
