package rift.launcher.web;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * The one-line JSON payload the launcher writes to the client's stdin at spawn. Carries the Supabase
 * session (so the client can call {@code /me/plugins} + {@code /artifact} and self-refresh) and the API
 * config. Passed over stdin — never env vars (leak via process listing) or disk.
 */
public final class LaunchHandoff
{
	private static final Gson GSON = new Gson();

	@SerializedName("access_token")
	private final String accessToken;

	@SerializedName("refresh_token")
	private final String refreshToken;

	@SerializedName("expires_at")
	private final long expiresAt;

	@SerializedName("base_url")
	private final String baseUrl;

	@SerializedName("anon_key")
	private final String anonKey;

	@SerializedName("supabase_url")
	private final String supabaseUrl;

	/**
	 * Whether the client may load local developer plugins. Set only after the launcher has verified a
	 * developer license key against {@code /api/v1/dev/verify} for this launch. Deliberately a plain
	 * boolean: the key itself never enters the client process, so it can't leak from there.
	 */
	@SerializedName("developer_mode")
	private final boolean developerMode;

	public LaunchHandoff(String accessToken, String refreshToken, long expiresAt, String baseUrl,
		String anonKey, String supabaseUrl)
	{
		this(accessToken, refreshToken, expiresAt, baseUrl, anonKey, supabaseUrl, false);
	}

	public LaunchHandoff(String accessToken, String refreshToken, long expiresAt, String baseUrl,
		String anonKey, String supabaseUrl, boolean developerMode)
	{
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.expiresAt = expiresAt;
		this.baseUrl = baseUrl;
		this.anonKey = anonKey;
		this.supabaseUrl = supabaseUrl;
		this.developerMode = developerMode;
	}

	/** A single-line JSON string suitable for writing to the client's stdin. */
	public String toJson()
	{
		return GSON.toJson(this);
	}
}
