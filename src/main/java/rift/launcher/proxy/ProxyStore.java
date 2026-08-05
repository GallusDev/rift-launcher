package rift.launcher.proxy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import rift.launcher.crypto.Crypto;

/**
 * Persists saved proxies encrypted at rest, in the same way as the account and developer-key stores.
 *
 * <p>Proxy passwords are credentials — a leaked list is someone else's bandwidth billed to the user,
 * and often their identity attached to it — so they get the same DPAPI treatment as everything else
 * here rather than sitting in a plain config file.
 */
@Slf4j
public final class ProxyStore
{
	private static final Gson GSON = new Gson();

	private final File file;
	private final Crypto crypto;

	public ProxyStore(File file, Crypto crypto)
	{
		this.file = file;
		this.crypto = crypto;
	}

	public synchronized void save(List<ProxyEntry> proxies) throws IOException
	{
		byte[] json = GSON.toJson(proxies).getBytes(StandardCharsets.UTF_8);
		File parent = file.getParentFile();
		if (parent != null)
		{
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		Files.write(file.toPath(), crypto.protect(json));
	}

	/** The saved proxies, or an empty list if there are none or the file can't be read. */
	public synchronized List<ProxyEntry> load()
	{
		if (!file.exists())
		{
			return new ArrayList<>();
		}
		try
		{
			byte[] json = crypto.unprotect(Files.readAllBytes(file.toPath()));
			List<ProxyEntry> proxies = GSON.fromJson(new String(json, StandardCharsets.UTF_8),
				new TypeToken<List<ProxyEntry>>()
				{
				}.getType());
			return proxies == null ? new ArrayList<>() : proxies;
		}
		catch (Exception ex)
		{
			// Log the failure type only -- never the message, which could carry decrypted content.
			log.warn("Could not read proxy file {} ({}); treating as empty",
				file, ex.getClass().getSimpleName());
			return new ArrayList<>();
		}
	}

	/** Adds proxies, assigning ids and skipping any endpoint already saved. Returns how many were added. */
	public synchronized int add(List<ProxyEntry> toAdd) throws IOException
	{
		List<ProxyEntry> existing = load();
		List<String> endpoints = new ArrayList<>();
		for (ProxyEntry e : existing)
		{
			endpoints.add(e.endpoint());
		}

		int added = 0;
		for (ProxyEntry entry : toAdd)
		{
			if (endpoints.contains(entry.endpoint()))
			{
				continue;
			}
			if (entry.getId() == null)
			{
				entry.setId(UUID.randomUUID().toString());
			}
			existing.add(entry);
			endpoints.add(entry.endpoint());
			added++;
		}
		if (added > 0)
		{
			save(existing);
		}
		return added;
	}

	public synchronized boolean remove(String id) throws IOException
	{
		List<ProxyEntry> existing = load();
		boolean removed = existing.removeIf(e -> id.equals(e.getId()));
		if (removed)
		{
			save(existing);
		}
		return removed;
	}

	/** Replaces a saved proxy, matched on id. Returns false when the id is unknown. */
	public synchronized boolean update(ProxyEntry updated) throws IOException
	{
		List<ProxyEntry> existing = load();
		for (int i = 0; i < existing.size(); i++)
		{
			if (existing.get(i).getId() != null && existing.get(i).getId().equals(updated.getId()))
			{
				existing.set(i, updated);
				save(existing);
				return true;
			}
		}
		return false;
	}

	/** The proxy with this id, or {@code null} — used to resolve an account's assignment at launch. */
	public synchronized ProxyEntry byId(String id)
	{
		if (id == null)
		{
			return null;
		}
		for (ProxyEntry e : load())
		{
			if (id.equals(e.getId()))
			{
				return e;
			}
		}
		return null;
	}
}
