package rift.launcher.web;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import rift.launcher.crypto.Crypto;

/**
 * Persists the developer license key encrypted at rest (DPAPI in production, via {@link Crypto}), so a
 * developer enters it once instead of on every launch.
 *
 * <p>The key is a standalone credential — whoever holds it can prove they are an active developer — so
 * it gets the same treatment as the Supabase refresh token in {@link AuthStore}: encrypted on disk,
 * never written to a log, and never passed to the game client (the launcher verifies it and hands the
 * client only a boolean). Storing it does not grant developer mode on its own; it is re-verified
 * against the server at every launch so a revoked key stops working immediately.
 */
@Slf4j
public final class DevLicenseStore
{
	private final File file;
	private final Crypto crypto;

	public DevLicenseStore(File file, Crypto crypto)
	{
		this.file = file;
		this.crypto = crypto;
	}

	public synchronized void save(String key) throws IOException
	{
		byte[] encrypted = crypto.protect(key.getBytes(StandardCharsets.UTF_8));
		File parent = file.getParentFile();
		if (parent != null)
		{
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		Files.write(file.toPath(), encrypted);
	}

	/** The stored key, or {@code null} if none is saved or it can't be decrypted. */
	public synchronized String load()
	{
		if (!file.exists())
		{
			return null;
		}
		try
		{
			byte[] decrypted = crypto.unprotect(Files.readAllBytes(file.toPath()));
			return new String(decrypted, StandardCharsets.UTF_8);
		}
		catch (Exception ex)
		{
			// Log only the failure type — never the message, which could carry the key.
			log.warn("Could not read developer license file {} ({}); treating as absent",
				file, ex.getClass().getSimpleName());
			return null;
		}
	}

	public synchronized void clear()
	{
		//noinspection ResultOfMethodCallIgnored
		file.delete();
	}

	/** {@code rift_dev_••••••1a2b} — a masked preview for the UI, never the full key. */
	public static String mask(String key)
	{
		if (key == null || key.isEmpty())
		{
			return "";
		}
		String last4 = key.length() <= 4 ? key : key.substring(key.length() - 4);
		return "rift_dev_" + "••••••" + last4;
	}
}
