package rift.launcher.web;

/**
 * Builds {@link Release} instances for tests. Production instances always come from Gson, which sets
 * the private fields directly, so {@code Release} deliberately has no public constructor or setters —
 * this keeps that true rather than widening the type just to make it testable.
 */
public final class ReleaseTestFactory
{
	private ReleaseTestFactory()
	{
	}

	public static Release release(String id, String version, String sha256, Long sizeBytes)
	{
		return Release.forTest(id, version, sha256, sizeBytes);
	}
}
