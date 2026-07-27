package rift.launcher.launch;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;

/**
 * The JX_* values replayed to a launched client. Mirrors exactly what the Jagex Launcher passes:
 * the three real values, plus the two token vars as empty (the game authenticates from the session).
 */
@Value
public class JxCredentials
{
	String sessionId;
	String characterId;
	String displayName;

	public Map<String, String> asEnvMap()
	{
		Map<String, String> env = new LinkedHashMap<>();
		env.put("JX_SESSION_ID", sessionId);
		env.put("JX_CHARACTER_ID", characterId);
		env.put("JX_DISPLAY_NAME", displayName);
		env.put("JX_ACCESS_TOKEN", "");
		env.put("JX_REFRESH_TOKEN", "");
		return env;
	}
}
