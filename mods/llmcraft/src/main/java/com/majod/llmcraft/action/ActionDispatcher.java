package com.majod.llmcraft.action;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes {@link Action}s server-side, capturing inverses into an {@link UndoStack} and
 * returning a string result to feed back to the LLM.
 *
 * Caps:
 * <ul>
 *   <li>{@link #MAX_REGION_VOLUME} — max blocks touched in a single fill or query.
 *       Rejects oversized regions before any mutation. Larger structures must be built
 *       across multiple tool calls.</li>
 *   <li>{@link #MAX_PALETTE_ENTRIES} — query_blocks reports up to N distinct block
 *       types; remaining types are aggregated under "other" so the response stays
 *       compact even for diverse regions.</li>
 * </ul>
 *
 * All world mutations must run on the server thread (the caller's responsibility — typically
 * via {@code MinecraftServer.execute(...)}).
 */
public final class ActionDispatcher {

	public static final int MAX_REGION_VOLUME = 4096;
	public static final int MAX_PALETTE_ENTRIES = 16;

	private ActionDispatcher() {}

	public static ActionResult execute(Action action, ServerWorld world, ServerPlayerEntity player,
	                                    UndoStack undo) {
		try {
			return switch (action) {
				case Action.PlaceBlock a -> placeBlock(a, world, undo);
				case Action.FillRegion a -> fillRegion(a, world, undo);
				case Action.QueryBlocks a -> queryBlocks(a, world);
				case Action.Chat a -> chat(a, player);
			};
		} catch (Exception e) {
			return ActionResult.error("Action failed: " + e.getMessage());
		}
	}

	// ─── place_block ────────────────────────────────────────────────────────────

	private static ActionResult placeBlock(Action.PlaceBlock a, ServerWorld world, UndoStack undo) {
		BlockState target = resolveBlock(a.material());
		if (target == null) return ActionResult.error("Unknown block id: " + a.material());

		BlockPos pos = new BlockPos(a.x(), a.y(), a.z());
		if (!isInsideWorld(world, pos)) {
			return ActionResult.error("Position " + posStr(pos) + " is outside the world's build limits");
		}
		BlockState prior = world.getBlockState(pos);
		world.setBlockState(pos, target);
		undo.push("place_block " + a.material() + " @ " + posStr(pos),
				List.of(new UndoStack.BlockChange(pos, prior)));

		return ActionResult.ok("Placed " + a.material() + " at " + posStr(pos));
	}

	// ─── fill_region ────────────────────────────────────────────────────────────

	private static ActionResult fillRegion(Action.FillRegion a, ServerWorld world, UndoStack undo) {
		BlockState target = resolveBlock(a.material());
		if (target == null) return ActionResult.error("Unknown block id: " + a.material());

		// Normalise so from <= to on every axis, regardless of which corner the LLM gave us first.
		int x1 = Math.min(a.fromX(), a.toX()), x2 = Math.max(a.fromX(), a.toX());
		int y1 = Math.min(a.fromY(), a.toY()), y2 = Math.max(a.fromY(), a.toY());
		int z1 = Math.min(a.fromZ(), a.toZ()), z2 = Math.max(a.fromZ(), a.toZ());

		long volume = (long)(x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
		if (volume > MAX_REGION_VOLUME) {
			return ActionResult.error("Region volume " + volume + " exceeds the "
					+ MAX_REGION_VOLUME + "-block limit. Split the build into smaller chunks.");
		}

		List<UndoStack.BlockChange> changes = new ArrayList<>((int) volume);
		BlockPos.Mutable mut = new BlockPos.Mutable();
		int placed = 0;
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					mut.set(x, y, z);
					if (!isInsideWorld(world, mut)) continue;
					BlockState prior = world.getBlockState(mut);
					if (prior.equals(target)) continue;  // no-op skip; keeps undo small
					BlockPos imm = mut.toImmutable();
					changes.add(new UndoStack.BlockChange(imm, prior));
					world.setBlockState(imm, target);
					placed++;
				}
			}
		}
		undo.push("fill_region " + a.material() + " " + posStr(new BlockPos(x1, y1, z1))
				+ " → " + posStr(new BlockPos(x2, y2, z2)), changes);

		return ActionResult.ok("Filled " + placed + " blocks of " + a.material()
				+ " in region " + posStr(new BlockPos(x1, y1, z1)) + " → " + posStr(new BlockPos(x2, y2, z2))
				+ (placed < volume ? " (" + (volume - placed) + " skipped: already that material or out of bounds)" : ""));
	}

	// ─── query_blocks ───────────────────────────────────────────────────────────

	private static ActionResult queryBlocks(Action.QueryBlocks a, ServerWorld world) {
		int x1 = Math.min(a.fromX(), a.toX()), x2 = Math.max(a.fromX(), a.toX());
		int y1 = Math.min(a.fromY(), a.toY()), y2 = Math.max(a.fromY(), a.toY());
		int z1 = Math.min(a.fromZ(), a.toZ()), z2 = Math.max(a.fromZ(), a.toZ());

		long volume = (long)(x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
		if (volume > MAX_REGION_VOLUME) {
			return ActionResult.error("Region volume " + volume + " exceeds the "
					+ MAX_REGION_VOLUME + "-block limit.");
		}

		Map<String, Integer> counts = new LinkedHashMap<>();
		BlockPos.Mutable mut = new BlockPos.Mutable();
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					mut.set(x, y, z);
					String id = Registries.BLOCK.getId(world.getBlockState(mut).getBlock()).toString();
					counts.merge(id, 1, Integer::sum);
				}
			}
		}

		// Sort by count desc; cap palette size with an "other" rollup.
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
		sorted.sort((a1, a2) -> Integer.compare(a2.getValue(), a1.getValue()));
		StringBuilder summary = new StringBuilder();
		summary.append("Region ").append(posStr(new BlockPos(x1, y1, z1)))
				.append(" → ").append(posStr(new BlockPos(x2, y2, z2)))
				.append(" (").append(volume).append(" blocks):\n");
		int shown = Math.min(MAX_PALETTE_ENTRIES, sorted.size());
		for (int i = 0; i < shown; i++) {
			summary.append("  ").append(sorted.get(i).getKey())
					.append(": ").append(sorted.get(i).getValue()).append("\n");
		}
		if (sorted.size() > shown) {
			int otherTotal = 0;
			for (int i = shown; i < sorted.size(); i++) otherTotal += sorted.get(i).getValue();
			summary.append("  ... + ").append(sorted.size() - shown).append(" more types totalling ")
					.append(otherTotal).append(" blocks\n");
		}
		return ActionResult.ok(summary.toString().stripTrailing());
	}

	// ─── chat ───────────────────────────────────────────────────────────────────

	private static ActionResult chat(Action.Chat a, ServerPlayerEntity player) {
		player.sendMessage(Text.literal("[LLM] " + a.message()).formatted(Formatting.AQUA), false);
		return ActionResult.ok("Message sent.");
	}

	// ─── undo replay ────────────────────────────────────────────────────────────

	/**
	 * Restore each captured (pos, state) pair from an undo entry. Returns the number
	 * of blocks reverted. Caller is responsible for popping the entry off the stack.
	 */
	public static int replayInverse(UndoStack.Entry entry, ServerWorld world) {
		int count = 0;
		for (UndoStack.BlockChange change : entry.changes()) {
			world.setBlockState(change.pos(), change.state());
			count++;
		}
		return count;
	}

	// ─── helpers ────────────────────────────────────────────────────────────────

	private static BlockState resolveBlock(String materialId) {
		Identifier id = Identifier.tryParse(materialId);
		if (id == null) return null;
		Block block = Registries.BLOCK.get(id);
		// Registries.BLOCK.get(unknown) returns AIR, so explicitly check the round-trip.
		if (!Registries.BLOCK.getId(block).equals(id)) return null;
		return block.getDefaultState();
	}

	private static boolean isInsideWorld(ServerWorld world, BlockPos pos) {
		// 1.21.4 yarn renamed the exclusive getTopY() to getTopYInclusive() (inclusive bound).
		return pos.getY() >= world.getBottomY() && pos.getY() <= world.getTopYInclusive()
				&& world.getWorldBorder().contains(pos);
	}

	private static String posStr(BlockPos pos) {
		return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
	}
}
