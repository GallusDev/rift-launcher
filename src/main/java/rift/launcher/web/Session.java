package rift.launcher.web;

/**
 * A Supabase auth session: the JWT access token used as the API Bearer, the refresh token (a
 * credential — persist only encrypted, never log), and the access token's expiry (epoch seconds).
 */
public final class Session
{
	private final String accessToken;
	private final String refreshToken;
	private final long expiresAt;
	private final String userName;

	public Session(String accessToken, String refreshToken, long expiresAt, String userName)
	{
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.expiresAt = expiresAt;
		this.userName = userName;
	}

	public String getAccessToken()
	{
		return accessToken;
	}

	public String getRefreshToken()
	{
		return refreshToken;
	}

	/** Access-token expiry, epoch seconds (0 if the server didn't provide one). */
	public long getExpiresAt()
	{
		return expiresAt;
	}

	/** Discord display name for this Rift account, or {@code null} if the server didn't provide one. */
	public String getUserName()
	{
		return userName;
	}
}
