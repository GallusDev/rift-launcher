package rift.launcher.proxy;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import rift.launcher.crypto.Crypto;

public class ProxyStoreTest
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

	private ProxyStore store(String name)
	{
		return new ProxyStore(new File(tmp.getRoot(), name), new XorCrypto());
	}

	@Test
	public void passwordsAreNotReadableOnDisk() throws Exception
	{
		File file = new File(tmp.getRoot(), "proxies.dat");
		ProxyStore store = new ProxyStore(file, new XorCrypto());

		store.save(Collections.singletonList(
			new ProxyEntry("home", "1.2.3.4", 1080, "alice", "s3cret-password")));

		String onDisk = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
		assertFalse("the password must never be readable on disk", onDisk.contains("s3cret-password"));
		assertFalse(onDisk.contains("alice"));
		assertEquals("s3cret-password", store.load().get(0).getPassword());
	}

	@Test
	public void missingOrCorruptFileLoadsEmpty() throws Exception
	{
		assertTrue(store("absent.dat").load().isEmpty());

		File bad = tmp.newFile("corrupt.dat");
		Files.write(bad.toPath(), "not encrypted json".getBytes(StandardCharsets.UTF_8));
		// Must degrade to "no proxies" rather than throwing during launcher startup.
		assertTrue(new ProxyStore(bad, new XorCrypto()).load().isEmpty());
	}

	@Test
	public void addAssignsIdsAndSkipsDuplicateEndpoints() throws Exception
	{
		ProxyStore store = store("proxies.dat");

		int added = store.add(Arrays.asList(
			new ProxyEntry("a", "1.2.3.4", 1080, null, null),
			new ProxyEntry("b", "5.6.7.8", 1080, null, null)));
		assertEquals(2, added);

		// Same endpoint again -- provider lists repeat, and re-pasting one is normal.
		int addedAgain = store.add(Collections.singletonList(
			new ProxyEntry("a-again", "1.2.3.4", 1080, "user", "pass")));
		assertEquals(0, addedAgain);

		List<ProxyEntry> all = store.load();
		assertEquals(2, all.size());
		assertNotNull("ids are assigned on add", all.get(0).getId());
		assertFalse(all.get(0).getId().equals(all.get(1).getId()));
	}

	@Test
	public void updateReplacesByIdAndReportsUnknownIds() throws Exception
	{
		ProxyStore store = store("proxies.dat");
		store.add(Collections.singletonList(new ProxyEntry("a", "1.2.3.4", 1080, null, null)));

		ProxyEntry saved = store.load().get(0);
		saved.setNickname("renamed");
		saved.setLastStatus(ProxyEntry.Status.OK);
		saved.setLastExitIp("9.9.9.9");
		assertTrue(store.update(saved));

		ProxyEntry reloaded = store.byId(saved.getId());
		assertEquals("renamed", reloaded.getNickname());
		assertEquals(ProxyEntry.Status.OK, reloaded.getLastStatus());
		assertEquals("9.9.9.9", reloaded.getLastExitIp());

		ProxyEntry unknown = new ProxyEntry("x", "9.9.9.9", 1080, null, null);
		unknown.setId("no-such-id");
		assertFalse(store.update(unknown));
	}

	@Test
	public void removeDeletesOnlyTheMatchingProxy() throws Exception
	{
		ProxyStore store = store("proxies.dat");
		store.add(Arrays.asList(
			new ProxyEntry("a", "1.2.3.4", 1080, null, null),
			new ProxyEntry("b", "5.6.7.8", 1080, null, null)));
		String id = store.load().get(0).getId();

		assertTrue(store.remove(id));

		assertEquals(1, store.load().size());
		assertEquals("b", store.load().get(0).getNickname());
		assertFalse("removing twice is not an error, just false", store.remove(id));
	}

	@Test
	public void byIdReturnsNullRatherThanThrowing()
	{
		ProxyStore store = store("proxies.dat");
		assertNull(store.byId(null));
		assertNull(store.byId("no-such-id"));
	}

	@Test
	public void statusDefaultsToUnknownForEntriesNeverTested() throws Exception
	{
		ProxyStore store = store("proxies.dat");
		store.add(Collections.singletonList(new ProxyEntry("a", "1.2.3.4", 1080, null, null)));
		assertEquals(ProxyEntry.Status.UNKNOWN, store.load().get(0).getLastStatus());
	}
}
