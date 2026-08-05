package rift.launcher;

import java.io.InputStream;
import java.util.Properties;

/**
 * This build's version, read from a resource the build stamps with the Gradle project version.
 *
 * <p>Read rather than hard-coded because the version has two consumers — the window title and the
 * update check that decides whether the launcher channel has something newer — and it previously
 * existed twice (a constant here and {@code version} in build.gradle.kts) which had already drifted
 * apart. Bumping {@code version} in the build file is now the only place to change.
 */
public final class RiftVersion
{
	/** Used when running from a classpath with no stamped resource, i.e. straight out of an IDE. */
	private static final String DEV = "dev";

	private static final String VERSION = read();

	private RiftVersion()
	{
	}

	public static String get()
	{
		return VERSION;
	}

	private static String read()
	{
		try (InputStream in = RiftVersion.class.getResourceAsStream("/rift/launcher/version.properties"))
		{
			if (in == null)
			{
				return DEV;
			}
			Properties props = new Properties();
			props.load(in);
			String version = props.getProperty("version", "").trim();
			// An unfiltered resource still holds the literal placeholder; treat that as a dev run
			// rather than letting "${project.version}" reach the title bar or an update check.
			if (version.isEmpty() || version.startsWith("${"))
			{
				return DEV;
			}
			return version;
		}
		catch (Exception ex)
		{
			return DEV;
		}
	}
}
