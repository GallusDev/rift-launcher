package rift.launcher.update;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UpdateServiceTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void sha256MatchesAKnownVector()
	{
		// sha256("abc"), so a wrong implementation cannot pass by comparing against itself.
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
			UpdateService.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void replaceAtomicallyOverwritesAndLeavesNoTempFile() throws Exception
	{
		File target = tmp.newFile("rift-client.jar");
		Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));

		UpdateService.replaceAtomically(target, "new".getBytes(StandardCharsets.UTF_8));

		assertEquals("new", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
		assertFalse("the .new staging file must not survive",
			new File(target.getParentFile(), target.getName() + ".new").exists());
	}

	@Test
	public void replaceAtomicallyCreatesTheFileWhenAbsent() throws Exception
	{
		File target = new File(tmp.getRoot(), "rift-client.jar");

		UpdateService.replaceAtomically(target, "fresh".getBytes(StandardCharsets.UTF_8));

		assertTrue(target.isFile());
		assertEquals("fresh", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void isUpdateOnlyWhenTheVersionActuallyDiffers()
	{
		assertFalse(InstalledVersions.isUpdate("1.0.0", "1.0.0"));
		assertTrue(InstalledVersions.isUpdate("1.0.0", "1.0.1"));
		// A deliberate rollback is still an update: versions are opaque strings we do not order.
		assertTrue(InstalledVersions.isUpdate("1.0.1", "1.0.0"));
	}

	@Test
	public void unknownVersionsNeverTriggerAnUpdate()
	{
		// Nothing recorded yet, or nothing advertised: must not download on every start.
		assertFalse(InstalledVersions.isUpdate(null, "1.0.0"));
		assertFalse(InstalledVersions.isUpdate("1.0.0", null));
		assertFalse(InstalledVersions.isUpdate("1.0.0", ""));
	}

	@Test
	public void versionsRoundTripAndDegradeToEmptyWhenUnreadable() throws Exception
	{
		File f = new File(tmp.getRoot(), "versions.json");
		InstalledVersions v = new InstalledVersions();
		v.setClientVersion("1.12.35");
		v.setLauncherVersion("1.0.0");
		v.save(f);

		InstalledVersions loaded = InstalledVersions.load(f);
		assertEquals("1.12.35", loaded.getClientVersion());
		assertEquals("1.0.0", loaded.getLauncherVersion());

		File bad = tmp.newFile("bad.json");
		Files.write(bad.toPath(), "not json".getBytes(StandardCharsets.UTF_8));
		// Unreadable must not throw during startup; it simply means "unknown".
		assertEquals(null, InstalledVersions.load(bad).getClientVersion());
		assertEquals(null, InstalledVersions.load(new File(tmp.getRoot(), "absent")).getClientVersion());
	}
}
