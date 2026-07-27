package rift.launcher.account;

import lombok.Value;

/**
 * One launchable game character, identified by its Jagex character id. Stores the captured Jagex
 * game-session id, which is replayed to launch the client (valid until the Jagex session expires).
 */
@Value
public class Account
{
	String characterId;   // JX_CHARACTER_ID
	String displayName;   // JX_DISPLAY_NAME
	String sessionId;     // JX_SESSION_ID (captured Jagex game session)
	long addedAt;         // epoch millis
}
