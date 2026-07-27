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

	public LaunchHandoff(String accessToken, String refreshToken, long expiresAt, String baseUrl,
		String anonKey, String supabaseUrl)
	{
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.expiresAt = expiresAt;
		this.baseUrl = baseUrl;
		this.anonKey = anonKey;
		this.supabaseUrl = supabaseUrl;
	}

	/** A single-line JSON string suitable for writing to the client's stdin. */
	public String toJson()
	{
		return GSON.toJson(this);
	}
}
