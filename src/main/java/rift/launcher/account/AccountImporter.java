package rift.launcher.account;

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
		return new Account(characterId, displayName, sessionId, now);
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
