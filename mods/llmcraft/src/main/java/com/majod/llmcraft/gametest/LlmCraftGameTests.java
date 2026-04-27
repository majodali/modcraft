package com.majod.llmcraft.gametest;

import com.google.gson.JsonObject;
import com.majod.llmbridge.ContentBlock;
import com.majod.llmcraft.action.Action;
import com.majod.llmcraft.action.ActionDispatcher;
import com.majod.llmcraft.action.ActionResult;
import com.majod.llmcraft.action.UndoStack;
import com.majod.llmcraft.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * In-world gametests for LLMCraft's action layer. The /imagine command itself isn't
 * exercised here (it requires a live LLM); instead we feed pre-baked tool_use blocks
 * through {@link ActionDispatcher} as if the LLM had returned them. That's the same
 * code path with the network round-trip stubbed out.
 *
 * Loaded only when {@code -Dfabric-api.gametest} is set (via the {@code runGametest}
 * Gradle task).
 */
public class LlmCraftGameTests implements FabricGameTest {

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void echoStoneCanBeSpawnedAsItemEntity(TestContext context) {
		context.spawnItem(ModItems.ECHO_STONE, 1.0f, 2.0f, 1.0f);
		context.complete();
	}

	/**
	 * Round-trip a single place_block tool call through the action layer:
	 * dispatch → assert block in world → /undo replay → assert prior block restored.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void placeBlockActionRoundtripsThroughUndo(TestContext context) {
		BlockPos relative = new BlockPos(2, 1, 2);
		BlockPos absolute = context.getAbsolutePos(relative);

		// Build a tool_use as if the LLM had emitted it.
		JsonObject input = new JsonObject();
		input.addProperty("x", absolute.getX());
		input.addProperty("y", absolute.getY());
		input.addProperty("z", absolute.getZ());
		input.addProperty("material", "minecraft:diamond_block");
		ContentBlock.ToolUse use = new ContentBlock.ToolUse("toolu_test_1", "place_block", input);

		// Capture the prior block state so we can verify undo restored it (empty arena → air).
		var priorState = context.getWorld().getBlockState(absolute);
		context.expectBlock(Blocks.AIR, relative);

		// Dispatch through the same code path /imagine uses.
		Action action = Action.fromToolUse(use.name(), use.input());
		UndoStack undo = new UndoStack();
		ActionResult result = ActionDispatcher.execute(action, context.getWorld(), null, undo);

		if (result.isError()) {
			throw new AssertionError("Expected place_block to succeed, got error: " + result.content());
		}
		context.expectBlock(Blocks.DIAMOND_BLOCK, relative);
		if (undo.depth() != 1) {
			throw new AssertionError("Expected 1 undo entry, got " + undo.depth());
		}

		// Replay the inverse and confirm the original (air) block is back.
		UndoStack.Entry entry = undo.pop().orElseThrow();
		int reverted = ActionDispatcher.replayInverse(entry, context.getWorld());
		if (reverted != 1) {
			throw new AssertionError("Expected to revert 1 block, got " + reverted);
		}
		var afterUndo = context.getWorld().getBlockState(absolute);
		if (!afterUndo.equals(priorState)) {
			throw new AssertionError("Undo did not restore prior state. Was=" + priorState + " now=" + afterUndo);
		}
		context.complete();
	}

	/**
	 * fill_region across a 3×1×3 area: every block placed, undo reverts every block.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void fillRegionRoundtripsThroughUndo(TestContext context) {
		BlockPos relCorner1 = new BlockPos(1, 1, 1);
		BlockPos relCorner2 = new BlockPos(3, 1, 3);
		BlockPos absCorner1 = context.getAbsolutePos(relCorner1);
		BlockPos absCorner2 = context.getAbsolutePos(relCorner2);

		JsonObject input = new JsonObject();
		input.addProperty("from_x", absCorner1.getX());
		input.addProperty("from_y", absCorner1.getY());
		input.addProperty("from_z", absCorner1.getZ());
		input.addProperty("to_x",   absCorner2.getX());
		input.addProperty("to_y",   absCorner2.getY());
		input.addProperty("to_z",   absCorner2.getZ());
		input.addProperty("material", "minecraft:gold_block");

		Action action = Action.fromToolUse("fill_region", input);
		UndoStack undo = new UndoStack();
		ActionResult result = ActionDispatcher.execute(action, context.getWorld(), null, undo);

		if (result.isError()) {
			throw new AssertionError("fill_region errored: " + result.content());
		}
		// Every block in the 3x1x3 region should be gold.
		for (int dx = 0; dx <= 2; dx++) {
			for (int dz = 0; dz <= 2; dz++) {
				context.expectBlock(Blocks.GOLD_BLOCK, relCorner1.add(dx, 0, dz));
			}
		}

		// One undo entry should restore all 9.
		UndoStack.Entry entry = undo.pop().orElseThrow();
		int reverted = ActionDispatcher.replayInverse(entry, context.getWorld());
		if (reverted != 9) {
			throw new AssertionError("Expected 9 blocks reverted, got " + reverted);
		}
		for (int dx = 0; dx <= 2; dx++) {
			for (int dz = 0; dz <= 2; dz++) {
				context.expectBlock(Blocks.AIR, relCorner1.add(dx, 0, dz));
			}
		}
		context.complete();
	}

	/**
	 * Volume cap rejection — a 17×17×17 region (4913 blocks) exceeds the 4096-block
	 * cap, so dispatch must short-circuit with an error and place nothing.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void fillRegionRejectsOversizedVolume(TestContext context) {
		BlockPos rel = new BlockPos(1, 1, 1);
		BlockPos abs = context.getAbsolutePos(rel);

		JsonObject input = new JsonObject();
		input.addProperty("from_x", abs.getX());
		input.addProperty("from_y", abs.getY());
		input.addProperty("from_z", abs.getZ());
		input.addProperty("to_x", abs.getX() + 16);
		input.addProperty("to_y", abs.getY() + 16);
		input.addProperty("to_z", abs.getZ() + 16);
		input.addProperty("material", "minecraft:stone");

		Action action = Action.fromToolUse("fill_region", input);
		UndoStack undo = new UndoStack();
		ActionResult result = ActionDispatcher.execute(action, context.getWorld(), null, undo);

		if (!result.isError()) {
			throw new AssertionError("Expected oversized fill to error, got: " + result.content());
		}
		if (undo.depth() != 0) {
			throw new AssertionError("Failed action must not push to undo stack");
		}
		// No mutation should have happened.
		context.expectBlock(Blocks.AIR, rel);
		context.complete();
	}

	/**
	 * Unknown block id surfaces as an error result without partial side effects.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void placeBlockRejectsUnknownMaterial(TestContext context) {
		BlockPos rel = new BlockPos(1, 1, 1);
		BlockPos abs = context.getAbsolutePos(rel);

		JsonObject input = new JsonObject();
		input.addProperty("x", abs.getX());
		input.addProperty("y", abs.getY());
		input.addProperty("z", abs.getZ());
		input.addProperty("material", "minecraft:not_a_real_block");

		Action action = Action.fromToolUse("place_block", input);
		UndoStack undo = new UndoStack();
		ActionResult result = ActionDispatcher.execute(action, context.getWorld(), null, undo);

		if (!result.isError()) {
			throw new AssertionError("Unknown material should error, got: " + result.content());
		}
		if (undo.depth() != 0) {
			throw new AssertionError("Failed action must not push to undo stack");
		}
		context.expectBlock(Blocks.AIR, rel);
		context.complete();
	}

	/**
	 * query_blocks returns a non-empty palette summary for a non-trivial region.
	 * We don't assert exact contents (the empty arena could be air-only), just that
	 * the result isn't an error and contains the air entry the test arena will have.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void queryBlocksReportsPalette(TestContext context) {
		BlockPos abs1 = context.getAbsolutePos(new BlockPos(1, 1, 1));
		BlockPos abs2 = context.getAbsolutePos(new BlockPos(3, 1, 3));

		JsonObject input = new JsonObject();
		input.addProperty("from_x", abs1.getX());
		input.addProperty("from_y", abs1.getY());
		input.addProperty("from_z", abs1.getZ());
		input.addProperty("to_x", abs2.getX());
		input.addProperty("to_y", abs2.getY());
		input.addProperty("to_z", abs2.getZ());

		Action action = Action.fromToolUse("query_blocks", input);
		UndoStack undo = new UndoStack();
		ActionResult result = ActionDispatcher.execute(action, context.getWorld(), null, undo);

		if (result.isError()) {
			throw new AssertionError("query_blocks errored: " + result.content());
		}
		if (!result.content().contains("minecraft:air")) {
			throw new AssertionError("Expected palette to mention minecraft:air, got: " + result.content());
		}
		// Queries are read-only.
		if (undo.depth() != 0) {
			throw new AssertionError("Read-only action must not push to undo stack");
		}
		context.complete();
	}

	/**
	 * UndoStack evicts the oldest entry once depth hits MAX_DEPTH. Verifying the cap
	 * keeps memory bounded for long building sessions.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void undoStackEvictsOldestPastMaxDepth(TestContext context) {
		UndoStack stack = new UndoStack();
		for (int i = 0; i < UndoStack.MAX_DEPTH + 5; i++) {
			stack.push("entry " + i, List.of(
					new UndoStack.BlockChange(new BlockPos(i, 0, 0), Blocks.AIR.getDefaultState())));
		}
		if (stack.depth() != UndoStack.MAX_DEPTH) {
			throw new AssertionError("Expected stack capped at " + UndoStack.MAX_DEPTH
					+ " entries, got " + stack.depth());
		}
		// Top should be the most recent push (entry 20), not the oldest (entry 0).
		UndoStack.Entry top = stack.pop().orElseThrow();
		if (!top.label().equals("entry " + (UndoStack.MAX_DEPTH + 4))) {
			throw new AssertionError("Top of stack should be the most recent push, got: " + top.label());
		}
		context.complete();
	}
}
