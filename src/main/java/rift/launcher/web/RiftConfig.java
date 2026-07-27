package rift.launcher.web;

/**
 * Static config for the Rift website integration. The Supabase URL + anon key are public/embeddable
 * (the anon key is a client credential by design). The API base URL is overridable via the
 * {@code rift.api.base} system property or {@code RIFT_API_BASE} env var, since it changes on deploy.
 */
public final class RiftConfig
{
	public static final String SUPABASE_URL = "https://mqneomdiddzkxokampcj.supabase.co";

	/** Public/embeddable Supabase anon key (safe in the client per the website contract). */
	public static final String SUPABASE_ANON_KEY =
		"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1xbmVvbWRpZGR6a3hva2FtcGNq"
			+ "Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQ5NTYzMzEsImV4cCI6MjEwMDUzMjMzMX0"
			+ ".JN7zyVFyma2QfA_LiIf4NBtWTAIm7eY0HUU0blgFmr8";

	private static final String DEFAULT_BASE_URL = "http://localhost:3000";

	private RiftConfig()
	{
	}

	/** The Rift API base URL — {@code rift.api.base} / {@code RIFT_API_BASE} override, else local dev. */
	public static String apiBaseUrl()
	{
		String override = System.getProperty("rift.api.base");
		if (override == null || override.isEmpty())
		{
			override = System.getenv("RIFT_API_BASE");
		}
		return override == null || override.isEmpty() ? DEFAULT_BASE_URL : override;
	}

	/** The pinned loopback port the operator allow-lists in Supabase redirect URLs. */
	public static int authPort()
	{
		return LoopbackRedirectServer.DEFAULT_PORT;
	}
}
