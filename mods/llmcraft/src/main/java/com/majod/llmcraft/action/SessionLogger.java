package com.majod.llmcraft.action;

import com.google.gson.GsonBuilder;
import com.majod.llmbridge.CompletionResult;
import com.majod.llmbridge.ContentBlock;
import com.majod.llmbridge.Conversation;
import com.majod.llmbridge.Message;
import com.majod.llmcraft.LlmCraftMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Per-command session logging. One human-readable text file per /imagine or /iterate
 * invocation, written under {@code <server-run-dir>/logs/llmcraft/}.
 *
 * Each file contains the full conversation as the LLM saw it (system prompt +
 * snapshot + user request, all assistant turns, all tool calls and results) plus a
 * final summary. Format is plain text with section dividers for grep-ability — not
 * machine-parseable on purpose. If you want structured logs, switch this to JSONL
 * later.
 *
 * Failures are swallowed and logged at WARN — a disk problem must never crash the
 * command itself.
 */
public final class SessionLogger {
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final DateTimeFormatter HUMAN_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final com.google.gson.Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

	private SessionLogger() {}

	/**
	 * Write a complete /imagine or /iterate transcript. Called once per command, after
	 * the LLM responds and tool calls have executed.
	 *
	 * @param server         the running MC server (for the run directory)
	 * @param player         the invoking player
	 * @param commandName    "imagine" or "iterate" (drives the filename prefix)
	 * @param userInput      the raw description/feedback the player typed
	 * @param conv           the full conversation as sent to the LLM (most recent turn last)
	 * @param result         the LLM's response (may be null on error)
	 * @param toolResults    the tool results we executed (may be empty)
	 * @param actionsRun     count of tool calls dispatched
	 * @param errors         count of those that returned isError
	 * @param errMessage     transport/parse error if the call failed; null on success
	 * @return absolute path of the written log, or null on failure
	 */
	public static Path log(MinecraftServer server, ServerPlayerEntity player, String commandName,
	                        String userInput, Conversation conv, CompletionResult result,
	                        List<ContentBlock.ToolResult> toolResults,
	                        int actionsRun, int errors, String errMessage) {
		try {
			Path dir = server.getRunDirectory().resolve("logs").resolve("llmcraft");
			Files.createDirectories(dir);
			LocalDateTime now = LocalDateTime.now();
			String filename = commandName + "_" + now.format(FILE_STAMP) + "_" + player.getName().getString() + ".log";
			Path file = dir.resolve(filename);

			StringBuilder sb = new StringBuilder();
			sb.append("=== /").append(commandName).append(' ').append(now.format(HUMAN_STAMP)).append(" ===\n");
			sb.append("Player: ").append(player.getName().getString())
					.append(" @ ").append(player.getBlockPos().toShortString()).append('\n');
			sb.append("Input:  ").append(userInput).append("\n\n");

			// Full conversation as sent to the LLM. Already includes the system prompt
			// + world snapshot inside the first user message.
			List<Message> messages = conv.messages();
			for (int i = 0; i < messages.size(); i++) {
				Message m = messages.get(i);
				sb.append("--- Message ").append(i + 1).append(" (role=").append(m.role()).append(") ---\n");
				appendContent(sb, m.content());
				sb.append('\n');
			}

			if (errMessage != null) {
				sb.append("=== ERROR ===\n").append(errMessage).append('\n');
			} else if (result != null) {
				sb.append("=== LLM RESPONSE (stop_reason=").append(result.stopReason()).append(") ===\n");
				appendContent(sb, result.content());
				sb.append('\n');

				if (!toolResults.isEmpty()) {
					sb.append("=== TOOL RESULTS ===\n");
					for (ContentBlock.ToolResult r : toolResults) {
						sb.append("[").append(r.toolUseId()).append("]")
								.append(r.isError() ? " ERROR" : " ok").append(":\n")
								.append(indent(r.content())).append("\n\n");
					}
				}
			}

			sb.append("=== SUMMARY ===\n");
			sb.append("Actions executed: ").append(actionsRun);
			if (errors > 0) sb.append(" (").append(errors).append(" errored)");
			sb.append('\n');

			Files.writeString(file, sb.toString());
			LlmCraftMod.LOGGER.info("/{} session log: {}", commandName, file);
			return file;
		} catch (IOException | RuntimeException e) {
			LlmCraftMod.LOGGER.warn("Failed to write session log for /{}: {}", commandName, e.getMessage());
			return null;
		}
	}

	private static void appendContent(StringBuilder sb, List<ContentBlock> blocks) {
		for (ContentBlock b : blocks) {
			switch (b) {
				case ContentBlock.Text t -> sb.append(t.text()).append('\n');
				case ContentBlock.ToolUse u -> {
					sb.append("[tool_use ").append(u.name()).append(" id=").append(u.id()).append("]\n");
					sb.append(indent(PRETTY.toJson(u.input()))).append('\n');
				}
				case ContentBlock.ToolResult r -> {
					sb.append("[tool_result ").append(r.toolUseId())
							.append(r.isError() ? " ERROR" : "").append("]\n");
					sb.append(indent(r.content())).append('\n');
				}
			}
		}
	}

	private static String indent(String s) {
		return s.lines().map(line -> "  " + line).reduce((a, b) -> a + "\n" + b).orElse("  ");
	}
}
