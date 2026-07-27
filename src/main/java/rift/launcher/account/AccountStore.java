package rift.launcher.account;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import rift.launcher.crypto.Crypto;

/**
 * Persists the account list as an encrypted JSON blob. Encryption is delegated to {@link Crypto}
 * (DPAPI in production), so the on-disk file never contains plaintext tokens.
 */
@Slf4j
public class AccountStore
{
	private static final Gson GSON = new Gson();
	private static final Type LIST_TYPE = new TypeToken<ArrayList<Account>>()
	{
	}.getType();

	private final File file;
	private final Crypto crypto;

	public AccountStore(File file, Crypto crypto)
	{
		this.file = file;
		this.crypto = crypto;
	}

	public synchronized List<Account> load()
	{
		if (!file.exists())
		{
			return new ArrayList<>();
		}
		try
		{
			byte[] encrypted = Files.readAllBytes(file.toPath());
			byte[] json = crypto.unprotect(encrypted);
			List<Account> accounts = GSON.fromJson(new String(json, StandardCharsets.UTF_8), LIST_TYPE);
			return accounts == null ? new ArrayList<>() : accounts;
		}
		catch (Exception ex)
		{
			// Log only the failure type — the exception message can contain decrypted
			// plaintext (refresh tokens), which must never be written to logs.
			log.error("Could not read accounts file {} ({}); backing it up and starting empty",
				file, ex.getClass().getSimpleName());
			backupCorruptFile();
			return new ArrayList<>();
		}
	}

	public synchronized void save(List<Account> accounts) throws IOException
	{
		byte[] json = GSON.toJson(accounts, LIST_TYPE).getBytes(StandardCharsets.UTF_8);
		byte[] encrypted = crypto.protect(json);
		File parent = file.getParentFile();
		if (parent != null)
		{
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		Path tmp = Files.createTempFile(file.getParentFile().toPath(), "accounts", ".tmp");
		Files.write(tmp, encrypted);
		try
		{
			Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (java.nio.file.AtomicMoveNotSupportedException ex)
		{
			Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void backupCorruptFile()
	{
		try
		{
			Files.move(file.toPath(), new File(file.getParentFile(), file.getName() + ".bak").toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException ex)
		{
			log.warn("Could not back up corrupt accounts file {}", file, ex);
		}
	}
}
