package rift.shim;

import java.io.File;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ShimConfigTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void roundTripsAllFields() throws Exception
	{
		File f = new File(tmp.getRoot(), "shim.json");
		ShimConfig c = new ShimConfig();
		c.setRiftLauncherExe("C:\\Users\\x\\.rift\\launcher-app\\RiftLauncher\\RiftLauncher.exe");
		c.setRiftLauncherJar("C:\\Users\\x\\.rift\\rift-launcher.jar");
		c.setJavaExe("C:\\RL\\jre\\bin\\javaw.exe");
		c.setRuneLiteClassPath(Arrays.asList("RuneLite.jar"));
		c.setRuneLiteMainClass("net.runelite.launcher.Launcher");
		c.setRuneLiteVmArgs(Arrays.asList("-Xmx768m", "-Xss2m"));
		c.setRuneLiteDir("C:\\RL");
		c.setRememberedChoice("rift");

		ShimConfig.save(f, c);
		ShimConfig loaded = ShimConfig.load(f);

		assertEquals("C:\\RL\\jre\\bin\\javaw.exe", loaded.getJavaExe());
		assertEquals(Arrays.asList("RuneLite.jar"), loaded.getRuneLiteClassPath());
		assertEquals("net.runelite.launcher.Launcher", loaded.getRuneLiteMainClass());
		assertEquals(Arrays.asList("-Xmx768m", "-Xss2m"), loaded.getRuneLiteVmArgs());
		assertEquals("rift", loaded.getRememberedChoice());
		assertEquals("C:\\RL", loaded.getRuneLiteDir());
		assertTrue(f.isFile());
	}

	@Test
	public void loadReturnsNullWhenMissingOrUnreadable() throws Exception
	{
		assertNull(ShimConfig.load(new File(tmp.getRoot(), "absent.json")));

		File bad = tmp.newFile("bad.json");
		java.nio.file.Files.write(bad.toPath(), "not json".getBytes("UTF-8"));
		assertNull("unreadable config must not throw", ShimConfig.load(bad));
	}

	@Test
	public void rememberedChoiceDefaultsToNull() throws Exception
	{
		File f = new File(tmp.getRoot(), "shim.json");
		ShimConfig.save(f, new ShimConfig());
		assertNull(ShimConfig.load(f).getRememberedChoice());
	}
}
