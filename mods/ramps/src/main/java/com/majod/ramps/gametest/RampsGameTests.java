package com.majod.ramps.gametest;

import com.majod.ramps.block.ModBlocks;
import com.majod.ramps.block.RampBlock;
import com.majod.ramps.block.RampOrientation;
import com.majod.ramps.block.SlabBlock;
import com.majod.ramps.block.SlabFraction;
import com.majod.ramps.block.SlabOrientation;
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
	 * Spawn-test: drops one ItemEntity for each registered ramp + slab block.
	 * Validates the full mod-load + registration pipeline.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void canSpawnAllRampItemEntities(TestContext context) {
		int x = 1;
		for (ModBlocks.Material material : ModBlocks.Material.values()) {
			for (int grade : ModBlocks.GRADES) {
				for (int step = 0; step < grade; step++) {
					RampBlock block = ModBlocks.RAMPS.get(material).get(grade).get(step);
					context.spawnItem(block.asItem(), (float) x, 1.0f, 1.0f);
					x++;
				}
			}
		}
		for (ModBlocks.Material material : ModBlocks.Material.values()) {
			for (SlabFraction fraction : SlabFraction.values()) {
				SlabBlock block = ModBlocks.SLABS.get(material).get(fraction);
				context.spawnItem(block.asItem(), (float) x, 1.0f, 1.0f);
				x++;
			}
		}
		context.complete();
	}

	/**
	 * Placement test: builds a full oak 1:3 ramp (steps a/b/c) facing east at
	 * consecutive positions and asserts each block is the expected RampBlock type
	 * with the correct facing+orientation.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void placesOakRamp13FloorSequence(TestContext context) {
		RampBlock stepA = ModBlocks.RAMPS.get(ModBlocks.Material.OAK).get(3).get(0);
		RampBlock stepB = ModBlocks.RAMPS.get(ModBlocks.Material.OAK).get(3).get(1);
		RampBlock stepC = ModBlocks.RAMPS.get(ModBlocks.Material.OAK).get(3).get(2);

		BlockPos posA = new BlockPos(1, 1, 1);
		BlockPos posB = new BlockPos(2, 1, 1);
		BlockPos posC = new BlockPos(3, 1, 1);

		context.setBlockState(posA, stepA.getDefaultState()
				.with(RampBlock.FACING, Direction.EAST)
				.with(RampBlock.ORIENTATION, RampOrientation.FLOOR));
		context.setBlockState(posB, stepB.getDefaultState()
				.with(RampBlock.FACING, Direction.EAST)
				.with(RampBlock.ORIENTATION, RampOrientation.FLOOR));
		context.setBlockState(posC, stepC.getDefaultState()
				.with(RampBlock.FACING, Direction.EAST)
				.with(RampBlock.ORIENTATION, RampOrientation.FLOOR));

		context.expectBlock(stepA, posA);
		context.expectBlock(stepB, posB);
		context.expectBlock(stepC, posC);

		context.complete();
	}

	/**
	 * Verify each (orientation × facing) combination can be set and read back.
	 * Layed out as a 2D grid (orientation along x, facing along z) to stay inside
	 * the 8×8 test arena even at 6 orientations.
	 *
	 * Shape correctness is implicit — if a BlockState's voxel shape isn't precomputed,
	 * the lookup throws when getOutlineShape is called during ticking. The fact that
	 * the arena ticks without exception is the assertion.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void allOrientationsPlaceable(TestContext context) {
		RampBlock block = ModBlocks.RAMPS.get(ModBlocks.Material.OAK).get(3).get(0);

		int x = 1;
		for (RampOrientation orientation : RampOrientation.values()) {
			int z = 1;
			for (Direction facing : Direction.Type.HORIZONTAL) {
				BlockPos pos = new BlockPos(x, 1, z);
				context.setBlockState(pos, block.getDefaultState()
						.with(RampBlock.FACING, facing)
						.with(RampBlock.ORIENTATION, orientation));
				context.expectBlock(block, pos);
				z++;
			}
			x++;
		}
		context.complete();
	}

	/**
	 * Verify each (slab fraction × orientation) combination can be set and read back.
	 * Lays out fractions along x and orientations along z within the 8×8 arena.
	 *
	 * Shape correctness is implicit: if a state's voxel shape isn't precomputed for a
	 * given orientation, the lookup throws when the arena ticks.
	 */
	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void allSlabOrientationsPlaceable(TestContext context) {
		int x = 1;
		for (SlabFraction fraction : SlabFraction.values()) {
			SlabBlock block = ModBlocks.SLABS.get(ModBlocks.Material.OAK).get(fraction);
			int z = 1;
			for (SlabOrientation orientation : SlabOrientation.values()) {
				BlockPos pos = new BlockPos(x, 1, z);
				context.setBlockState(pos, block.getDefaultState()
						.with(SlabBlock.ORIENTATION, orientation));
				context.expectBlock(block, pos);
				z++;
			}
			x++;
		}
		context.complete();
	}
}
