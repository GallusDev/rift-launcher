package rift.launcher.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds an {@link Account} from the JX_* environment the Jagex Launcher passes to a launched client.
 * Only the session id + character id are required (the token vars are empty in that environment).
 */
public final class AccountImporter
{
	private AccountImporter()
	{
	}

	public static Account fromEnvironment(Map<String, String> env, long now)
	{
		String characterId = require(env, "JX_CHARACTER_ID");
		String sessionId = require(env, "JX_SESSION_ID");
		String displayName = env.get("JX_DISPLAY_NAME");
		if (displayName == null || displayName.isEmpty())
		{
			displayName = characterId;
		}
		// No proxy on import: an account starts connecting directly until one is assigned. Re-importing
		// an existing account must not silently clear an assignment, so callers merge rather than replace.
		return new Account(characterId, displayName, sessionId, now, null);
	}

	/**
	 * The stored accounts with {@code imported} merged in: it takes the place of the stored entry for
	 * the same character, keeping that entry's proxy assignment, or is appended if the character is new.
	 *
	 * <p>Everything else comes from the import -- session, display name and timestamp are exactly what
	 * just changed. The proxy is the one field an import cannot know and the one the user set on
	 * purpose. The caller used to replace the entry wholesale, which cleared it, and because the Jagex
	 * Launcher re-imports on every start, an assignment did not survive a day.
	 *
	 * <p>Every stored copy of the character is collapsed into the one merged entry, as the old
	 * replace-all did, so a list that ever picked up a duplicate heals rather than keeps it. The first
	 * copy's proxy and position win.
	 */
	public static List<Account> merge(List<Account> accounts, Account imported)
	{
		List<Account> merged = new ArrayList<>();
		int at = -1;
		String proxyId = null;
		for (Account a : accounts)
		{
			if (a.getCharacterId().equals(imported.getCharacterId()))
			{
				if (at < 0)
				{
					at = merged.size();
					proxyId = a.getProxyId();
				}
				continue;
			}
			merged.add(a);
		}
		if (at < 0)
		{
			merged.add(imported);
			return merged;
		}
		merged.add(at, new Account(imported.getCharacterId(), imported.getDisplayName(),
			imported.getSessionId(), imported.getAddedAt(), proxyId));
		return merged;
	}

	private static String require(Map<String, String> env, String key)
	{
		String value = env.get(key);
		if (value == null || value.isEmpty())
		{
			throw new IllegalStateException("Missing required Jagex environment variable: " + key);
		}
		return value;
	}
}
