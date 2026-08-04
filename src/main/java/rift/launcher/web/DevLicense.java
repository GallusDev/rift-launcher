package rift.launcher.web;

import com.google.gson.annotations.SerializedName;

/**
 * The result of {@code POST /api/v1/dev/verify}. The website answers {@code 200 {valid:true,...}} for a
 * live key and {@code 401 {valid:false}} for anything else (unknown, revoked, or a suspended developer)
 * without saying which — so this type only ever carries "valid, and if so who/what tier".
 *
 * <p>Note this never holds the key itself: the key is a credential, kept in {@link DevLicenseStore}
 * (encrypted at rest) and never copied into launch state or logs.
 */
public final class DevLicense
{
	@SerializedName("valid")
	private boolean valid;

	@SerializedName("developer_id")
	private String developerId;

	@SerializedName("tier")
	private String tier;

	/** The fixed "not a developer" answer, used for 401s and for any unreadable body. */
	public static DevLicense invalid()
	{
		return new DevLicense();
	}

	public boolean isValid()
	{
		return valid;
	}

	public String getDeveloperId()
	{
		return developerId;
	}

	public String getTier()
	{
		return tier;
	}
}
