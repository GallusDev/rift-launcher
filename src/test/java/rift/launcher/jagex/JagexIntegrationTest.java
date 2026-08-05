package rift.launcher.jagex;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import rift.shim.ShimConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JagexIntegrationTest
{
	private static final String ORIGINAL =
		"{\"classPath\":[\"RuneLite.jar\"],"
			+ "\"mainClass\":\"net.runelite.launcher.Launcher\","
			+ "\"vmArgs\":[\"-Xmx768m\"]}";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File runeLiteDir;
	private File riftDir;
	private JagexIntegration integration;

	@Before
	public void setUp() throws Exception
	{
		runeLiteDir = tmp.newFolder("RuneLite");
		riftDir = tmp.newFolder("rift");
		Files.write(new File(runeLiteDir, "config.json").toPath(), ORIGINAL.getBytes(StandardCharsets.UTF_8));
		File jreBin = new File(runeLiteDir, "jre/bin");
		//noinspection ResultOfMethodCallIgnored
		jreBin.mkdirs();
		Files.write(new File(jreBin, "javaw.exe").toPath(), new byte[]{0});
		Files.write(new File(riftDir, "rift-launcher.jar").toPath(), new byte[]{0});
		integration = new JagexIntegration(runeLiteDir, riftDir);
	}

	@Test
	public void detectsAnInstalledRuneLite()
	{
		assertTrue(integration.isRuneLiteInstalled());
		assertFalse(new JagexIntegration(new File(tmp.getRoot(), "nope"), riftDir).isRuneLiteInstalled());
	}

	@Test
	public void statusIsNotInstalledWhenRuneLiteIsAbsent()
	{
		assertEquals(JagexIntegration.Status.NOT_INSTALLED,
			new JagexIntegration(new File(tmp.getRoot(), "nope"), riftDir).status());
	}

	@Test
	public void applyRedirectsBacksUpAndRecordsTheOriginal() throws Exception
	{
		integration.apply();

		assertEquals(JagexIntegration.Status.ACTIVE, integration.status());

		File backup = new File(runeLiteDir, "config.json.rift-backup");
		assertTrue("original must be backed up", backup.isFile());
		assertEquals(ORIGINAL, new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8));

		ShimConfig shim = ShimConfig.load(new File(riftDir, "shim.json"));
		assertEquals(Arrays.asList("RuneLite.jar"), shim.getRuneLiteClassPath());
		assertEquals("net.runelite.launcher.Launcher", shim.getRuneLiteMainClass());
		assertEquals(Arrays.asList("-Xmx768m"), shim.getRuneLiteVmArgs());
		assertTrue(shim.getJavaExe().endsWith("javaw.exe"));
	}

	@Test
	public void applyNeverOverwritesAnExistingBackup() throws Exception
	{
		integration.apply();
		// A second apply must not capture the already-redirected config as if it were the original.
		integration.apply();

		File backup = new File(runeLiteDir, "config.json.rift-backup");
		assertEquals(ORIGINAL, new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void statusIsRevertedWhenSomethingElseRewroteTheConfig() throws Exception
	{
		integration.apply();
		Files.write(new File(runeLiteDir, "config.json").toPath(), ORIGINAL.getBytes(StandardCharsets.UTF_8));

		assertEquals(JagexIntegration.Status.REVERTED, integration.status());
	}

	@Test
	public void repairRestoresActiveStateFromReverted() throws Exception
	{
		integration.apply();
		Files.write(new File(runeLiteDir, "config.json").toPath(), ORIGINAL.getBytes(StandardCharsets.UTF_8));

		assertTrue(integration.repair());

		assertEquals(JagexIntegration.Status.ACTIVE, integration.status());
	}

	@Test
	public void restorePutsTheOriginalConfigBack() throws Exception
	{
		integration.apply();

		assertTrue(integration.restore());

		assertEquals(ORIGINAL, new String(
			Files.readAllBytes(new File(runeLiteDir, "config.json").toPath()), StandardCharsets.UTF_8));
		assertFalse(new File(runeLiteDir, "config.json.rift-backup").exists());
	}
}
