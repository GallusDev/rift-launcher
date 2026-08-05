package rift.shim;

import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ClientSpawnerTest
{
	private static ShimConfig config()
	{
		ShimConfig c = new ShimConfig();
		c.setRiftLauncherExe("C:\\rift\\RiftLauncher.exe");
		c.setRiftLauncherJar("C:\\rift\\rift-launcher.jar");
		c.setJavaExe("C:\\RL\\jre\\bin\\javaw.exe");
		c.setRuneLiteDir("C:\\RL");
		c.setRuneLiteClassPath(Arrays.asList("RuneLite.jar"));
		c.setRuneLiteMainClass("net.runelite.launcher.Launcher");
		c.setRuneLiteVmArgs(Arrays.asList("-Xmx768m", "-Xss2m"));
		return c;
	}

	@Test
	public void riftCommandPrefersTheAppImageExe()
	{
		List<String> cmd = ClientSpawner.riftCommand(config(), true);
		assertEquals(Arrays.asList("C:\\rift\\RiftLauncher.exe"), cmd);
	}

	@Test
	public void riftCommandFallsBackToTheJarWhenExeMissing()
	{
		List<String> cmd = ClientSpawner.riftCommand(config(), false);
		assertEquals(Arrays.asList("C:\\RL\\jre\\bin\\javaw.exe", "-jar", "C:\\rift\\rift-launcher.jar"), cmd);
	}

	@Test
	public void runeLiteCommandReproducesTheOriginalLaunch()
	{
		List<String> cmd = ClientSpawner.runeLiteCommand(config());
		assertEquals(Arrays.asList(
			"C:\\RL\\jre\\bin\\javaw.exe",
			"-Xmx768m", "-Xss2m",
			"-cp", "RuneLite.jar",
			"net.runelite.launcher.Launcher"), cmd);
	}

	@Test
	public void runeLiteClassPathEntriesAreJoinedWithSemicolons()
	{
		ShimConfig c = config();
		c.setRuneLiteClassPath(Arrays.asList("RuneLite.jar", "extra.jar"));
		assertTrue(ClientSpawner.runeLiteCommand(c).contains("RuneLite.jar;extra.jar"));
	}

	@Test
	public void noCommandCarriesJagexCredentials()
	{
		// JX_* must reach the child through the inherited environment, never the command line,
		// where it would be visible in a process listing.
		String rift = String.join(" ", ClientSpawner.riftCommand(config(), true));
		String rl = String.join(" ", ClientSpawner.runeLiteCommand(config()));
		assertFalse(rift.contains("JX_"));
		assertFalse(rl.contains("JX_"));
	}
}
