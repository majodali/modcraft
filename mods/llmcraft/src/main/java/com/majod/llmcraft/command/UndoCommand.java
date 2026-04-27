package com.majod.llmcraft.command;

import com.majod.llmcraft.action.ActionDispatcher;
import com.majod.llmcraft.action.PlayerSessions;
import com.majod.llmcraft.action.UndoStack;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;

/**
 * {@code /undo} — pops the most recent action off the player's undo stack and replays
 * its captured inverse blocks. No LLM call.
 *
 * Bounded history: the stack keeps at most {@link UndoStack#MAX_DEPTH} entries (older
 * ones are evicted). Once an entry is popped it cannot be redone — there's no
 * {@code /redo}; if a feature ever needs that, the dispatcher would have to capture
 * the post-state too.
 */
public final class UndoCommand {
	private UndoCommand() {}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("undo").executes(UndoCommand::run));
	}

	private static int run(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("/undo must be run by a player."));
			return 0;
		}
		ServerWorld world = source.getWorld();
		PlayerSessions.Session session = PlayerSessions.get(player.getUuid());
		Optional<UndoStack.Entry> top = session.undo.pop();
		if (top.isEmpty()) {
			source.sendFeedback(() -> Text.literal("[/undo] Nothing to undo.")
					.formatted(Formatting.GRAY), false);
			return 0;
		}
		UndoStack.Entry entry = top.get();
		int reverted = ActionDispatcher.replayInverse(entry, world);
		source.sendFeedback(() -> Text.literal(
				"[/undo] Reverted '" + entry.label() + "' (" + reverted + " block"
						+ (reverted == 1 ? "" : "s") + "). " + session.undo.depth() + " left in history.")
				.formatted(Formatting.GRAY), false);
		return 1;
	}
}
