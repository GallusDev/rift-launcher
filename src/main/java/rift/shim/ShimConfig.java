package rift.shim;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * State the shim needs at launch, written by the launcher at install time and read by {@link ShimMain}.
 *
 * <p>Deliberately plain data with no dependency on launcher internals: the shim runs inside the JVM
 * RuneLite's own launcher starts, before anything of ours exists. Contains no secrets — only paths and
 * the user's remembered choice. The Jagex {@code JX_*} credentials never appear here; they reach the
 * game through the inherited environment.
 */
public final class ShimConfig
{
	private static final Gson GSON = new Gson();

	@SerializedName("rift_launcher_exe")
	private String riftLauncherExe;

	@SerializedName("rift_launcher_jar")
	private String riftLauncherJar;

	@SerializedName("java_exe")
	private String javaExe;

	@SerializedName("runelite_dir")
	private String runeLiteDir;

	@SerializedName("runelite_class_path")
	private List<String> runeLiteClassPath;

	@SerializedName("runelite_main_class")
	private String runeLiteMainClass;

	@SerializedName("runelite_vm_args")
	private List<String> runeLiteVmArgs;

	/** {@code "rift"}, {@code "runelite"}, or null to ask each launch. */
	@SerializedName("remembered_choice")
	private String rememberedChoice;

	public static void save(File file, ShimConfig config) throws IOException
	{
		File parent = file.getParentFile();
		if (parent != null)
		{
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))
		{
			GSON.toJson(config, w);
		}
	}

	/** The stored config, or {@code null} if absent or unreadable — callers fall back to RuneLite. */
	public static ShimConfig load(File file)
	{
		if (!file.isFile())
		{
			return null;
		}
		try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			return GSON.fromJson(r, ShimConfig.class);
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	public String getRiftLauncherExe()
	{
		return riftLauncherExe;
	}

	public void setRiftLauncherExe(String riftLauncherExe)
	{
		this.riftLauncherExe = riftLauncherExe;
	}

	public String getRiftLauncherJar()
	{
		return riftLauncherJar;
	}

	public void setRiftLauncherJar(String riftLauncherJar)
	{
		this.riftLauncherJar = riftLauncherJar;
	}

	public String getJavaExe()
	{
		return javaExe;
	}

	public void setJavaExe(String javaExe)
	{
		this.javaExe = javaExe;
	}

	public String getRuneLiteDir()
	{
		return runeLiteDir;
	}

	public void setRuneLiteDir(String runeLiteDir)
	{
		this.runeLiteDir = runeLiteDir;
	}

	public List<String> getRuneLiteClassPath()
	{
		return runeLiteClassPath;
	}

	public void setRuneLiteClassPath(List<String> runeLiteClassPath)
	{
		this.runeLiteClassPath = runeLiteClassPath;
	}

	public String getRuneLiteMainClass()
	{
		return runeLiteMainClass;
	}

	public void setRuneLiteMainClass(String runeLiteMainClass)
	{
		this.runeLiteMainClass = runeLiteMainClass;
	}

	public List<String> getRuneLiteVmArgs()
	{
		return runeLiteVmArgs;
	}

	public void setRuneLiteVmArgs(List<String> runeLiteVmArgs)
	{
		this.runeLiteVmArgs = runeLiteVmArgs;
	}

	public String getRememberedChoice()
	{
		return rememberedChoice;
	}

	public void setRememberedChoice(String rememberedChoice)
	{
		this.rememberedChoice = rememberedChoice;
	}
}
