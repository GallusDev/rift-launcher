package rift.launcher.update;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import rift.launcher.web.Release;
import rift.launcher.web.RiftApiClient;

/**
 * Keeps the installed client and launcher current against the website's release channels.
 *
 * <p>The two halves are deliberately asymmetric, because a process cannot replace a file it is running
 * from:
 * <ul>
 *   <li><b>Client</b> — updated in place. It is not running when the launcher checks, so the jar can be
 *       downloaded, verified and swapped silently.</li>
 *   <li><b>Launcher</b> — cannot overwrite itself while running (Windows holds the jar open), so its
 *       update is downloaded and the user is offered the installer. The installer then replaces
 *       everything properly, reusing machinery that already exists rather than inventing a
 *       self-replacing dance.</li>
 * </ul>
 *
 * <p>Everything here fails soft: an update problem must never stop someone playing. A failed check,
 * download or verification leaves the working install exactly as it was.
 */
@Slf4j
public class UpdateService
{
	public static final String CHANNEL_CLIENT = "client";
	public static final String CHANNEL_LAUNCHER = "launcher";
	public static final String PLATFORM_WINDOWS = "windows";

	private final RiftApiClient api;
	private final File riftDir;
	private final String runningLauncherVersion;

	public UpdateService(RiftApiClient api, File riftDir, String runningLauncherVersion)
	{
		this.api = api;
		this.riftDir = riftDir;
		this.runningLauncherVersion = runningLauncherVersion;
	}

	/** What a check found. {@code message} is safe to show in the UI as-is. */
	public static final class Result
	{
		private final boolean clientUpdated;
		private final Release launcherUpdate;
		private final String message;

		Result(boolean clientUpdated, Release launcherUpdate, String message)
		{
			this.clientUpdated = clientUpdated;
			this.launcherUpdate = launcherUpdate;
			this.message = message;
		}

		public boolean isClientUpdated()
		{
			return clientUpdated;
		}

		/** Non-null when a newer launcher exists and the user should be offered the installer. */
		public Release getLauncherUpdate()
		{
			return launcherUpdate;
		}

		public String getMessage()
		{
			return message;
		}
	}

	/**
	 * Checks both channels, applying the client update immediately and reporting a launcher update for
	 * the caller to offer. Never throws: the returned message explains whatever happened.
	 */
	public Result check()
	{
		InstalledVersions versions = InstalledVersions.load(versionsFile());
		StringBuilder message = new StringBuilder();
		boolean clientUpdated = false;

		try
		{
			clientUpdated = updateClient(versions, message);
		}
		catch (Exception ex)
		{
			log.warn("Rift: client update check failed ({})", ex.getClass().getSimpleName());
			message.append("Client update check failed. ");
		}

		Release launcherUpdate = null;
		try
		{
			launcherUpdate = findLauncherUpdate(versions);
			if (launcherUpdate != null)
			{
				message.append("Launcher ").append(launcherUpdate.getVersion()).append(" available. ");
			}
		}
		catch (Exception ex)
		{
			log.warn("Rift: launcher update check failed ({})", ex.getClass().getSimpleName());
		}

		if (message.length() == 0)
		{
			message.append("Up to date.");
		}
		return new Result(clientUpdated, launcherUpdate, message.toString().trim());
	}

	/** Downloads and swaps the client jar when the server advertises a different version. */
	private boolean updateClient(InstalledVersions versions, StringBuilder message) throws Exception
	{
		Release.Latest latest = api.latestRelease(CHANNEL_CLIENT, PLATFORM_WINDOWS);
		if (latest == null)
		{
			return false;
		}
		Release release = latest.forPlatform(PLATFORM_WINDOWS);
		if (release == null)
		{
			return false;
		}

		// First run after an install: adopt the advertised version instead of re-downloading what the
		// installer just placed.
		if (versions.getClientVersion() == null)
		{
			versions.setClientVersion(latest.getVersion());
			versions.save(versionsFile());
			return false;
		}
		if (!InstalledVersions.isUpdate(versions.getClientVersion(), latest.getVersion()))
		{
			return false;
		}

		byte[] bytes = api.downloadRelease(release.getId());
		if (!verify(bytes, release, "client"))
		{
			message.append("Client update rejected (integrity check failed). ");
			return false;
		}

		replaceAtomically(new File(riftDir, "rift-client.jar"), bytes);
		versions.setClientVersion(latest.getVersion());
		versions.save(versionsFile());
		log.info("Rift: client updated to {}", latest.getVersion());
		message.append("Client updated to ").append(latest.getVersion()).append(". ");
		return true;
	}

	private Release findLauncherUpdate(InstalledVersions versions) throws Exception
	{
		Release.Latest latest = api.latestRelease(CHANNEL_LAUNCHER, PLATFORM_WINDOWS);
		if (latest == null)
		{
			return null;
		}
		// Trust the running build's own version over the recorded one: the recorded value can be stale
		// if someone installed a new launcher without going through here.
		String installed = runningLauncherVersion != null
			? runningLauncherVersion : versions.getLauncherVersion();
		return InstalledVersions.isUpdate(installed, latest.getVersion())
			? latest.forPlatform(PLATFORM_WINDOWS) : null;
	}

	/**
	 * Downloads the launcher installer to a temp file and returns it, for the caller to run. Returns
	 * {@code null} if the download or its verification fails.
	 */
	public File downloadLauncherInstaller(Release release)
	{
		try
		{
			byte[] bytes = api.downloadRelease(release.getId());
			if (!verify(bytes, release, "launcher"))
			{
				return null;
			}
			File out = new File(System.getProperty("java.io.tmpdir"),
				"RiftSetup-" + release.getVersion() + ".exe");
			Files.write(out.toPath(), bytes);
			return out;
		}
		catch (Exception ex)
		{
			log.warn("Rift: launcher installer download failed ({})", ex.getClass().getSimpleName());
			return null;
		}
	}

	/**
	 * Verifies a download against the release's {@code sha256}.
	 *
	 * <p>The server does not publish checksums yet, so an absent hash cannot be treated as a failure
	 * without disabling updates entirely. It is logged loudly instead: transport is HTTPS, but that
	 * only protects the wire, not a corrupted or swapped storage object. Once the server publishes
	 * {@code sha256} this becomes a hard gate.
	 */
	private boolean verify(byte[] bytes, Release release, String what)
	{
		if (bytes == null || bytes.length == 0)
		{
			log.warn("Rift: {} download was empty", what);
			return false;
		}
		Long expectedSize = release.getSizeBytes();
		if (expectedSize != null && expectedSize != bytes.length)
		{
			log.warn("Rift: {} download size mismatch (expected {}, got {})",
				what, expectedSize, bytes.length);
			return false;
		}
		String expected = release.getSha256();
		if (expected == null || expected.isEmpty())
		{
			log.warn("Rift: {} release {} publishes no sha256 - installing an unverified download",
				what, release.getVersion());
			return true;
		}
		return expected.equalsIgnoreCase(sha256Hex(bytes));
	}

	static String sha256Hex(byte[] bytes)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder sb = new StringBuilder(digest.length * 2);
			for (byte b : digest)
			{
				sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	/**
	 * Writes beside the target then moves into place, so a failure mid-write cannot leave a truncated
	 * jar that the launcher would then try to run.
	 */
	static void replaceAtomically(File target, byte[] bytes) throws IOException
	{
		File tmp = new File(target.getParentFile(), target.getName() + ".new");
		Files.write(tmp.toPath(), bytes);
		try
		{
			Files.move(tmp.toPath(), target.toPath(),
				StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (Exception ex)
		{
			// Some filesystems cannot move atomically; a plain replace is still better than a partial write.
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private File versionsFile()
	{
		return new File(riftDir, "versions.json");
	}
}
