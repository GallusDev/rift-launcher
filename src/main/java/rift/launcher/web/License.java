package rift.launcher.web;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of {@code POST /api/v1/license/check}: the account's session/entitlement gate.
 * <p>
 * {@code maxSessions} is {@code null} for VIP/VIP+ (unlimited) and {@code 1} for free users.
 */
public final class License
{
	private boolean vip;

	@SerializedName("max_sessions")
	private Integer maxSessions;

	private boolean blocked;
	private String tier;

	@SerializedName("discount_bps")
	private int discountBps;

	private List<Entitlement> entitlements;

	public boolean isVip()
	{
		return vip;
	}

	/** {@code null} = unlimited concurrent sessions (VIP/VIP+). */
	public Integer getMaxSessions()
	{
		return maxSessions;
	}

	public boolean isUnlimitedSessions()
	{
		return maxSessions == null;
	}

	public boolean isBlocked()
	{
		return blocked;
	}

	public String getTier()
	{
		return tier;
	}

	public int getDiscountBps()
	{
		return discountBps;
	}

	public List<Entitlement> getEntitlements()
	{
		return entitlements == null ? new ArrayList<>() : entitlements;
	}

	/** A live (non-expired) license for one plugin. */
	public static final class Entitlement
	{
		@SerializedName("plugin_slug")
		private String pluginSlug;

		@SerializedName("expires_at")
		private String expiresAt;

		public String getPluginSlug()
		{
			return pluginSlug;
		}

		/** ISO-8601 expiry, or null for a perpetual entitlement. */
		public String getExpiresAt()
		{
			return expiresAt;
		}
	}
}
