package rift.launcher.jagex;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import rift.shim.ShimConfig;

/**
 * Owns Rift's relationship with an installed RuneLite: it redirects RuneLite's {@code config.json} at
 * the Rift shim so the Jagex Launcher boots us, and can report on, repair, or undo that.
 *
 * <p>This is the only code that writes inside RuneLite's directory. It never touches an executable —
 * only the JSON config that RuneLite's own (still signed) native launcher reads — and it always keeps a
 * pristine backup so the change can be undone exactly.
 */
@Slf4j
public class JagexIntegration
{
	public static final String SHIM_MAIN_CLASS = "rift.shim.ShimMain";
	private static final String BACKUP_NAME = "config.json.rift-backup";

	public enum Status
	{
		/** No RuneLite install found — nothing to integrate with. */
		NOT_INSTALLED,
		/** config.json points at the Rift shim. */
		ACTIVE,
		/** RuneLite is installed but the redirect is gone (an update rewrote the config). */
		REVERTED
	}

	/** Where Vortex installs by default. Only used to decide whether to offer it in the chooser. */
	private static final File DEFAULT_VORTEX_DIR = new File("C:/Program Files/Vortex");

	private final File runeLiteDir;
	private final File riftDir;
	private final File vortexDir;

	public JagexIntegration(File runeLiteDir, File riftDir)
	{
		this(runeLiteDir, riftDir, DEFAULT_VORTEX_DIR);
	}

	/** Overload taking the Vortex location so the detection can be exercised in tests. */
	JagexIntegration(File runeLiteDir, File riftDir, File vortexDir)
	{
		this.runeLiteDir = runeLiteDir;
		this.riftDir = riftDir;
		this.vortexDir = vortexDir;
	}

	/** The standard install location the Jagex Launcher uses for the RuneLite client. */
	public static File defaultRuneLiteDir()
	{
		String localAppData = System.getenv("LOCALAPPDATA");
		File base = localAppData == null
			? new File(System.getProperty("user.home"), "AppData/Local") : new File(localAppData);
		return new File(base, "RuneLite");
	}

	public boolean isRuneLiteInstalled()
	{
		return configFile().isFile() && javaExe().isFile();
	}

	public Status status()
	{
		if (!isRuneLiteInstalled())
		{
			return Status.NOT_INSTALLED;
		}
		RuneLiteConfig config = RuneLiteConfig.read(configFile());
		return config != null && config.pointsAt(SHIM_MAIN_CLASS) ? Status.ACTIVE : Status.REVERTED;
	}

	/**
	 * Backs up the current config (once, never overwriting an existing backup), records what RuneLite
	 * needs to launch normally, then repoints the config at the shim.
	 */
	public void apply() throws IOException
	{
		if (!isRuneLiteInstalled())
		{
			throw new IOException("RuneLite is not installed at " + runeLiteDir);
		}
		RuneLiteConfig current = RuneLiteConfig.read(configFile());
		if (current == null)
		{
			throw new IOException("Could not read RuneLite config at " + configFile());
		}

		// Only capture the original once. A second apply would otherwise record the already-redirected
		// config as the "original", losing the way back to RuneLite forever.
		if (!current.pointsAt(SHIM_MAIN_CLASS))
		{
			File backup = backupFile();
			if (!backup.isFile())
			{
				Files.copy(configFile().toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			writeShimConfig(current);
		}

		RuneLiteConfig.redirect(configFile(), riftLauncherJar().getAbsolutePath(), SHIM_MAIN_CLASS);
		log.info("Rift: Jagex Launcher integration applied ({})", configFile());
	}

	/** Re-applies the redirect after something reverted it. Returns false if there was nothing to do. */
	public boolean repair() throws IOException
	{
		if (status() != Status.REVERTED)
		{
			return false;
		}
		apply();
		return true;
	}

	/** Puts the original config back and drops the backup. Returns false if there is no backup. */
	public boolean restore() throws IOException
	{
		File backup = backupFile();
		if (!backup.isFile())
		{
			return false;
		}
		Files.copy(backup.toPath(), configFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
		Files.delete(backup.toPath());
		// shim.json only describes a redirect that no longer exists, so it goes too. User data
		// (accounts, sign-in, developer key) is deliberately left alone -- uninstalling must not
		// destroy the accounts someone imported.
		Files.deleteIfExists(shimConfigFile().toPath());
		log.info("Rift: Jagex Launcher integration removed; RuneLite config restored");
		return true;
	}

	private void writeShimConfig(RuneLiteConfig original) throws IOException
	{
		ShimConfig shim = new ShimConfig();
		shim.setRiftLauncherExe(
			new File(riftDir, "launcher-app/RiftLauncher/RiftLauncher.exe").getAbsolutePath());
		shim.setRiftLauncherJar(riftLauncherJar().getAbsolutePath());
		shim.setJavaExe(javaExe().getAbsolutePath());
		shim.setRuneLiteDir(runeLiteDir.getAbsolutePath());
		shim.setRuneLiteClassPath(original.getClassPath());
		shim.setRuneLiteMainClass(original.getMainClass());
		shim.setRuneLiteVmArgs(original.getVmArgs());

		// Optional convenience only: if Vortex is already installed, offer it in the chooser too. Rift
		// never requires Vortex — when it is absent these stay null and the chooser shows two buttons.
		File vortexExe = new File(vortexDir, "jre/bin/vortex-launcher.exe");
		File vortexJar = new File(vortexDir, "bin/vortex-launcher.jar");
		if (vortexExe.isFile() && vortexJar.isFile())
		{
			shim.setVortexExe(vortexExe.getAbsolutePath());
			shim.setVortexJar(vortexJar.getAbsolutePath());
		}

		ShimConfig.save(shimConfigFile(), shim);
	}

	public File shimConfigFile()
	{
		return new File(riftDir, "shim.json");
	}

	private File configFile()
	{
		return new File(runeLiteDir, "config.json");
	}

	private File backupFile()
	{
		return new File(runeLiteDir, BACKUP_NAME);
	}

	private File javaExe()
	{
		return new File(runeLiteDir, "jre/bin/javaw.exe");
	}

	private File riftLauncherJar()
	{
		return new File(riftDir, "rift-launcher.jar");
	}
}
