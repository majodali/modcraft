package com.majod.llmcraft;

import com.majod.llmbridge.LlmClient;
import com.majod.llmbridge.LlmClientFactory;
import com.majod.llmbridge.LlmConfig;
import com.majod.llmcraft.action.PlayerSessions;
import com.majod.llmcraft.command.AskCommand;
import com.majod.llmcraft.command.ImagineCommand;
import com.majod.llmcraft.command.IterateCommand;
import com.majod.llmcraft.command.UndoCommand;
import com.majod.llmcraft.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class LlmCraftMod implements ModInitializer {
	public static final String MOD_ID = "llmcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static LlmClient llmClient;

	public static LlmClient llm() {
		return llmClient;
	}

	@Override
	public void onInitialize() {
		Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
		LlmConfig config = LlmConfig.loadOrCreate(configPath);
		llmClient = LlmClientFactory.create(config);
		LOGGER.info("LLMCraft initialized with provider={}", config.provider);

		ModItems.register();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			AskCommand.register(dispatcher);
			ImagineCommand.register(dispatcher);
			IterateCommand.register(dispatcher);
			UndoCommand.register(dispatcher);
		});

		// Drop a player's undo stack + cached conversation when they disconnect, and
		// blow away every session on server stop. Keeps memory bounded and prevents
		// stale state from surviving a relog.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				PlayerSessions.clear(handler.player.getUuid()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PlayerSessions.clearAll());
	}
}
