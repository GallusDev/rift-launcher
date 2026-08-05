package rift.launcher.account;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import rift.launcher.crypto.Crypto;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AccountStoreTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	// Reversible non-DPAPI crypto so the store logic is testable off-Windows and deterministically.
	private static final Crypto XOR = new Crypto()
	{
		private byte[] x(byte[] b)
		{
			byte[] o = new byte[b.length];
			for (int i = 0; i < b.length; i++)
			{
				o[i] = (byte) (b[i] ^ 0x5A);
			}
			return o;
		}

		public byte[] protect(byte[] p)
		{
			return x(p);
		}

		public byte[] unprotect(byte[] c)
		{
			return x(c);
		}
	};

	@Test
	public void saveThenLoadRoundTrips() throws Exception
	{
		File f = new File(folder.getRoot(), "accounts.dat");
		AccountStore store = new AccountStore(f, XOR);

		Account a = new Account("char-1", "Zezima", "sess-abc", 111L, null);
		store.save(Collections.singletonList(a));

		List<Account> loaded = store.load();
		assertEquals(1, loaded.size());
		assertEquals("char-1", loaded.get(0).getCharacterId());
		assertEquals("Zezima", loaded.get(0).getDisplayName());
		assertEquals("sess-abc", loaded.get(0).getSessionId());
	}

	@Test
	public void fileOnDiskIsNotPlaintext() throws Exception
	{
		File f = new File(folder.getRoot(), "accounts.dat");
		AccountStore store = new AccountStore(f, XOR);
		store.save(Collections.singletonList(new Account("c", "d", "sess-SECRET", 1L, null)));

		byte[] raw = Files.readAllBytes(f.toPath());
		assertTrue(f.exists());
		assertEquals(-1, new String(raw, java.nio.charset.StandardCharsets.UTF_8).indexOf("sess-SECRET"));
	}

	@Test
	public void missingFileLoadsEmpty() throws Exception
	{
		AccountStore store = new AccountStore(new File(folder.getRoot(), "nope.dat"), XOR);
		assertEquals(0, store.load().size());
	}

	@Test
	public void corruptFileBacksUpAndLoadsEmpty() throws Exception
	{
		File f = new File(folder.getRoot(), "accounts.dat");
		Files.write(f.toPath(), new byte[]{1, 2, 3, 4, 5});
		AccountStore store = new AccountStore(f, XOR);

		List<Account> loaded = store.load();

		assertEquals(0, loaded.size());
		File bak = new File(folder.getRoot(), "accounts.dat.bak");
		assertTrue("corrupt file should be backed up", bak.exists());
		assertArrayEquals("backup must preserve the original bytes, not wipe them",
			new byte[]{1, 2, 3, 4, 5}, Files.readAllBytes(bak.toPath()));
	}
}
