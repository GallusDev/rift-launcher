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

	/**
	 * SOCKS5 proxy for this launch, or null to connect directly.
	 *
	 * <p>Credentials travel here rather than as {@code -Djava.net.socksPassword} on the command line,
	 * where any process listing would show them -- the same reason Jagex credentials never go there.
	 */
	@SerializedName("proxy")
	private final ProxyConfig proxy;

	/** Just enough to configure SOCKS5 on the client side. */
	public static final class ProxyConfig
	{
		@SerializedName("host")
		private final String host;

		@SerializedName("port")
		private final int port;

		@SerializedName("username")
		private final String username;

		@SerializedName("password")
		private final String password;

		public ProxyConfig(String host, int port, String username, String password)
		{
			this.host = host;
			this.port = port;
			this.username = username;
			this.password = password;
		}
	}

	public LaunchHandoff(String accessToken, String refreshToken, long expiresAt, String baseUrl,
		String anonKey, String supabaseUrl)
	{
		this(accessToken, refreshToken, expiresAt, baseUrl, anonKey, supabaseUrl, false, null);
	}

	public LaunchHandoff(String accessToken, String refreshToken, long expiresAt, String baseUrl,
		String anonKey, String supabaseUrl, boolean developerMode)
	{
		this(accessToken, refreshToken, expiresAt, baseUrl, anonKey, supabaseUrl, developerMode, null);
	}

	public LaunchHandoff(String accessToken, String refreshToken, long expiresAt, String baseUrl,
		String anonKey, String supabaseUrl, boolean developerMode, ProxyConfig proxy)
	{
		this.proxy = proxy;
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
