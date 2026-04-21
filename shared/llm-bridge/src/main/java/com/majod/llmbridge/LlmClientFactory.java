package com.majod.llmbridge;

public final class LlmClientFactory {
	private LlmClientFactory() {}

	public static LlmClient create(LlmConfig config) {
		HttpTransport transport = new JdkHttpTransport();
		return switch (config.provider) {
			case "anthropic" -> new AnthropicClient(
					transport,
					config.anthropic.apiKey,
					config.anthropic.model,
					config.anthropic.maxTokens);
			case "ollama" -> new OllamaClient(
					transport,
					config.ollama.baseUrl,
					config.ollama.model);
			default -> throw new IllegalArgumentException("Unknown LLM provider: " + config.provider);
		};
	}
}
