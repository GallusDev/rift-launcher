package rift.launcher.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class DpapiCryptoTest
{
	@Test
	public void protectThenUnprotectRoundTrips()
	{
		Crypto crypto = new DpapiCrypto();
		byte[] plain = "a-secret-refresh-token".getBytes(StandardCharsets.UTF_8);

		byte[] encrypted = crypto.protect(plain);
		byte[] decrypted = crypto.unprotect(encrypted);

		assertArrayEquals(plain, decrypted);
		assertFalse("ciphertext must differ from plaintext", Arrays.equals(plain, encrypted));
	}
}
