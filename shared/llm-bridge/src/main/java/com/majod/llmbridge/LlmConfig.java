package com.majod.llmbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LlmConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String provider = "ollama";
	public AnthropicConfig anthropic = new AnthropicConfig();
	public OllamaConfig ollama = new OllamaConfig();

	public static final class AnthropicConfig {
		public String apiKey = "";
		public String model = "claude-sonnet-4-6";
		public int maxTokens = 1024;
	}

	public static final class OllamaConfig {
		public String baseUrl = "http://localhost:11434";
		public String model = "llama3.2";
	}

	public static LlmConfig loadOrCreate(Path path) {
		try {
			if (Files.exists(path)) {
				String json = Files.readString(path);
				LlmConfig cfg = GSON.fromJson(json, LlmConfig.class);
				return cfg != null ? cfg : new LlmConfig();
			}
			LlmConfig defaults = new LlmConfig();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(defaults));
			return defaults;
		} catch (IOException e) {
			throw new RuntimeException("Failed to load LLM config at " + path, e);
		}
	}
}
