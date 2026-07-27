package rift.launcher.web;

/** Raised when the OAuth flow fails — the provider returned an error, or the flow timed out. */
public class OAuthException extends Exception
{
	public OAuthException(String message)
	{
		super(message);
	}

	public OAuthException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
