package rift.shim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and starts the child process for whichever client the user picked.
 *
 * <p>The child inherits this process's environment, which is how the Jagex Launcher's {@code JX_*}
 * variables reach the game. They are never placed on the command line, where a process listing would
 * expose them.
 */
public final class ClientSpawner
{
	private ClientSpawner()
	{
	}

	/**
	 * Prefers the jpackage app-image exe: it carries its own JRE (so it does not depend on RuneLite's
	 * bundled Java) and is the same entry point as the desktop shortcut, so behaviour cannot diverge
	 * between launching from the Jagex Launcher and launching directly.
	 */
	public static List<String> riftCommand(ShimConfig config, boolean appImagePresent)
	{
		List<String> cmd = new ArrayList<>();
		if (appImagePresent && config.getRiftLauncherExe() != null)
		{
			cmd.add(config.getRiftLauncherExe());
			return cmd;
		}
		cmd.add(config.getJavaExe());
		cmd.add("-jar");
		cmd.add(config.getRiftLauncherJar());
		return cmd;
	}

	/** Reconstructs RuneLite's original launch from the values recorded at install time. */
	public static List<String> runeLiteCommand(ShimConfig config)
	{
		List<String> cmd = new ArrayList<>();
		cmd.add(config.getJavaExe());
		if (config.getRuneLiteVmArgs() != null)
		{
			cmd.addAll(config.getRuneLiteVmArgs());
		}
		cmd.add("-cp");
		cmd.add(String.join(";", config.getRuneLiteClassPath()));
		cmd.add(config.getRuneLiteMainClass());
		return cmd;
	}

	/**
	 * Vortex's own launch line, mirroring how its shim invokes it. Offered only as a convenience when
	 * Vortex is already installed — Rift never requires it and never depends on it being present.
	 */
	public static List<String> vortexCommand(ShimConfig config)
	{
		List<String> cmd = new ArrayList<>();
		cmd.add(config.getVortexExe());
		cmd.add("-jar");
		cmd.add(config.getVortexJar());
		cmd.add("--jagex-import");
		return cmd;
	}

	/** Starts {@code cmd} in {@code workingDir}, inheriting this process's environment. */
	public static Process spawn(List<String> cmd, File workingDir) throws IOException
	{
		ProcessBuilder pb = new ProcessBuilder(cmd);
		if (workingDir != null && workingDir.isDirectory())
		{
			pb.directory(workingDir);
		}
		pb.inheritIO();
		return pb.start();
	}
}
