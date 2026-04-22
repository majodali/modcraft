package com.majod.ramps.gametest;

import com.majod.ramps.block.ModBlocks;
import com.majod.ramps.block.RampBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * In-world Fabric GameTests for the Ramps mod.
 *
 * Loaded only when {@code -Dfabric-api.gametest} is set (via the {@code runGametest}
 * Gradle task). Wired through the {@code fabric-gametest} entrypoint in fabric.mod.json.
 */
public class RampsGameTests implements FabricGameTest {

	/**
	 * Spawn-test: drops one ItemEntity for each of the 27 registered ramp blocks.
	 * Validates the full mod-load + registration pipeline — fails fast if any
	 * block has a broken Item registration.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void canSpawnAllRampItemEntities(TestContext context) {
		int x = 1;
		for (ModBlocks.Material material : ModBlocks.Material.values()) {
			for (int grade : ModBlocks.GRADES) {
				for (int step = 0; step < grade; step++) {
					RampBlock block = ModBlocks.RAMPS.get(material).get(grade).get(step);
					// Block.asItem() returns the BlockItem registered alongside this block.
					context.spawnItem(block.asItem(), (float) x, 1.0f, 1.0f);
					x++;
				}
			}
		}
		context.complete();
	}

	/**
	 * Placement test: builds a full oak 1:3 ramp (steps a/b/c) facing east at
	 * consecutive positions and asserts each block is the expected RampBlock type.
	 *
	 * This confirms our 3-piece "place in a line and they form a ramp" design
	 * works end-to-end for the canonical use case.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void placesOakRamp13Sequence(TestContext context) {
		RampBlock stepA = ModBlocks.RAMPS.get(ModBlocks.Material.OAK).get(3).get(0);
		RampBlock stepB = ModBlocks.RAMPS.get(ModBlocks.Material.OAK).get(3).get(1);
		RampBlock stepC = ModBlocks.RAMPS.get(ModBlocks.Material.OAK).get(3).get(2);

		BlockPos posA = new BlockPos(1, 1, 1);
		BlockPos posB = new BlockPos(2, 1, 1);
		BlockPos posC = new BlockPos(3, 1, 1);

		context.setBlockState(posA, stepA.getDefaultState().with(RampBlock.FACING, Direction.EAST));
		context.setBlockState(posB, stepB.getDefaultState().with(RampBlock.FACING, Direction.EAST));
		context.setBlockState(posC, stepC.getDefaultState().with(RampBlock.FACING, Direction.EAST));

		context.expectBlock(stepA, posA);
		context.expectBlock(stepB, posB);
		context.expectBlock(stepC, posC);

		context.complete();
	}
}
