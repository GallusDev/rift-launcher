package rift.launcher.web;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import rift.launcher.crypto.Crypto;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AuthStoreTest
{
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
		File file = new File(tmp.getRoot(), "auth.dat");
		AuthStore store = new AuthStore(file, new XorCrypto());

		store.save("my-refresh-token");

		byte[] onDisk = Files.readAllBytes(file.toPath());
		assertFalse("stored bytes must not be plaintext",
			new String(onDisk, StandardCharsets.UTF_8).contains("my-refresh-token"));

		assertEquals("my-refresh-token", store.load());
	}

	@Test
	public void loadReturnsNullWhenAbsent()
	{
		AuthStore store = new AuthStore(new File(tmp.getRoot(), "missing.dat"), new XorCrypto());
		assertNull(store.load());
	}

	@Test
	public void clearRemovesTheFile() throws Exception
	{
		File file = new File(tmp.getRoot(), "auth.dat");
		AuthStore store = new AuthStore(file, new XorCrypto());
		store.save("tok");
		assertTrue(file.exists());

		store.clear();
		assertFalse(file.exists());
		assertNull(store.load());
	}

	@Test
	public void roundTripsThroughCrypto() throws Exception
	{
		File file = new File(tmp.getRoot(), "auth.dat");
		Crypto crypto = new XorCrypto();
		new AuthStore(file, crypto).save("abc");

		byte[] onDisk = Files.readAllBytes(file.toPath());
		assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), crypto.unprotect(onDisk));
	}
}
