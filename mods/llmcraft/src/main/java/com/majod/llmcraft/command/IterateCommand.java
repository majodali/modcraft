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
 * {@code /iterate <feedback>} — refines the most recent {@code /imagine} build by
 * appending the player's feedback to that conversation and re-running the LLM with
 * the same tool set.
 *
 * Like {@code /imagine}, this is a single-turn call: the model sees the previous
 * tool calls, their results, and the new feedback, then emits another batch of tool
 * uses. Each iterate adds one entry to the undo stack, so {@code /undo} steps back
 * one iteration at a time.
 *
 * Errors out cleanly if the player hasn't run {@code /imagine} yet this session.
 */
public final class IterateCommand {
	private IterateCommand() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("iterate")
				.then(CommandManager.argument("feedback", StringArgumentType.greedyString())
						.executes(IterateCommand::run)));
	}

	private static int run(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("/iterate must be run by a player."));
			return 0;
		}
		ServerWorld world = source.getWorld();
		String feedback = StringArgumentType.getString(ctx, "feedback");

		PlayerSessions.Session session = PlayerSessions.get(player.getUuid());
		Conversation conv = session.lastImagine;
		if (conv == null) {
			source.sendError(Text.literal(
					"/iterate needs a previous /imagine session. Run /imagine first."));
			return 0;
		}
		conv.appendUserText(feedback);

		source.sendFeedback(
				() -> Text.literal("[/iterate] thinking…").formatted(Formatting.GRAY), false);

		LlmCraftMod.llm()
				.completeWithTools(conv, Tools.ALL)
				.whenComplete((result, err) -> source.getServer().execute(
						() -> handleResponse(player, world, conv, session, result, err)));
		return 1;
	}

	private static void handleResponse(ServerPlayerEntity player, ServerWorld world,
	                                    Conversation conv, PlayerSessions.Session session,
	                                    CompletionResult result, Throwable err) {
		if (err != null) {
			LlmCraftMod.LOGGER.error("/iterate LLM call failed", err);
			player.sendMessage(Text.literal("[/iterate] LLM error: " + err.getMessage())
					.formatted(Formatting.RED), false);
			return;
		}
		conv.appendAssistant(result.content());

		List<ContentBlock.ToolResult> toolResults = new ArrayList<>();
		int actionsRun = 0;
		int errors = 0;
		for (ContentBlock.ToolUse use : result.toolUses()) {
			ActionResult ar;
			try {
				Action action = Action.fromToolUse(use.name(), use.input());
				ar = ActionDispatcher.execute(action, world, player, session.undo);
			} catch (Exception e) {
				ar = ActionResult.error("Could not run tool '" + use.name() + "': " + e.getMessage());
			}
			actionsRun++;
			if (ar.isError()) errors++;
			toolResults.add(new ContentBlock.ToolResult(use.id(), ar.content(), ar.isError()));
		}
		if (!toolResults.isEmpty()) {
			conv.appendUserToolResults(toolResults);
		}

		String text = result.text();
		if (!text.isBlank()) {
			player.sendMessage(Text.literal("[/iterate] " + text).formatted(Formatting.AQUA), false);
		}
		String summary = "[/iterate] ran " + actionsRun + " action" + (actionsRun == 1 ? "" : "s")
				+ (errors > 0 ? " (" + errors + " errored)" : "")
				+ (actionsRun > 0 ? ". /undo reverts the last." : ".");
		player.sendMessage(Text.literal(summary).formatted(Formatting.GRAY), false);
	}
}
