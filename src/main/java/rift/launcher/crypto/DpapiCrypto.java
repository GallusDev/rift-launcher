package rift.launcher.crypto;

import com.sun.jna.platform.win32.Crypt32Util;

/**
 * Encrypts/decrypts bytes with the Windows Data Protection API, tied to the current Windows user.
 * A blob produced here cannot be decrypted by another user or on another machine.
 */
public class DpapiCrypto implements Crypto
{
	@Override
	public byte[] protect(byte[] plaintext)
	{
		return Crypt32Util.cryptProtectData(plaintext);
	}

	@Override
	public byte[] unprotect(byte[] ciphertext)
	{
		return Crypt32Util.cryptUnprotectData(ciphertext);
	}
}
