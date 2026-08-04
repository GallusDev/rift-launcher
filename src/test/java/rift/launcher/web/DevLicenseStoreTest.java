package rift.launcher.web;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import rift.launcher.crypto.Crypto;

/**
 * The developer key is a credential, so the store must round-trip it without ever leaving it readable
 * on disk — the same contract {@code AuthStoreTest} holds the refresh-token store to.
 */
public class DevLicenseStoreTest
{
	private static final String KEY = "rift_dev_0123456789abcdef0123456789abcdef01234567";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Reversible non-identity transform, so we can prove the file isn't plaintext. */
	private static final class XorCrypto implements Crypto
	{
		@Override
		public byte[] protect(byte[] plaintext)
		{
			return xor(plaintext);
		}

		@Override
		public byte[] unprotect(byte[] ciphertext)
		{
			return xor(ciphertext);
		}

		private static byte[] xor(byte[] in)
		{
			byte[] out = new byte[in.length];
			for (int i = 0; i < in.length; i++)
			{
				out[i] = (byte) (in[i] ^ 0x5A);
			}
			return out;
		}
	}

	@Test
	public void savesEncryptedAndLoadsBack() throws Exception
	{
		File file = new File(tmp.getRoot(), "devlicense.dat");
		DevLicenseStore store = new DevLicenseStore(file, new XorCrypto());

		store.save(KEY);

		String onDisk = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertFalse("the key must never be readable on disk", onDisk.contains(KEY));
		assertFalse(onDisk.contains("rift_dev_"));
		assertEquals(KEY, store.load());
	}

	@Test
	public void missingFileLoadsNull()
	{
		DevLicenseStore store = new DevLicenseStore(new File(tmp.getRoot(), "absent.dat"), new XorCrypto());
		assertNull(store.load());
	}

	@Test
	public void unreadableFileLoadsNullInsteadOfThrowing() throws Exception
	{
		File file = new File(tmp.getRoot(), "corrupt.dat");
		Files.write(file.toPath(), new byte[]{1, 2, 3});
		DevLicenseStore store = new DevLicenseStore(file, new Crypto()
		{
			@Override
			public byte[] protect(byte[] plaintext)
			{
				return plaintext;
			}

			@Override
			public byte[] unprotect(byte[] ciphertext)
			{
				throw new IllegalStateException("cannot decrypt");
			}
		});

		// A key encrypted by another Windows user (or a corrupt file) must degrade to "no key".
		assertNull(store.load());
	}

	@Test
	public void clearRemovesTheStoredKey() throws Exception
	{
		File file = new File(tmp.getRoot(), "devlicense.dat");
		DevLicenseStore store = new DevLicenseStore(file, new XorCrypto());
		store.save(KEY);
		assertTrue(file.exists());

		store.clear();

		assertFalse(file.exists());
		assertNull(store.load());
	}

	@Test
	public void maskShowsOnlyTheLastFourCharacters()
	{
		String masked = DevLicenseStore.mask(KEY);
		assertFalse("masking must not reveal the key", masked.contains(KEY));
		assertTrue(masked.endsWith("4567"));
		assertEquals("", DevLicenseStore.mask(null));
	}
}
