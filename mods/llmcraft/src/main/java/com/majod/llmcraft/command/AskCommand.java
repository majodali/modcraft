package com.majod.llmcraft.command;

import com.majod.llmcraft.LlmCraftMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class AskCommand {
	private AskCommand() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("ask")
				.then(CommandManager.argument("prompt", StringArgumentType.greedyString())
						.executes(ctx -> {
							String prompt = StringArgumentType.getString(ctx, "prompt");
							ServerCommandSource source = ctx.getSource();
							source.sendFeedback(() -> Text.literal("[LLM] thinking…").formatted(Formatting.GRAY), false);

							LlmCraftMod.llm().complete(prompt).whenComplete((reply, err) ->
									source.getServer().execute(() -> {
										if (err != null) {
											LlmCraftMod.LOGGER.error("LLM call failed", err);
											source.sendError(Text.literal("LLM error: " + err.getMessage()));
											return;
										}
										source.sendFeedback(() -> Text.literal("[LLM] " + reply).formatted(Formatting.AQUA), false);
									}));
							return 1;
						})));
	}
}
