package com.majod.llmbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicClientTest {

	@Test
	void sendsWellFormedRequestAndParsesTextResponse() throws Exception {
		AtomicReference<String> capturedUrl = new AtomicReference<>();
		AtomicReference<Map<String, String>> capturedHeaders = new AtomicReference<>();
		AtomicReference<String> capturedBody = new AtomicReference<>();

		HttpTransport fake = (url, headers, body) -> {
			capturedUrl.set(url);
			capturedHeaders.set(headers);
			capturedBody.set(body);
			return """
				{
				  "id": "msg_01",
				  "type": "message",
				  "role": "assistant",
				  "content": [{"type": "text", "text": "Hello, world!"}],
				  "model": "claude-sonnet-4-6",
				  "stop_reason": "end_turn"
				}
				""";
		};

		AnthropicClient client = new AnthropicClient(fake, "sk-test", "claude-sonnet-4-6", 512);
		String reply = client.complete("Say hello").get();

		assertEquals("Hello, world!", reply);
		assertEquals("https://api.anthropic.com/v1/messages", capturedUrl.get());
		assertEquals("sk-test", capturedHeaders.get().get("x-api-key"));
		assertEquals("2023-06-01", capturedHeaders.get().get("anthropic-version"));
		assertEquals("application/json", capturedHeaders.get().get("content-type"));

		JsonObject parsed = JsonParser.parseString(capturedBody.get()).getAsJsonObject();
		assertEquals("claude-sonnet-4-6", parsed.get("model").getAsString());
		assertEquals(512, parsed.get("max_tokens").getAsInt());
		JsonObject firstMessage = parsed.getAsJsonArray("messages").get(0).getAsJsonObject();
		assertEquals("user", firstMessage.get("role").getAsString());
		assertEquals("Say hello", firstMessage.get("content").getAsString());
	}

	@Test
	void concatenatesMultipleTextBlocks() {
		String resp = """
			{
			  "content": [
			    {"type": "text", "text": "Part one. "},
			    {"type": "text", "text": "Part two."}
			  ]
			}
			""";
		assertEquals("Part one. Part two.", AnthropicClient.extractText(resp));
	}

	@Test
	void ignoresNonTextBlocks() {
		String resp = """
			{
			  "content": [
			    {"type": "tool_use", "name": "foo"},
			    {"type": "text", "text": "only this"}
			  ]
			}
			""";
		assertEquals("only this", AnthropicClient.extractText(resp));
	}

	@Test
	void returnsEmptyStringWhenContentMissing() {
		assertEquals("", AnthropicClient.extractText("{}"));
	}

	@Test
	void surfacesTransportErrors() {
		HttpTransport fake = (u, h, b) -> { throw new IOException("boom"); };
		AnthropicClient client = new AnthropicClient(fake, "k", "m", 1);
		var future = client.complete("x");
		var ex = assertThrows(ExecutionException.class, future::get);
		assertTrue(ex.getCause().getMessage().contains("boom"));
	}
}
