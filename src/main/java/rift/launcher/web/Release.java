package rift.launcher.web;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * A published build, from {@code GET /api/v1/releases/latest}. The response carries the newest
 * {@code version} plus every platform row for it.
 *
 * <p>{@code sha256} and {@code sizeBytes} are optional because the server does not publish them yet
 * (see {@code rift-auto-update-for-website.md}). Until it does, a downloaded file cannot be verified
 * before it is put in place, so the updater treats an unverifiable download as a reason to warn.
 */
public final class Release
{
	@SerializedName("id")
	private String id;

	@SerializedName("channel")
	private String channel;

	@SerializedName("version")
	private String version;

	@SerializedName("platform")
	private String platform;

	@SerializedName("notes_md")
	private String notesMd;

	@SerializedName("sha256")
	private String sha256;

	@SerializedName("size_bytes")
	private Long sizeBytes;

	/** Test seam: production instances always come from Gson, which sets the fields directly. */
	static Release forTest(String id, String version, String sha256, Long sizeBytes)
	{
		Release r = new Release();
		r.id = id;
		r.version = version;
		r.sha256 = sha256;
		r.sizeBytes = sizeBytes;
		return r;
	}

	public String getId()
	{
		return id;
	}

	public String getChannel()
	{
		return channel;
	}

	public String getVersion()
	{
		return version;
	}

	public String getPlatform()
	{
		return platform;
	}

	public String getNotesMd()
	{
		return notesMd;
	}

	/** Hex sha256 of the file, or {@code null} while the server does not publish one. */
	public String getSha256()
	{
		return sha256;
	}

	public Long getSizeBytes()
	{
		return sizeBytes;
	}

	/** The {@code {version, releases[]}} envelope the endpoint returns. */
	public static final class Latest
	{
		@SerializedName("version")
		private String version;

		@SerializedName("releases")
		private List<Release> releases;

		public String getVersion()
		{
			return version;
		}

		public List<Release> getReleases()
		{
			return releases == null ? new ArrayList<>() : releases;
		}

		/** The row for {@code platform}, or the first one when the server did not split by platform. */
		public Release forPlatform(String platform)
		{
			for (Release r : getReleases())
			{
				if (platform.equals(r.getPlatform()))
				{
					return r;
				}
			}
			return getReleases().isEmpty() ? null : getReleases().get(0);
		}
	}
}
