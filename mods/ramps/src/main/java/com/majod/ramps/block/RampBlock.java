package com.majod.ramps.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import java.util.EnumMap;
import java.util.Map;

/**
 * One step-piece of a ramp. A 1:N ramp comprises N RampBlock instances with
 * step values 0..N-1 placed in a horizontal line; together they climb one
 * full block.
 *
 * Geometry of step S (0-indexed) of grade N:
 *   - solid support box from y=0 to y=S/N (omitted for step 0)
 *   - 8-slice wedge from y=S/N at the low end to y=(S+1)/N at the high end,
 *     climbing toward {@link #FACING}.
 *
 * Each step is a separate Block — no `step` blockstate property. This keeps
 * the per-block state space tiny (just FACING), which leaves room to add
 * an Orientation property later for sideways/wall variants without combinatorial
 * blow-up.
 */
public class RampBlock extends Block {
	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

	private final int grade;
	private final int step;
	private final Map<Direction, VoxelShape> shapesByFacing;

	public RampBlock(int grade, int step, AbstractBlock.Settings settings) {
		super(settings);
		if (grade < 2 || grade > 16) {
			throw new IllegalArgumentException("Ramp grade must be 2..16, got " + grade);
		}
		if (step < 0 || step >= grade) {
			throw new IllegalArgumentException("Ramp step " + step + " out of range for grade " + grade);
		}
		this.grade = grade;
		this.step = step;
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
		this.shapesByFacing = computeShapes(grade, step);
	}

	public int getGrade() { return grade; }
	public int getStep() { return step; }

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapesByFacing.get(state.get(FACING));
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	private static Map<Direction, VoxelShape> computeShapes(int grade, int step) {
		Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
		for (Direction facing : Direction.Type.HORIZONTAL) {
			map.put(facing, buildShape(grade, step, facing));
		}
		return map;
	}

	private static VoxelShape buildShape(int grade, int step, Direction facing) {
		final double baseHeightPx = 16.0 * step / grade;
		final double topHeightPx = 16.0 * (step + 1) / grade;

		VoxelShape shape = VoxelShapes.empty();

		if (baseHeightPx > 0) {
			shape = VoxelShapes.union(shape,
					VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, baseHeightPx / 16.0, 1.0));
		}

		final int slices = 8;
		final double minSliceAboveBasePx = 1.0;
		for (int i = 0; i < slices; i++) {
			double tFromLow = i / (double)(slices - 1);
			double sliceTopPx = baseHeightPx + minSliceAboveBasePx
					+ (topHeightPx - baseHeightPx - minSliceAboveBasePx) * tFromLow;

			double slicePosLowPx = i * 2.0;
			double slicePosHighPx = (i + 1) * 2.0;

			VoxelShape slice = switch (facing) {
				case NORTH -> VoxelShapes.cuboid(
						0.0, baseHeightPx / 16.0, (16 - slicePosHighPx) / 16.0,
						1.0, sliceTopPx / 16.0, (16 - slicePosLowPx) / 16.0);
				case SOUTH -> VoxelShapes.cuboid(
						0.0, baseHeightPx / 16.0, slicePosLowPx / 16.0,
						1.0, sliceTopPx / 16.0, slicePosHighPx / 16.0);
				case EAST -> VoxelShapes.cuboid(
						slicePosLowPx / 16.0, baseHeightPx / 16.0, 0.0,
						slicePosHighPx / 16.0, sliceTopPx / 16.0, 1.0);
				case WEST -> VoxelShapes.cuboid(
						(16 - slicePosHighPx) / 16.0, baseHeightPx / 16.0, 0.0,
						(16 - slicePosLowPx) / 16.0, sliceTopPx / 16.0, 1.0);
				default -> throw new IllegalStateException("Non-horizontal facing: " + facing);
			};
			shape = VoxelShapes.union(shape, slice);
		}
		return shape;
	}
}
