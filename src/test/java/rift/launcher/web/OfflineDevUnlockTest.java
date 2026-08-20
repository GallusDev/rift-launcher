package rift.launcher.web;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import rift.launcher.crypto.Crypto;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfflineDevUnlockTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** DPAPI is unavailable off-Windows and in CI, so the round trip is exercised with a stub. */
	private static final Crypto PLAIN = new Crypto()
	{
		@Override
		public byte[] protect(byte[] plaintext)
		{
			return plaintext.clone();
		}

		@Override
		public byte[] unprotect(byte[] ciphertext)
		{
			return ciphertext.clone();
		}
	};

	private OfflineDevUnlock store() throws Exception
	{
		return new OfflineDevUnlock(new File(tmp.getRoot(), "devoffline.dat"), PLAIN);
	}

	@Test
	public void acceptsTheConfiguredCredential() throws Exception
	{
		OfflineDevUnlock store = store();
		store.configure("admin", "admin".toCharArray());
		assertTrue(store.isConfigured());
		assertTrue(store.verify("admin", "admin".toCharArray()));
	}

	@Test
	public void rejectsAWrongPassword() throws Exception
	{
		OfflineDevUnlock store = store();
		store.configure("admin", "admin".toCharArray());
		assertFalse(store.verify("admin", "hunter2".toCharArray()));
	}

	@Test
	public void rejectsAWrongUsername() throws Exception
	{
		OfflineDevUnlock store = store();
		store.configure("admin", "admin".toCharArray());
		assertFalse(store.verify("root", "admin".toCharArray()));
	}

	/**
	 * The failure mode that would matter most: an unconfigured launcher must refuse everything rather
	 * than treat "nothing to compare against" as a match. A shipped build has no credential file, so
	 * this is what stands between it and an open door.
	 */
	@Test
	public void refusesEverythingWhenNotConfigured() throws Exception
	{
		OfflineDevUnlock store = store();
		assertFalse(store.isConfigured());
		assertFalse(store.verify("admin", "admin".toCharArray()));
		assertFalse(store.verify("", "".toCharArray()));
		assertFalse(store.verify(null, null));
	}

	/** A corrupt or truncated file is treated as absent, not as a pass. */
	@Test
	public void treatsACorruptFileAsAbsent() throws Exception
	{
		File file = new File(tmp.getRoot(), "devoffline.dat");
		Files.write(file.toPath(), "not a credential".getBytes(StandardCharsets.UTF_8));
		OfflineDevUnlock store = new OfflineDevUnlock(file, PLAIN);
		assertFalse(store.isConfigured());
		assertFalse(store.verify("admin", "admin".toCharArray()));
	}

	/** The password is never recoverable from the file -- it stores a salted hash, not the secret. */
	@Test
	public void storesAHashRatherThanThePassword() throws Exception
	{
		File file = new File(tmp.getRoot(), "devoffline.dat");
		OfflineDevUnlock store = new OfflineDevUnlock(file, PLAIN);
		store.configure("admin", "correct horse battery staple".toCharArray());
		String onDisk = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		assertFalse("the password must not appear on disk",
			onDisk.contains("correct horse battery staple"));
		assertTrue("the username is not a secret and is stored plainly", onDisk.contains("admin"));
	}

	/** Two identical passwords hash differently, so the file does not reveal reuse. */
	@Test
	public void saltsEachCredential() throws Exception
	{
		File a = new File(tmp.getRoot(), "a.dat");
		File b = new File(tmp.getRoot(), "b.dat");
		new OfflineDevUnlock(a, PLAIN).configure("admin", "admin".toCharArray());
		new OfflineDevUnlock(b, PLAIN).configure("admin", "admin".toCharArray());
		assertFalse(Files.readAllLines(a.toPath()).equals(Files.readAllLines(b.toPath())));
	}

	@Test
	public void reconfiguringReplacesTheOldCredential() throws Exception
	{
		OfflineDevUnlock store = store();
		store.configure("admin", "admin".toCharArray());
		store.configure("admin", "newpass".toCharArray());
		assertFalse(store.verify("admin", "admin".toCharArray()));
		assertTrue(store.verify("admin", "newpass".toCharArray()));
	}

	@Test
	public void clearForgetsIt() throws Exception
	{
		OfflineDevUnlock store = store();
		store.configure("admin", "admin".toCharArray());
		store.clear();
		assertFalse(store.isConfigured());
		assertFalse(store.verify("admin", "admin".toCharArray()));
	}
}
