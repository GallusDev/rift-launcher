package rift.launcher.launch;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Spawns the Rift client. Jagex credentials go via JX_* env vars (never the command line, so they
 * don't leak into process listings). The Rift/Supabase session is handed off over the client's
 * <b>stdin</b> as one JSON line — never env or disk — signalled by {@code RIFT_MANAGED_HANDOFF}.
 */
@Slf4j
public class ClientLauncher
{
	/** Env var that tells the client a handoff line is waiting on stdin. */
	static final String HANDOFF_SIGNAL = "RIFT_MANAGED_HANDOFF";

	static List<String> buildCommand(File javaw, File clientJar)
	{
		List<String> cmd = new ArrayList<>();
		cmd.add(javaw.getAbsolutePath());
		cmd.add("-jar");
		cmd.add(clientJar.getAbsolutePath());
		return cmd;
	}

	/** The environment additions for a launch: JX_* plus the handoff signal when a session is passed. */
	static Map<String, String> buildEnv(JxCredentials creds, boolean withHandoff)
	{
		Map<String, String> env = new LinkedHashMap<>(creds.asEnvMap());
		if (withHandoff)
		{
			env.put(HANDOFF_SIGNAL, "1");
		}
		return env;
	}

	/** Launch without a Rift session (Jagex only) — inherits IO. */
	public Process launch(JxCredentials creds, File javaw, File clientJar) throws IOException
	{
		if (!clientJar.isFile())
		{
			throw new IOException("Rift client jar not found: " + clientJar.getAbsolutePath());
		}
		ProcessBuilder pb = new ProcessBuilder(buildCommand(javaw, clientJar));
		pb.environment().putAll(buildEnv(creds, false));
		pb.inheritIO();
		log.info("Launching Rift client for character {}", creds.getCharacterId());
		return pb.start();
	}

	/**
	 * Launch with a Rift session: the {@code handoffJson} line is written to the client's stdin (then
	 * closed, sending EOF), so the client reads exactly one line. stdout/stderr are inherited.
	 */
	public Process launch(JxCredentials creds, String handoffJson, File javaw, File clientJar) throws IOException
	{
		if (!clientJar.isFile())
		{
			throw new IOException("Rift client jar not found: " + clientJar.getAbsolutePath());
		}
		ProcessBuilder pb = new ProcessBuilder(buildCommand(javaw, clientJar));
		pb.environment().putAll(buildEnv(creds, true));
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		pb.redirectError(ProcessBuilder.Redirect.INHERIT);
		// stdin stays a pipe so we can write the handoff.
		log.info("Launching Rift client for character {} (managed session)", creds.getCharacterId());
		Process process = pb.start();
		try (OutputStream stdin = process.getOutputStream())
		{
			stdin.write((handoffJson + "\n").getBytes(StandardCharsets.UTF_8));
			stdin.flush();
		}
		return process;
	}
}
