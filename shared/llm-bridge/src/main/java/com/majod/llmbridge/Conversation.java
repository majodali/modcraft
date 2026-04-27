package com.majod.llmbridge;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable, in-memory message history for a single LLM session.
 *
 * Append-only by design — older turns are never edited, only added to or pruned from
 * the front for token-budget reasons (truncation TBD). Thread-confined: callers must
 * synchronize externally if accessed from multiple threads.
 *
 * Typical agentic loop:
 * <pre>{@code
 * Conversation conv = new Conversation();
 * conv.appendUserText("Build a small wooden bridge");
 * CompletionResult r = client.completeWithTools(conv, tools).get();
 * conv.appendAssistant(r.content());
 * List<ContentBlock.ToolResult> results = executeAll(r.toolUses());
 * conv.appendUserToolResults(results);
 * // ...repeat until r.stopReason().equals("end_turn")
 * }</pre>
 *
 * Persistence (save/load to/from JSON) is intentionally not built yet — wait for the
 * first feature that needs cross-session memory before designing the on-disk format.
 */
public final class Conversation {
	private final List<Message> messages = new ArrayList<>();

	public void appendUserText(String text) {
		messages.add(Message.userText(text));
	}

	public void appendAssistant(List<ContentBlock> content) {
		messages.add(Message.assistantContent(content));
	}

	public void appendUserToolResults(List<ContentBlock.ToolResult> results) {
		messages.add(Message.userToolResults(results));
	}

	/** Defensive snapshot; mutations to the returned list don't affect the conversation. */
	public List<Message> messages() {
		return List.copyOf(messages);
	}

	public int size() {
		return messages.size();
	}

	public boolean isEmpty() {
		return messages.isEmpty();
	}
}
