package com.majod.template;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeleton mod entrypoint. Copy this class + fabric.mod.json + build.gradle
 * into a new mod directory and fill in the actual behavior.
 *
 * To use the shared LLM library (already wired in build.gradle):
 *   import com.majod.llmbridge.LlmClient;
 *   import com.majod.llmbridge.LlmClientFactory;
 *   import com.majod.llmbridge.LlmConfig;
 * Look at mods/llmcraft/LlmCraftMod for a worked example of config loading
 * and a /ask-style command.
 */
public class TemplateMod implements ModInitializer {
	public static final String MOD_ID = "template";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Template mod initialized — replace me with real behavior.");
	}
}
