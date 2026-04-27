package com.majod.llmbridge;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface LlmClient {

	/**
	 * Plain text round-trip. No tools, no history, no system prompt — for simple
	 * single-shot uses like {@code /ask}.
	 */
	CompletableFuture<String> complete(String prompt);

	/**
	 * Multi-turn tool-use call. The conversation supplies the message history; tools
	 * declare what the model is allowed to invoke. Returns the next assistant turn's
	 * content + stop reason.
	 *
	 * Provider support:
	 * <ul>
	 *   <li>{@code AnthropicClient} — full support via the Messages API tool_use protocol.</li>
	 *   <li>{@code OllamaClient}    — not implemented; throws {@link UnsupportedOperationException}.
	 *       Add Ollama tool support when a feature requires it.</li>
	 * </ul>
	 */
	CompletableFuture<CompletionResult> completeWithTools(Conversation conversation, List<Tool> tools);
}
