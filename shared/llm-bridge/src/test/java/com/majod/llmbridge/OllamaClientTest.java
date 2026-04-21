package com.majod.llmbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {

	@Test
	void sendsRequestToGenerateEndpoint() throws Exception {
		AtomicReference<String> capturedUrl = new AtomicReference<>();
		AtomicReference<String> capturedBody = new AtomicReference<>();

		HttpTransport fake = (url, headers, body) -> {
			capturedUrl.set(url);
			capturedBody.set(body);
			return "{\"model\":\"llama3.2\",\"response\":\"hi there\",\"done\":true}";
		};

		OllamaClient client = new OllamaClient(fake, "http://localhost:11434", "llama3.2");
		String reply = client.complete("hey").get();

		assertEquals("hi there", reply);
		assertEquals("http://localhost:11434/api/generate", capturedUrl.get());

		JsonObject body = JsonParser.parseString(capturedBody.get()).getAsJsonObject();
		assertEquals("llama3.2", body.get("model").getAsString());
		assertEquals("hey", body.get("prompt").getAsString());
		assertFalse(body.get("stream").getAsBoolean());
	}

	@Test
	void stripsTrailingSlashFromBaseUrl() throws Exception {
		AtomicReference<String> capturedUrl = new AtomicReference<>();
		HttpTransport fake = (url, h, b) -> {
			capturedUrl.set(url);
			return "{\"response\":\"ok\"}";
		};
		OllamaClient client = new OllamaClient(fake, "http://localhost:11434/", "m");
		client.complete("x").get();
		assertEquals("http://localhost:11434/api/generate", capturedUrl.get());
	}

	@Test
	void returnsEmptyStringWhenResponseFieldMissing() {
		assertEquals("", OllamaClient.extractText("{}"));
	}
}
