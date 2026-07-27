package rift.launcher.web;

/**
 * Local concurrent-session policy. The server reports the limit ({@code max_sessions} from
 * {@code license/check}); there's no server session registry yet, so the launcher enforces it locally
 * by counting the client processes it has running.
 */
public final class SessionEnforcer
{
	private SessionEnforcer()
	{
	}

	/**
	 * @param maxSessions      the account's limit; {@code null} = unlimited (VIP/VIP+).
	 * @param currentlyRunning client processes this launcher currently has alive.
	 * @return whether another client may be launched.
	 */
	public static boolean canLaunch(Integer maxSessions, int currentlyRunning)
	{
		return maxSessions == null || currentlyRunning < maxSessions;
	}
}
