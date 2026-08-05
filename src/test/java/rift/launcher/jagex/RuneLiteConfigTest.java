package rift.launcher.jagex;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RuneLiteConfigTest
{
	private static final String ORIGINAL =
		"{\"classPath\":[\"RuneLite.jar\"],"
			+ "\"mainClass\":\"net.runelite.launcher.Launcher\","
			+ "\"vmArgs\":[\"-Xmx768m\",\"-Xss2m\"],"
			+ "\"unknownFutureField\":{\"keep\":true}}";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File configWith(String json) throws Exception
	{
		File f = new File(tmp.getRoot(), "config.json");
		Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
		return f;
	}

	@Test
	public void readsClassPathMainClassAndVmArgs() throws Exception
	{
		RuneLiteConfig c = RuneLiteConfig.read(configWith(ORIGINAL));
		assertEquals(Arrays.asList("RuneLite.jar"), c.getClassPath());
		assertEquals("net.runelite.launcher.Launcher", c.getMainClass());
		assertEquals(Arrays.asList("-Xmx768m", "-Xss2m"), c.getVmArgs());
	}

	@Test
	public void redirectReplacesEntryPointButPreservesVmArgsAndUnknownFields() throws Exception
	{
		File f = configWith(ORIGINAL);

		RuneLiteConfig.redirect(f, "C:\\rift\\rift-launcher.jar", "rift.shim.ShimMain");

		String after = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		RuneLiteConfig c = RuneLiteConfig.read(f);
		assertEquals(Arrays.asList("C:\\rift\\rift-launcher.jar"), c.getClassPath());
		assertEquals("rift.shim.ShimMain", c.getMainClass());
		// vmArgs must survive verbatim -- they carry RuneLite's required JVM flags.
		assertEquals(Arrays.asList("-Xmx768m", "-Xss2m"), c.getVmArgs());
		assertTrue("unknown fields must be preserved", after.contains("unknownFutureField"));
	}

	@Test
	public void readReturnsNullForMissingOrMalformedFile() throws Exception
	{
		assertNull(RuneLiteConfig.read(new File(tmp.getRoot(), "absent.json")));
		assertNull(RuneLiteConfig.read(configWith("not json")));
	}

	@Test
	public void pointsAtDetectsOurMainClass() throws Exception
	{
		assertTrue(RuneLiteConfig.read(configWith(
			"{\"classPath\":[\"x.jar\"],\"mainClass\":\"rift.shim.ShimMain\",\"vmArgs\":[]}"))
			.pointsAt("rift.shim.ShimMain"));
		assertEquals(false, RuneLiteConfig.read(configWith(ORIGINAL)).pointsAt("rift.shim.ShimMain"));
	}
}
