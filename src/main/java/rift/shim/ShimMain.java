package rift.shim;

import java.io.File;
import java.util.List;

/**
 * The entry point RuneLite's native launcher boots once Rift's integration has redirected its
 * {@code config.json}. Shows the chooser, then spawns the selected client as a child process, which
 * inherits the Jagex Launcher's {@code JX_*} variables and so logs in normally.
 *
 * <p>Every failure path ends in launching RuneLite. The user pressed Play expecting to play; a broken
 * Rift install must never be the reason nothing starts.
 */
public final class ShimMain
{
	private ShimMain()
	{
	}

	public static void main(String[] args)
	{
		try
		{
			run();
		}
		catch (Throwable t)
		{
			// Last-resort guard: anything unexpected still ends with RuneLite starting.
			System.err.println("Rift shim failed (" + t.getClass().getSimpleName() + "); launching RuneLite");
			launchRuneLiteBestEffort();
		}
		System.exit(0);
	}

	private static void run() throws Exception
	{
		File shimFile = shimConfigFile();
		ShimConfig config = ShimConfig.load(shimFile);
		if (config == null)
		{
			System.err.println("Rift shim: no shim.json; launching RuneLite");
			launchRuneLiteBestEffort();
			return;
		}

		boolean appImage = config.getRiftLauncherExe() != null
			&& new File(config.getRiftLauncherExe()).isFile();
		boolean jar = config.getRiftLauncherJar() != null
			&& new File(config.getRiftLauncherJar()).isFile();
		boolean riftAvailable = appImage || jar;
		boolean vortexAvailable = config.getVortexExe() != null
			&& new File(config.getVortexExe()).isFile();

		String choice = config.getRememberedChoice();
		// Re-ask if nothing is remembered, or if the remembered choice is Rift but Rift has gone missing.
		if (choice == null || (Chooser.RIFT.equals(choice) && !riftAvailable))
		{
			Chooser chooser = new Chooser();
			choice = chooser.prompt(riftAvailable, "Rift is not installed correctly - reinstall Rift",
				vortexAvailable);
			if (choice == null)
			{
				return; // dialog closed: user cancelled, launch nothing
			}
			if (chooser.isRemember())
			{
				config.setRememberedChoice(choice);
				try
				{
					ShimConfig.save(shimFile, config);
				}
				catch (Exception ignored)
				{
					// Not worth failing a launch over; they will simply be asked again next time.
				}
			}
		}

		if (Chooser.RIFT.equals(choice) && riftAvailable)
		{
			ClientSpawner.spawn(ClientSpawner.riftCommand(config, appImage), null);
			return;
		}
		if (Chooser.VORTEX.equals(choice) && vortexAvailable)
		{
			ClientSpawner.spawn(ClientSpawner.vortexCommand(config), null);
			return;
		}
		launchRuneLite(config);
	}

	private static void launchRuneLite(ShimConfig config) throws Exception
	{
		List<String> cmd = ClientSpawner.runeLiteCommand(config);
		ClientSpawner.spawn(cmd, new File(config.getRuneLiteDir()));
	}

	/** Fallback used when the chosen path is unusable: launch RuneLite from whatever we recorded. */
	private static void launchRuneLiteBestEffort()
	{
		try
		{
			ShimConfig config = ShimConfig.load(shimConfigFile());
			if (config != null && config.getRuneLiteMainClass() != null)
			{
				launchRuneLite(config);
			}
		}
		catch (Exception ex)
		{
			System.err.println("Rift shim: could not launch RuneLite (" + ex.getClass().getSimpleName() + ")");
		}
	}

	private static File shimConfigFile()
	{
		return new File(System.getProperty("user.home"), ".rift/shim.json");
	}
}
