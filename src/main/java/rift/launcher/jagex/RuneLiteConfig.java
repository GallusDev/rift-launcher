package rift.launcher.jagex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * RuneLite's {@code config.json} — the file its native launcher stub reads to decide which
 * {@code classPath}/{@code mainClass} to boot with which {@code vmArgs}.
 *
 * <p>Edits go through a {@link JsonObject} rather than a typed POJO so that any field we do not know
 * about survives a rewrite untouched; RuneLite owns this file and may add keys at any time. The
 * {@code vmArgs} in particular are never modified — they carry flags RuneLite needs to run at all.
 */
public final class RuneLiteConfig
{
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final List<String> classPath;
	private final String mainClass;
	private final List<String> vmArgs;

	private RuneLiteConfig(List<String> classPath, String mainClass, List<String> vmArgs)
	{
		this.classPath = classPath;
		this.mainClass = mainClass;
		this.vmArgs = vmArgs;
	}

	/** Parsed config, or {@code null} if the file is missing or not readable as JSON. */
	public static RuneLiteConfig read(File file)
	{
		JsonObject root = readRoot(file);
		if (root == null)
		{
			return null;
		}
		return new RuneLiteConfig(
			strings(root, "classPath"),
			root.has("mainClass") && !root.get("mainClass").isJsonNull()
				? root.get("mainClass").getAsString() : null,
			strings(root, "vmArgs"));
	}

	/**
	 * Repoints the config at {@code jarPath}/{@code mainClass}, leaving {@code vmArgs} and every other
	 * field exactly as they were.
	 */
	public static void redirect(File file, String jarPath, String mainClass) throws IOException
	{
		JsonObject root = readRoot(file);
		if (root == null)
		{
			throw new IOException("Could not read RuneLite config: " + file);
		}
		JsonArray cp = new JsonArray();
		cp.add(jarPath);
		root.add("classPath", cp);
		root.addProperty("mainClass", mainClass);
		try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))
		{
			GSON.toJson(root, w);
		}
	}

	private static JsonObject readRoot(File file)
	{
		if (!file.isFile())
		{
			return null;
		}
		try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			return GSON.fromJson(r, JsonObject.class);
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private static List<String> strings(JsonObject root, String key)
	{
		List<String> out = new ArrayList<>();
		if (root.has(key) && root.get(key).isJsonArray())
		{
			for (JsonElement e : root.getAsJsonArray(key))
			{
				out.add(e.getAsString());
			}
		}
		return out;
	}

	public boolean pointsAt(String expectedMainClass)
	{
		return expectedMainClass.equals(mainClass);
	}

	public List<String> getClassPath()
	{
		return classPath;
	}

	public String getMainClass()
	{
		return mainClass;
	}

	public List<String> getVmArgs()
	{
		return vmArgs;
	}
}
