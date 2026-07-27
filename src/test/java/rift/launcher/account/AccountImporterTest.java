package rift.launcher.account;

import java.util.HashMap;
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
}
