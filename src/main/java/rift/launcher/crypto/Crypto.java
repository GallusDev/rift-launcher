package rift.launcher.crypto;

public interface Crypto
{
	byte[] protect(byte[] plaintext);

	byte[] unprotect(byte[] ciphertext);
}
