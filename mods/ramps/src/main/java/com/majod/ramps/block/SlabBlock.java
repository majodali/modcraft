package com.majod.ramps.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import java.util.EnumMap;
import java.util.Map;

/**
 * A multi-orientation slab. One block class instance per (material × fraction) pair;
 * orientation is a state property.
 *
 * Outline shape == collision shape (slabs have no climbing surfaces, so the visual
 * cuboid IS what the player walks on / bumps into).
 *
 * Smart placement
 * ───────────────
 * Default rule: the slab's flat base touches the surface the player clicked on. So
 * clicking the top of a block places a FLOOR slab on top; clicking a wall places a
 * slab against that wall (its flat back glued to the wall).
 *
 * Side-on exception: when clicking a horizontal face of a FLOOR-orientation ramp
 * whose step base height matches this slab's fraction, place a FLOOR slab in the
 * adjacent cell instead. The slab's top sits flush with the ramp's step base, so
 * it visually extends the ramp's lower support box outward at the same level —
 * useful for building approaches and landings around a ramp.
 */
public class SlabBlock extends Block {
	public static final EnumProperty<SlabOrientation> ORIENTATION =
			EnumProperty.of("orientation", SlabOrientation.class);

	private final SlabFraction fraction;
	private final Map<SlabOrientation, VoxelShape> shapes;

	public SlabBlock(SlabFraction fraction, AbstractBlock.Settings settings) {
		super(settings);
		this.fraction = fraction;
		setDefaultState(getStateManager().getDefaultState().with(ORIENTATION, SlabOrientation.FLOOR));
		this.shapes = computeShapes(fraction.heightPx / 16.0);
	}

	public SlabFraction getFraction() {
		return fraction;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ORIENTATION);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapes.get(state.get(ORIENTATION));
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapes.get(state.get(ORIENTATION));
	}

	private static Map<SlabOrientation, VoxelShape> computeShapes(double h) {
		Map<SlabOrientation, VoxelShape> map = new EnumMap<>(SlabOrientation.class);
		map.put(SlabOrientation.FLOOR,      VoxelShapes.cuboid(0,     0,     0,     1,     h,     1    ));
		map.put(SlabOrientation.CEILING,    VoxelShapes.cuboid(0,     1 - h, 0,     1,     1,     1    ));
		map.put(SlabOrientation.WALL_NORTH, VoxelShapes.cuboid(0,     0,     0,     1,     1,     h    ));
		map.put(SlabOrientation.WALL_EAST,  VoxelShapes.cuboid(1 - h, 0,     0,     1,     1,     1    ));
		map.put(SlabOrientation.WALL_SOUTH, VoxelShapes.cuboid(0,     0,     1 - h, 1,     1,     1    ));
		map.put(SlabOrientation.WALL_WEST,  VoxelShapes.cuboid(0,     0,     0,     h,     1,     1    ));
		return map;
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction clickedSide = ctx.getSide();

		// Side-on exception (matching-height FLOOR ramp clicked on a horizontal face):
		// place a FLOOR slab in the new cell whose top is flush with the ramp's step base.
		if (clickedSide.getAxis().isHorizontal()) {
			BlockPos clickedPos = ctx.getBlockPos().offset(clickedSide.getOpposite());
			BlockState clickedState = ctx.getWorld().getBlockState(clickedPos);
			if (clickedState.getBlock() instanceof RampBlock ramp
					&& clickedState.get(RampBlock.ORIENTATION) == RampOrientation.FLOOR
					&& matchesRampStepBase(ramp)) {
				return getDefaultState().with(ORIENTATION, SlabOrientation.FLOOR);
			}
		}

		// Default: slab's flat base touches the clicked surface.
		return getDefaultState().with(ORIENTATION, orientationForClickedSide(clickedSide));
	}

	private static SlabOrientation orientationForClickedSide(Direction clickedSide) {
		return switch (clickedSide) {
			case UP    -> SlabOrientation.FLOOR;
			case DOWN  -> SlabOrientation.CEILING;
			case NORTH -> SlabOrientation.WALL_SOUTH;  // new cell is north of clicked → slab on its south wall
			case EAST  -> SlabOrientation.WALL_WEST;
			case SOUTH -> SlabOrientation.WALL_NORTH;
			case WEST  -> SlabOrientation.WALL_EAST;
		};
	}

	private boolean matchesRampStepBase(RampBlock ramp) {
		double rampBasePx = 16.0 * ramp.getStep() / ramp.getGrade();
		return Math.abs(rampBasePx - fraction.heightPx) < 0.01;
	}
}
