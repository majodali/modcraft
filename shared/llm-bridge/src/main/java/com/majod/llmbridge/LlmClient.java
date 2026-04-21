package com.majod.llmbridge;

import java.util.concurrent.CompletableFuture;

public interface LlmClient {
	CompletableFuture<String> complete(String prompt);
}
