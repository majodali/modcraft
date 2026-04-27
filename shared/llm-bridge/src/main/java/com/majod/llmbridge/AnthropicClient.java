package com.majod.llmbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
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

	// ─────────────────────────────────────────────────────────────────────────────
	// Plain text complete (used by /ask)
	// ─────────────────────────────────────────────────────────────────────────────

	@Override
	public CompletableFuture<String> complete(String prompt) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String resp = transport.post(url, headers(), buildRequestBody(prompt));
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

	// ─────────────────────────────────────────────────────────────────────────────
	// Tool use (used by /imagine and other agentic flows)
	// ─────────────────────────────────────────────────────────────────────────────

	@Override
	public CompletableFuture<CompletionResult> completeWithTools(Conversation conversation, List<Tool> tools) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String body = buildToolUseRequestBody(conversation, tools);
				String resp = transport.post(url, headers(), body);
				return parseToolUseResponse(resp);
			} catch (Exception e) {
				throw new RuntimeException("Anthropic tool-use request failed: " + e.getMessage(), e);
			}
		});
	}

	private Map<String, String> headers() {
		return Map.of(
				"content-type", "application/json",
				"x-api-key", apiKey,
				"anthropic-version", API_VERSION);
	}

	String buildToolUseRequestBody(Conversation conversation, List<Tool> tools) {
		JsonObject root = new JsonObject();
		root.addProperty("model", model);
		root.addProperty("max_tokens", maxTokens);

		JsonArray toolsArray = new JsonArray();
		for (Tool tool : tools) {
			JsonObject t = new JsonObject();
			t.addProperty("name", tool.name());
			t.addProperty("description", tool.description());
			t.add("input_schema", tool.inputSchema());
			toolsArray.add(t);
		}
		if (toolsArray.size() > 0) {
			root.add("tools", toolsArray);
		}

		JsonArray messages = new JsonArray();
		for (Message msg : conversation.messages()) {
			messages.add(serializeMessage(msg));
		}
		root.add("messages", messages);
		return root.toString();
	}

	private static JsonObject serializeMessage(Message msg) {
		JsonObject m = new JsonObject();
		m.addProperty("role", msg.role());
		JsonArray content = new JsonArray();
		for (ContentBlock block : msg.content()) {
			content.add(serializeBlock(block));
		}
		m.add("content", content);
		return m;
	}

	private static JsonObject serializeBlock(ContentBlock block) {
		JsonObject b = new JsonObject();
		switch (block) {
			case ContentBlock.Text t -> {
				b.addProperty("type", "text");
				b.addProperty("text", t.text());
			}
			case ContentBlock.ToolUse u -> {
				b.addProperty("type", "tool_use");
				b.addProperty("id", u.id());
				b.addProperty("name", u.name());
				b.add("input", u.input());
			}
			case ContentBlock.ToolResult r -> {
				b.addProperty("type", "tool_result");
				b.addProperty("tool_use_id", r.toolUseId());
				b.addProperty("content", r.content());
				if (r.isError()) {
					b.addProperty("is_error", true);
				}
			}
		}
		return b;
	}

	static CompletionResult parseToolUseResponse(String responseJson) {
		JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
		List<ContentBlock> blocks = new ArrayList<>();
		if (root.has("content")) {
			JsonArray arr = root.getAsJsonArray("content");
			for (JsonElement el : arr) {
				JsonObject obj = el.getAsJsonObject();
				String type = obj.has("type") ? obj.get("type").getAsString() : "";
				switch (type) {
					case "text" -> blocks.add(new ContentBlock.Text(obj.get("text").getAsString()));
					case "tool_use" -> blocks.add(new ContentBlock.ToolUse(
							obj.get("id").getAsString(),
							obj.get("name").getAsString(),
							obj.has("input") ? obj.getAsJsonObject("input") : new JsonObject()));
					default -> { /* unknown block type — skip */ }
				}
			}
		}
		String stopReason = root.has("stop_reason") ? root.get("stop_reason").getAsString() : "end_turn";
		return new CompletionResult(blocks, stopReason);
	}
}
