package com.majod.llmbridge;

import com.google.gson.JsonObject;

/**
 * One piece of message content. Anthropic's API models a message as an ordered list of
 * blocks; the same block types appear on both the request side (tool_result) and the
 * response side (text, tool_use).
 *
 * <ul>
 *   <li>{@link Text}      — plain assistant or user text.</li>
 *   <li>{@link ToolUse}   — assistant request to invoke a tool with structured inputs.</li>
 *   <li>{@link ToolResult}— user message containing the result of a previous tool_use.</li>
 * </ul>
 *
 * Sealed so a switch over a CompletionResult's content blocks is exhaustive.
 */
public sealed interface ContentBlock {

	record Text(String text) implements ContentBlock {}

	/**
	 * Assistant-side block. {@code id} is opaque to us — we only need to echo it back
	 * inside the corresponding {@link ToolResult} on the next turn.
	 */
	record ToolUse(String id, String name, JsonObject input) implements ContentBlock {}

	/**
	 * User-side block. {@code content} is plain text (Anthropic also accepts a content-block
	 * array here, but a string is sufficient for our action results). {@code isError = true}
	 * tells the model the tool errored so it can recover or apologise.
	 */
	record ToolResult(String toolUseId, String content, boolean isError) implements ContentBlock {}
}
