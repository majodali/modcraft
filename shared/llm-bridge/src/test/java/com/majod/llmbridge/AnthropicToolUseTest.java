package com.majod.llmbridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicToolUseTest {

	private static Tool simplePingTool() {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		JsonObject props = new JsonObject();
		JsonObject xProp = new JsonObject();
		xProp.addProperty("type", "integer");
		props.add("x", xProp);
		schema.add("properties", props);
		JsonArray required = new JsonArray();
		required.add("x");
		schema.add("required", required);
		return new Tool("ping", "Echo a coordinate back", schema);
	}

	@Test
	void serializesToolDefinitionsAndUserMessage() throws Exception {
		AtomicReference<String> capturedBody = new AtomicReference<>();
		HttpTransport fake = (url, headers, body) -> {
			capturedBody.set(body);
			return """
				{ "content": [{"type":"text","text":"ok"}], "stop_reason": "end_turn" }
				""";
		};
		AnthropicClient client = new AnthropicClient(fake, "k", "claude-x", 256);
		Conversation conv = new Conversation();
		conv.appendUserText("Build something");

		client.completeWithTools(conv, List.of(simplePingTool())).get();

		JsonObject parsed = JsonParser.parseString(capturedBody.get()).getAsJsonObject();
		assertEquals("claude-x", parsed.get("model").getAsString());
		assertEquals(256, parsed.get("max_tokens").getAsInt());

		JsonArray tools = parsed.getAsJsonArray("tools");
		assertEquals(1, tools.size());
		JsonObject tool = tools.get(0).getAsJsonObject();
		assertEquals("ping", tool.get("name").getAsString());
		assertEquals("Echo a coordinate back", tool.get("description").getAsString());
		assertEquals("object", tool.getAsJsonObject("input_schema").get("type").getAsString());

		JsonArray messages = parsed.getAsJsonArray("messages");
		assertEquals(1, messages.size());
		JsonObject msg = messages.get(0).getAsJsonObject();
		assertEquals("user", msg.get("role").getAsString());
		JsonArray content = msg.getAsJsonArray("content");
		assertEquals("text", content.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("Build something", content.get(0).getAsJsonObject().get("text").getAsString());
	}

	@Test
	void omitsToolsArrayWhenEmpty() throws Exception {
		AtomicReference<String> capturedBody = new AtomicReference<>();
		HttpTransport fake = (url, headers, body) -> {
			capturedBody.set(body);
			return "{ \"content\":[], \"stop_reason\":\"end_turn\" }";
		};
		AnthropicClient client = new AnthropicClient(fake, "k", "m", 1);
		Conversation conv = new Conversation();
		conv.appendUserText("hi");

		client.completeWithTools(conv, List.of()).get();

		JsonObject parsed = JsonParser.parseString(capturedBody.get()).getAsJsonObject();
		assertFalse(parsed.has("tools"), "tools key should be omitted when no tools are declared");
	}

	@Test
	void roundTripsAssistantToolUseAndUserToolResult() throws Exception {
		AtomicReference<String> capturedBody = new AtomicReference<>();
		HttpTransport fake = (url, headers, body) -> {
			capturedBody.set(body);
			return "{ \"content\":[], \"stop_reason\":\"end_turn\" }";
		};
		AnthropicClient client = new AnthropicClient(fake, "k", "m", 1);

		// Simulate a multi-turn conversation: user asks → assistant calls tool → user replies with result.
		Conversation conv = new Conversation();
		conv.appendUserText("Place a stone block");
		JsonObject toolInput = new JsonObject();
		toolInput.addProperty("x", 10);
		toolInput.addProperty("y", 64);
		toolInput.addProperty("z", 20);
		toolInput.addProperty("material", "minecraft:stone");
		conv.appendAssistant(List.of(
				new ContentBlock.Text("I'll place a stone block."),
				new ContentBlock.ToolUse("toolu_abc123", "place_block", toolInput)));
		conv.appendUserToolResults(List.of(
				new ContentBlock.ToolResult("toolu_abc123", "Block placed at 10,64,20", false)));

		client.completeWithTools(conv, List.of()).get();

		JsonObject parsed = JsonParser.parseString(capturedBody.get()).getAsJsonObject();
		JsonArray messages = parsed.getAsJsonArray("messages");
		assertEquals(3, messages.size());

		// Assistant message: text + tool_use
		JsonObject assistant = messages.get(1).getAsJsonObject();
		assertEquals("assistant", assistant.get("role").getAsString());
		JsonArray asstContent = assistant.getAsJsonArray("content");
		assertEquals("text", asstContent.get(0).getAsJsonObject().get("type").getAsString());
		assertEquals("I'll place a stone block.", asstContent.get(0).getAsJsonObject().get("text").getAsString());
		JsonObject toolUseBlock = asstContent.get(1).getAsJsonObject();
		assertEquals("tool_use", toolUseBlock.get("type").getAsString());
		assertEquals("toolu_abc123", toolUseBlock.get("id").getAsString());
		assertEquals("place_block", toolUseBlock.get("name").getAsString());
		assertEquals(10, toolUseBlock.getAsJsonObject("input").get("x").getAsInt());
		assertEquals("minecraft:stone", toolUseBlock.getAsJsonObject("input").get("material").getAsString());

		// User reply: tool_result echoing the id
		JsonObject userReply = messages.get(2).getAsJsonObject();
		assertEquals("user", userReply.get("role").getAsString());
		JsonObject resultBlock = userReply.getAsJsonArray("content").get(0).getAsJsonObject();
		assertEquals("tool_result", resultBlock.get("type").getAsString());
		assertEquals("toolu_abc123", resultBlock.get("tool_use_id").getAsString());
		assertEquals("Block placed at 10,64,20", resultBlock.get("content").getAsString());
		assertFalse(resultBlock.has("is_error"), "successful result should omit is_error");
	}

	@Test
	void serializesIsErrorOnlyForFailedToolResults() throws Exception {
		AtomicReference<String> capturedBody = new AtomicReference<>();
		HttpTransport fake = (url, headers, body) -> {
			capturedBody.set(body);
			return "{ \"content\":[], \"stop_reason\":\"end_turn\" }";
		};
		AnthropicClient client = new AnthropicClient(fake, "k", "m", 1);

		Conversation conv = new Conversation();
		conv.appendUserText("x");
		conv.appendAssistant(List.of(new ContentBlock.ToolUse("t1", "f", new JsonObject())));
		conv.appendUserToolResults(List.of(new ContentBlock.ToolResult("t1", "permission denied", true)));

		client.completeWithTools(conv, List.of()).get();
		JsonObject parsed = JsonParser.parseString(capturedBody.get()).getAsJsonObject();
		JsonObject resultBlock = parsed.getAsJsonArray("messages").get(2).getAsJsonObject()
				.getAsJsonArray("content").get(0).getAsJsonObject();
		assertTrue(resultBlock.get("is_error").getAsBoolean());
	}

	@Test
	void parsesMixedContentResponse() {
		String resp = """
			{
			  "id": "msg_01",
			  "type": "message",
			  "role": "assistant",
			  "content": [
			    {"type": "text", "text": "I'll place two blocks."},
			    {"type": "tool_use", "id": "t1", "name": "place_block",
			     "input": {"x": 0, "y": 64, "z": 0, "material": "minecraft:dirt"}},
			    {"type": "tool_use", "id": "t2", "name": "place_block",
			     "input": {"x": 1, "y": 64, "z": 0, "material": "minecraft:stone"}}
			  ],
			  "stop_reason": "tool_use"
			}
			""";
		CompletionResult result = AnthropicClient.parseToolUseResponse(resp);
		assertEquals("tool_use", result.stopReason());
		assertEquals(3, result.content().size());
		assertEquals("I'll place two blocks.", result.text());

		List<ContentBlock.ToolUse> uses = result.toolUses();
		assertEquals(2, uses.size());
		assertEquals("t1", uses.get(0).id());
		assertEquals("place_block", uses.get(0).name());
		assertEquals("minecraft:dirt", uses.get(0).input().get("material").getAsString());
		assertEquals("t2", uses.get(1).id());
	}

	@Test
	void parseToleratesMissingFields() {
		// Response missing stop_reason and content should not throw.
		CompletionResult result = AnthropicClient.parseToolUseResponse("{}");
		assertEquals("end_turn", result.stopReason());
		assertTrue(result.content().isEmpty());
	}

	@Test
	void parseSkipsUnknownBlockTypes() {
		String resp = """
			{ "content": [
			    {"type": "text", "text": "hi"},
			    {"type": "magical_new_thing", "details": "ignore me"},
			    {"type": "text", "text": " there"}
			  ], "stop_reason": "end_turn" }
			""";
		CompletionResult result = AnthropicClient.parseToolUseResponse(resp);
		assertEquals(2, result.content().size());
		assertEquals("hi there", result.text());
	}
}
