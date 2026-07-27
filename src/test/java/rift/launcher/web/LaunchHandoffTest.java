package rift.launcher.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class LaunchHandoffTest
{
	@Test
	public void serializesAllFieldsAsSingleJsonLine()
	{
		LaunchHandoff handoff = new LaunchHandoff("AT", "RT", 1999999999L,
			"http://localhost:3000", "anon-key", "https://proj.supabase.co");

		String json = handoff.toJson();
		assertFalse("must be one line for stdin framing", json.contains("\n"));

		JsonObject o = new Gson().fromJson(json, JsonObject.class);
		assertEquals("AT", o.get("access_token").getAsString());
		assertEquals("RT", o.get("refresh_token").getAsString());
		assertEquals(1999999999L, o.get("expires_at").getAsLong());
		assertEquals("http://localhost:3000", o.get("base_url").getAsString());
		assertEquals("anon-key", o.get("anon_key").getAsString());
		assertEquals("https://proj.supabase.co", o.get("supabase_url").getAsString());
	}
}
