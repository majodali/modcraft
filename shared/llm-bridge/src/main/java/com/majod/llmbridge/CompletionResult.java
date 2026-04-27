package com.majod.llmbridge;

import java.util.List;
import java.util.Objects;

/**
 * Single LLM turn response. Mirrors Anthropic's `messages.create` response shape closely
 * enough that downstream consumers can iterate {@code content} blocks and dispatch on type.
 *
 * {@code stopReason}:
 * <ul>
 *   <li>{@code "end_turn"} — model is done; no more tool calls expected.</li>
 *   <li>{@code "tool_use"} — model emitted one or more tool_use blocks; caller should
 *       execute them and (in agentic mode) feed results back via
 *       {@link Message#userToolResults}.</li>
 *   <li>{@code "max_tokens"} — response truncated; consider raising the cap.</li>
 *   <li>{@code "stop_sequence"} — hit a configured stop string.</li>
 * </ul>
 */
public record CompletionResult(List<ContentBlock> content, String stopReason) {

	public CompletionResult {
		Objects.requireNonNull(content, "content");
		Objects.requireNonNull(stopReason, "stopReason");
		content = List.copyOf(content);
	}

	/** All tool_use blocks in the response, in order. */
	public List<ContentBlock.ToolUse> toolUses() {
		return content.stream()
				.filter(b -> b instanceof ContentBlock.ToolUse)
				.map(b -> (ContentBlock.ToolUse) b)
				.toList();
	}

	/** Concatenated text from all text blocks (in order, no separators). */
	public String text() {
		StringBuilder sb = new StringBuilder();
		for (ContentBlock b : content) {
			if (b instanceof ContentBlock.Text t) sb.append(t.text());
		}
		return sb.toString();
	}
}
