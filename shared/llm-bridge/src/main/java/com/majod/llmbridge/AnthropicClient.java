package com.majod.llmbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class AnthropicClient implements LlmClient {
	private static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";
	private static final String API_VERSION = "2023-06-01";

	private final HttpTransport transport;
	private final String apiKey;
	private final String model;
	private final int maxTokens;
	private final String url;

	public AnthropicClient(HttpTransport transport, String apiKey, String model, int maxTokens) {
		this(transport, apiKey, model, maxTokens, DEFAULT_URL);
	}

	public AnthropicClient(HttpTransport transport, String apiKey, String model, int maxTokens, String url) {
		this.transport = transport;
		this.apiKey = apiKey;
		this.model = model;
		this.maxTokens = maxTokens;
		this.url = url;
	}

	@Override
	public CompletableFuture<String> complete(String prompt) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Map<String, String> headers = Map.of(
						"content-type", "application/json",
						"x-api-key", apiKey,
						"anthropic-version", API_VERSION);
				String resp = transport.post(url, headers, buildRequestBody(prompt));
				return extractText(resp);
			} catch (Exception e) {
				throw new RuntimeException("Anthropic request failed: " + e.getMessage(), e);
			}
		});
	}

	String buildRequestBody(String prompt) {
		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.addProperty("content", prompt);

		JsonArray messages = new JsonArray();
		messages.add(userMsg);

		JsonObject root = new JsonObject();
		root.addProperty("model", model);
		root.addProperty("max_tokens", maxTokens);
		root.add("messages", messages);
		return root.toString();
	}

	static String extractText(String responseJson) {
		JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
		if (!root.has("content")) return "";
		JsonArray content = root.getAsJsonArray("content");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < content.size(); i++) {
			JsonObject block = content.get(i).getAsJsonObject();
			if (block.has("type") && "text".equals(block.get("type").getAsString())) {
				sb.append(block.get("text").getAsString());
			}
		}
		return sb.toString();
	}
}
