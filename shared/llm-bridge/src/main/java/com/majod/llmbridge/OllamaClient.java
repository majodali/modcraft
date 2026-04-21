package com.majod.llmbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class OllamaClient implements LlmClient {
	private final HttpTransport transport;
	private final String baseUrl;
	private final String model;

	public OllamaClient(HttpTransport transport, String baseUrl, String model) {
		this.transport = transport;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.model = model;
	}

	@Override
	public CompletableFuture<String> complete(String prompt) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Map<String, String> headers = Map.of("content-type", "application/json");
				String resp = transport.post(baseUrl + "/api/generate", headers, buildRequestBody(prompt));
				return extractText(resp);
			} catch (Exception e) {
				throw new RuntimeException("Ollama request failed: " + e.getMessage(), e);
			}
		});
	}

	String buildRequestBody(String prompt) {
		JsonObject root = new JsonObject();
		root.addProperty("model", model);
		root.addProperty("prompt", prompt);
		root.addProperty("stream", false);
		return root.toString();
	}

	static String extractText(String responseJson) {
		JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
		return root.has("response") ? root.get("response").getAsString() : "";
	}
}
