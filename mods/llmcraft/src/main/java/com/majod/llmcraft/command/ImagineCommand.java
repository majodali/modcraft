package com.majod.llmcraft.command;

import com.majod.llmbridge.CompletionResult;
import com.majod.llmbridge.ContentBlock;
import com.majod.llmbridge.Conversation;
import com.majod.llmcraft.LlmCraftMod;
import com.majod.llmcraft.action.Action;
import com.majod.llmcraft.action.ActionDispatcher;
import com.majod.llmcraft.action.ActionResult;
import com.majod.llmcraft.action.PlayerSessions;
import com.majod.llmcraft.action.Tools;
import com.majod.llmcraft.action.WorldSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /imagine <description>} — one-shot creative LLM build command.
 *
 * Flow:
 * <ol>
 *   <li>Snapshot the world around the player.</li>
 *   <li>Build a conversation with the system instructions + snapshot + user request.</li>
 *   <li>Call the LLM with the full tool set ({@link Tools#ALL}).</li>
 *   <li>Execute every tool_use in the response, recording inverses to the player's
 *       undo stack.</li>
 *   <li>Stash the conversation on the player's session for {@code /iterate}.</li>
 *   <li>Report a one-line summary in chat.</li>
 * </ol>
 *
 * "One-shot" = exactly one LLM call. Predictable cost; no agentic loop. The model has
 * to commit its full plan in a single turn. Multi-turn refinement is exposed as
 * {@code /iterate <feedback>}.
 */
public final class ImagineCommand {
	private ImagineCommand() {}

	private static final String SYSTEM_PROMPT = """
			You are a creative builder embedded in a player's Minecraft world. The player has
			invoked you with /imagine and a description of what they want.

			You have tools to place blocks, fill cuboid regions, query existing blocks, and
			chat with the player. Plan and execute a build that matches the description in a
			single turn — emit all the tool calls needed at once, then a brief chat summary.

			Constraints:
			- Use absolute world coordinates (Y is up). The player's position and a snapshot
			  of nearby blocks are provided below.
			- Build NEAR the player — center your work within ~10 blocks of their position
			  unless they ask for something specific.
			- Use real Minecraft block ids like "minecraft:oak_planks", "minecraft:stone",
			  "minecraft:glass". Do not invent block names.
			- fill_region is capped at 4096 blocks per call. Split larger structures into
			  several calls.
			- One chat call at the end is enough. Don't narrate every block.
			""";

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("imagine")
				.then(CommandManager.argument("description", StringArgumentType.greedyString())
						.executes(ImagineCommand::run)));
	}

	private static int run(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("/imagine must be run by a player (need a position)."));
			return 0;
		}
		ServerWorld world = source.getWorld();
		String description = StringArgumentType.getString(ctx, "description");

		Conversation conv = new Conversation();
		String snapshot = WorldSnapshot.forPlayer(player, world);
		conv.appendUserText(SYSTEM_PROMPT + "\n\n" + snapshot + "\n\nUser request: " + description);

		source.sendFeedback(
				() -> Text.literal("[/imagine] thinking…").formatted(Formatting.GRAY), false);

		LlmCraftMod.llm()
				.completeWithTools(conv, Tools.ALL)
				.whenComplete((result, err) -> source.getServer().execute(
						() -> handleResponse(player, world, conv, result, err)));
		return 1;
	}

	private static void handleResponse(ServerPlayerEntity player, ServerWorld world,
	                                    Conversation conv, CompletionResult result, Throwable err) {
		if (err != null) {
			LlmCraftMod.LOGGER.error("/imagine LLM call failed", err);
			player.sendMessage(Text.literal("[/imagine] LLM error: " + err.getMessage())
					.formatted(Formatting.RED), false);
			return;
		}

		// Append the assistant turn so /iterate sees the full history.
		conv.appendAssistant(result.content());

		// Execute every tool_use in order; collect tool_results to feed back into the conversation.
		PlayerSessions.Session session = PlayerSessions.get(player.getUuid());
		List<ContentBlock.ToolResult> toolResults = new ArrayList<>();
		int actionsRun = 0;
		int errors = 0;
		for (ContentBlock.ToolUse use : result.toolUses()) {
			ActionResult ar = dispatchSafely(use, world, player, session);
			actionsRun++;
			if (ar.isError()) errors++;
			toolResults.add(new ContentBlock.ToolResult(use.id(), ar.content(), ar.isError()));
		}
		if (!toolResults.isEmpty()) {
			conv.appendUserToolResults(toolResults);
		}
		session.lastImagine = conv;

		// Report.
		String text = result.text();
		if (!text.isBlank()) {
			player.sendMessage(Text.literal("[/imagine] " + text).formatted(Formatting.AQUA), false);
		}
		String summary = "[/imagine] ran " + actionsRun + " action" + (actionsRun == 1 ? "" : "s")
				+ (errors > 0 ? " (" + errors + " errored)" : "")
				+ (actionsRun > 0 ? ". /undo reverts the last." : ".");
		player.sendMessage(Text.literal(summary).formatted(Formatting.GRAY), false);
	}

	/** Centralised try/catch so a single bad action doesn't kill the whole batch. */
	private static ActionResult dispatchSafely(ContentBlock.ToolUse use, ServerWorld world,
	                                            ServerPlayerEntity player,
	                                            PlayerSessions.Session session) {
		try {
			Action action = Action.fromToolUse(use.name(), use.input());
			return ActionDispatcher.execute(action, world, player, session.undo);
		} catch (Exception e) {
			LlmCraftMod.LOGGER.warn("Failed to parse/run tool {}: {}", use.name(), e.getMessage());
			return ActionResult.error("Could not run tool '" + use.name() + "': " + e.getMessage());
		}
	}
}
