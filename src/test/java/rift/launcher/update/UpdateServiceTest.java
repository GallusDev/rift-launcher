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
import rift.launcher.web.ReleaseTestFactory;

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
	public void verifyAcceptsAMatchingChecksum()
	{
		byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
		String hash = UpdateService.sha256Hex(bytes);

		assertTrue(UpdateService.verify(bytes,
			ReleaseTestFactory.release("id", "1.0.0", hash, (long) bytes.length), "client"));
		// Case must not matter -- the server publishes lowercase hex, but that is not ours to assume.
		assertTrue(UpdateService.verify(bytes,
			ReleaseTestFactory.release("id", "1.0.0", hash.toUpperCase(), null), "client"));
	}

	@Test
	public void verifyRefusesAMissingChecksum()
	{
		byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);

		// A hard gate: what we install gets executed, so an unverifiable download is never accepted.
		assertFalse(UpdateService.verify(bytes,
			ReleaseTestFactory.release("id", "1.0.0", null, null), "launcher"));
		assertFalse(UpdateService.verify(bytes,
			ReleaseTestFactory.release("id", "1.0.0", "", null), "launcher"));
	}

	@Test
	public void verifyRefusesTamperedOrTruncatedDownloads()
	{
		byte[] bytes = "payload".getBytes(StandardCharsets.UTF_8);
		String hash = UpdateService.sha256Hex(bytes);

		// Wrong content.
		assertFalse(UpdateService.verify("tampered".getBytes(StandardCharsets.UTF_8),
			ReleaseTestFactory.release("id", "1.0.0", hash, null), "client"));
		// Right content, wrong length: caught before the hash is even computed.
		assertFalse(UpdateService.verify(bytes,
			ReleaseTestFactory.release("id", "1.0.0", hash, 999L), "client"));
		// Nothing at all.
		assertFalse(UpdateService.verify(new byte[0],
			ReleaseTestFactory.release("id", "1.0.0", hash, null), "client"));
	}

	@Test
	public void clientIsFetchedWhenTheJarIsMissing()
	{
		// The installer no longer ships the client, so a fresh install has no jar and must download.
		assertTrue(UpdateService.needsClientDownload(false, null, "1.12.35"));
		// Even with a recorded version, an absent jar has to be refetched -- a recorded version with no
		// file is a broken install, not an up-to-date one.
		assertTrue(UpdateService.needsClientDownload(false, "1.12.35", "1.12.35"));
	}

	@Test
	public void clientIsFetchedWhenTheRecordedVersionIsUnknown()
	{
		// Lost or corrupt versions.json self-heals rather than leaving the launcher unable to start.
		assertTrue(UpdateService.needsClientDownload(true, null, "1.12.35"));
	}

	@Test
	public void clientIsLeftAloneWhenItMatches()
	{
		assertFalse(UpdateService.needsClientDownload(true, "1.12.35", "1.12.35"));
	}

	@Test
	public void clientIsFetchedWhenTheServerAdvertisesSomethingElse()
	{
		assertTrue(UpdateService.needsClientDownload(true, "1.12.35", "1.12.36"));
		// A deliberate rollback is still a change worth applying.
		assertTrue(UpdateService.needsClientDownload(true, "1.12.36", "1.12.35"));
	}

	@Test
	public void nothingIsFetchedWhenTheServerAdvertisesNothing()
	{
		assertFalse(UpdateService.needsClientDownload(false, null, null));
		assertFalse(UpdateService.needsClientDownload(true, "1.12.35", ""));
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
