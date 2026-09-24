package rift.launcher.web;

import com.google.gson.annotations.SerializedName;

/**
 * One plugin this account can use, from {@code GET /api/v1/me/plugins}.
 *
 * <p>Only what the Plugins page shows: which plugin, and on what terms. The same endpoint also
 * carries artifact and version-id details, which are the game client's business, not the launcher's.
 *
 * <p>The terms live under {@code entitlement}, which the game client never reads -- so its absence
 * from the client's own model is not evidence the server doesn't send it.
 */
public final class OwnedPlugin
{
	private String slug;
	private String name;
	private String version;
	private Entitlement entitlement;

	private static final class Entitlement
	{
		@SerializedName("expires_at")
		private String expiresAt;

		private String source;
	}

	public String getSlug()
	{
		return slug;
	}

	/** The display name, falling back to the slug so a row is never blank. */
	public String getName()
	{
		return name == null || name.isEmpty() ? slug : name;
	}

	public String getVersion()
	{
		return version;
	}

	/**
	 * When access ends, as the server's ISO-8601 timestamp, or null.
	 *
	 * <p>Null means different things depending on {@link #isTrial()}: for a purchase it is access with
	 * no end date, but a trial is <em>always</em> null here, because trials are metered in play time
	 * rather than ending on a date. Never read null as "forever" without checking which it is.
	 */
	public String getExpiresAt()
	{
		return entitlement == null ? null : entitlement.expiresAt;
	}

	/** How access was granted, e.g. {@code trial}; null if the server did not say. */
	public String getSource()
	{
		return entitlement == null ? null : entitlement.source;
	}

	public boolean isTrial()
	{
		return "trial".equals(getSource());
	}
}
