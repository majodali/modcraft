package com.majod.llmcraft.action;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Per-player undo history. Each {@link Entry} captures the block state at every position
 * a single dispatched action mutated, so {@code /undo} can replay them in place.
 *
 * Bounded depth: oldest entries are evicted past {@link #MAX_DEPTH} so a long building
 * session doesn't accumulate unbounded memory. 16 entries is enough for "oh that wasn't
 * what I meant — try again" workflows; we can raise it later if 16 turns out tight.
 *
 * Not thread-safe. All access happens on the server thread.
 */
public final class UndoStack {

	/** Per-position pre-mutation snapshot. */
	public record BlockChange(BlockPos pos, BlockState state) {}

	/** A single undo entry — typically the inverse of one dispatched action. */
	public record Entry(String label, List<BlockChange> changes) {
		public Entry {
			changes = List.copyOf(changes);
		}
	}

	public static final int MAX_DEPTH = 16;

	private final Deque<Entry> stack = new ArrayDeque<>(MAX_DEPTH);

	public void push(String label, List<BlockChange> changes) {
		if (changes.isEmpty()) return;  // no-op actions don't pollute the stack
		if (stack.size() >= MAX_DEPTH) {
			stack.removeLast();  // evict oldest
		}
		stack.push(new Entry(label, new ArrayList<>(changes)));
	}

	public Optional<Entry> pop() {
		return stack.isEmpty() ? Optional.empty() : Optional.of(stack.pop());
	}

	public int depth() {
		return stack.size();
	}

	public void clear() {
		stack.clear();
	}
}
