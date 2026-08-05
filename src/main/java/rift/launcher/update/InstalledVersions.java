package rift.launcher.update;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * What is currently installed, recorded at {@code ~/.rift/versions.json}.
 *
 * <p>Needed because neither artefact can be asked its own Rift version: the client jar reports
 * RuneLite's version (1.12.35), not Rift's, and the launcher jar has no version resource at runtime.
 * Rather than inferring, the updater writes down what it installed.
 *
 * <p>An absent or unreadable file means "unknown", which the updater treats as "assume current" — a
 * missing record must never trigger a download loop on every start.
 */
public final class InstalledVersions
{
	private static final Gson GSON = new Gson();

	@SerializedName("client")
	private String client;

	@SerializedName("launcher")
	private String launcher;

	/**
	 * The sha256 of the client jar we installed. A stronger identity than the version string: it also
	 * catches a rebuild republished under the same version, where the bytes change but the label does
	 * not. Absent for installs that predate this field, which fall back to comparing versions.
	 */
	@SerializedName("client_sha256")
	private String clientSha256;

	public static InstalledVersions load(File file)
	{
		if (!file.isFile())
		{
			return new InstalledVersions();
		}
		try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			InstalledVersions v = GSON.fromJson(r, InstalledVersions.class);
			return v == null ? new InstalledVersions() : v;
		}
		catch (Exception ex)
		{
			return new InstalledVersions();
		}
	}

	public void save(File file) throws IOException
	{
		File parent = file.getParentFile();
		if (parent != null)
		{
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))
		{
			GSON.toJson(this, w);
		}
	}

	public String getClientVersion()
	{
		return client;
	}

	public void setClientVersion(String client)
	{
		this.client = client;
	}

	public String getClientSha256()
	{
		return clientSha256;
	}

	public void setClientSha256(String clientSha256)
	{
		this.clientSha256 = clientSha256;
	}

	public String getLauncherVersion()
	{
		return launcher;
	}

	public void setLauncherVersion(String launcher)
	{
		this.launcher = launcher;
	}

	/**
	 * Whether {@code available} is something other than what is installed.
	 *
	 * <p>Deliberately an inequality rather than a "greater than": release versions here are opaque
	 * strings the operator sets, so ordering them would mean guessing a scheme. Any change to what the
	 * server advertises is an update, which also makes a deliberate rollback work.
	 */
	public static boolean isUpdate(String installed, String available)
	{
		if (available == null || available.isEmpty())
		{
			return false;
		}
		// Unknown installed version: record it rather than re-downloading on every start.
		return installed != null && !installed.equals(available);
	}
}
