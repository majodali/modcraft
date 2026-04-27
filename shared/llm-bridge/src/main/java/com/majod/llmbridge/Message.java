package com.majod.llmbridge;

import java.util.List;
import java.util.Objects;

/**
 * One turn in a conversation. {@code role} is "user" or "assistant"; {@code content} is
 * an ordered list of blocks.
 *
 * Convenience factories cover the common cases:
 * <ul>
 *   <li>{@link #userText(String)} — plain user message.</li>
 *   <li>{@link #assistantContent(List)} — replay an assistant turn we received earlier.</li>
 *   <li>{@link #userToolResults(List)} — user message containing only tool_result blocks
 *       (the standard way to send tool outputs back to the model).</li>
 * </ul>
 */
public record Message(String role, List<ContentBlock> content) {

	public Message {
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(content, "content");
		content = List.copyOf(content);
	}

	public static Message userText(String text) {
		return new Message("user", List.of(new ContentBlock.Text(text)));
	}

	public static Message assistantContent(List<ContentBlock> content) {
		return new Message("assistant", content);
	}

	public static Message userToolResults(List<ContentBlock.ToolResult> results) {
		return new Message("user", List.copyOf(results));
	}
}
