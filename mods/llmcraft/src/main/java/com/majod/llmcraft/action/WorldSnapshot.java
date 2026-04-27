package com.majod.llmcraft.action;

import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the world-state context block that gets injected into the LLM's prompt at the
 * start of a session. Goal: enough information for the model to reason about position,
 * available space, and existing structure — without burning a million tokens.
 *
 * Strategy: report the player's position + facing, then a compact block-count summary
 * of the cube around them. The model uses absolute coordinates in its tool calls, so
 * giving it absolute coords up front matters.
 *
 * Not a render — for visual aesthetics the LLM has to call {@code query_blocks} on a
 * specific region. This is just orientation.
 */
public final class WorldSnapshot {

	/** Half-extent of the snapshot cube around the player, in blocks. */
	public static final int DEFAULT_RADIUS = 8;

	private WorldSnapshot() {}

	public static String forPlayer(ServerPlayerEntity player, ServerWorld world) {
		return forPlayer(player, world, DEFAULT_RADIUS);
	}

	public static String forPlayer(ServerPlayerEntity player, ServerWorld world, int radius) {
		BlockPos pos = player.getBlockPos();
		StringBuilder sb = new StringBuilder();
		sb.append("Current world state:\n");
		sb.append("- Player position: (").append(pos.getX()).append(", ").append(pos.getY())
				.append(", ").append(pos.getZ()).append(")\n");
		sb.append("- Player facing: ").append(player.getHorizontalFacing().asString()).append("\n");
		sb.append("- World: ").append(world.getRegistryKey().getValue()).append("\n");

		BlockPos lo = pos.add(-radius, -radius, -radius);
		BlockPos hi = pos.add(radius, radius, radius);
		sb.append("- Snapshot cube: ").append(posStr(lo)).append(" → ").append(posStr(hi))
				.append(" (").append(radius * 2 + 1).append("³ blocks)\n");

		sb.append("- Block palette in the snapshot cube:\n");
		Map<String, Integer> counts = paletteCounts(world, lo, hi);
		List<Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(counts.entrySet());
		sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		int cap = Math.min(ActionDispatcher.MAX_PALETTE_ENTRIES, sorted.size());
		for (int i = 0; i < cap; i++) {
			sb.append("    ").append(sorted.get(i).getKey()).append(": ")
					.append(sorted.get(i).getValue()).append("\n");
		}
		if (sorted.size() > cap) {
			int rest = 0;
			for (int i = cap; i < sorted.size(); i++) rest += sorted.get(i).getValue();
			sb.append("    ... + ").append(sorted.size() - cap).append(" more types totalling ")
					.append(rest).append(" blocks\n");
		}

		sb.append("\nUse `query_blocks` to inspect any specific region in finer detail. "
				+ "All coordinates in your tool calls are absolute world coordinates.");
		return sb.toString();
	}

	private static Map<String, Integer> paletteCounts(ServerWorld world, BlockPos lo, BlockPos hi) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		BlockPos.Mutable mut = new BlockPos.Mutable();
		for (int x = lo.getX(); x <= hi.getX(); x++) {
			for (int y = lo.getY(); y <= hi.getY(); y++) {
				for (int z = lo.getZ(); z <= hi.getZ(); z++) {
					mut.set(x, y, z);
					String id = Registries.BLOCK.getId(world.getBlockState(mut).getBlock()).toString();
					counts.merge(id, 1, Integer::sum);
				}
			}
		}
		return counts;
	}

	private static String posStr(BlockPos pos) {
		return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
	}
}
