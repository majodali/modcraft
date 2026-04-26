package com.majod.ramps.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
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

import java.util.HashMap;
import java.util.Map;

/**
 * One step-piece of a ramp. A 1:N ramp is composed of N RampBlock instances with
 * step values 0..N-1 placed in a line; together they climb one full block.
 *
 * Visual vs collision are DECOUPLED:
 *   - {@link #getOutlineShape} returns the detailed 16-slice wedge (matches the model)
 *   - {@link #getCollisionShape} returns a 2-box approximation positioned at 1/4 and 3/4
 *     of the wedge height. This avoids MC's auto-step-up firing on every micro-slice,
 *     which causes noticeable slowdown when walking up ramps.
 *
 * Collision box heights (per step) are the average visual heights of the two halves
 * of the wedge, so player feet are within 1/8 of wedge height of the visual surface
 * anywhere on the ramp.
 */
public class RampBlock extends Block {
	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<RampOrientation> ORIENTATION = EnumProperty.of("orientation", RampOrientation.class);

	/** Number of visible slices per wedge. Must match the model-template element count. */
	public static final int SLICES = 16;

	private final int grade;
	private final int step;
	private final Map<BlockState, VoxelShape> outlineShapes;
	private final Map<BlockState, VoxelShape> collisionShapes;

	public RampBlock(int grade, int step, AbstractBlock.Settings settings) {
		super(settings);
		if (grade < 1 || grade > 16) {
			throw new IllegalArgumentException("Ramp grade must be 1..16, got " + grade);
		}
		if (step < 0 || step >= grade) {
			throw new IllegalArgumentException("Ramp step " + step + " out of range for grade " + grade);
		}
		this.grade = grade;
		this.step = step;
		setDefaultState(getStateManager().getDefaultState()
				.with(FACING, Direction.NORTH)
				.with(ORIENTATION, RampOrientation.FLOOR));
		this.outlineShapes = computeShapes(this::buildOutlineShape);
		this.collisionShapes = computeShapes(this::buildCollisionShape);
	}

	public int getGrade() { return grade; }
	public int getStep() { return step; }

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		builder.add(ORIENTATION);
	}

	/** Smart placement: match neighbor if clicking a ramp, otherwise infer from click face + look. */
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockPos clickedPos = ctx.getBlockPos().offset(ctx.getSide().getOpposite());
		BlockState clickedState = ctx.getWorld().getBlockState(clickedPos);

		if (clickedState.getBlock() instanceof RampBlock) {
			return getDefaultState()
					.with(FACING, clickedState.get(FACING))
					.with(ORIENTATION, clickedState.get(ORIENTATION));
		}

		Direction clickedSide = ctx.getSide();
		Direction horizontalFacing = ctx.getHorizontalPlayerFacing();
		PlayerEntity player = ctx.getPlayer();
		float pitch = player == null ? 0f : player.getPitch();

		return switch (clickedSide) {
			case UP -> getDefaultState().with(FACING, horizontalFacing).with(ORIENTATION, RampOrientation.FLOOR);
			case DOWN -> getDefaultState().with(FACING, horizontalFacing).with(ORIENTATION, RampOrientation.CEILING);
			default -> {
				Direction wallSide = clickedSide.getOpposite();
				if (pitch < -20f) {
					yield getDefaultState().with(FACING, wallSide).with(ORIENTATION, RampOrientation.WALL_UP);
				} else if (pitch > 20f) {
					yield getDefaultState().with(FACING, wallSide).with(ORIENTATION, RampOrientation.WALL_DOWN);
				} else {
					RampOrientation orient = (leftOf(horizontalFacing) == wallSide)
							? RampOrientation.HORIZONTAL_LEFT
							: RampOrientation.HORIZONTAL_RIGHT;
					yield getDefaultState().with(FACING, horizontalFacing).with(ORIENTATION, orient);
				}
			}
		};
	}

	private static Direction leftOf(Direction facing) {
		return switch (facing) {
			case NORTH -> Direction.WEST;
			case EAST -> Direction.NORTH;
			case SOUTH -> Direction.EAST;
			case WEST -> Direction.SOUTH;
			default -> throw new IllegalArgumentException("Non-horizontal facing: " + facing);
		};
	}

	// onUse intentionally NOT overridden. See RampWrenchItem for orientation/facing adjustment.

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return outlineShapes.get(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return collisionShapes.get(state);
	}

	@Override
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@FunctionalInterface
	private interface ShapeBuilder {
		VoxelShape build(RampOrientation orientation, Direction facing);
	}

	private Map<BlockState, VoxelShape> computeShapes(ShapeBuilder builder) {
		Map<BlockState, VoxelShape> map = new HashMap<>();
		for (RampOrientation orientation : RampOrientation.values()) {
			for (Direction facing : Direction.Type.HORIZONTAL) {
				BlockState key = getDefaultState().with(FACING, facing).with(ORIENTATION, orientation);
				map.put(key, builder.build(orientation, facing));
			}
		}
		return map;
	}

	// ═══════════════════════════════════════════════════════════════════════════════
	// Outline shapes — 16-slice wedge matching the model exactly
	// ═══════════════════════════════════════════════════════════════════════════════

	private VoxelShape buildOutlineShape(RampOrientation orientation, Direction facing) {
		return switch (orientation) {
			case FLOOR -> buildFloorOutline(grade, step, facing);
			case CEILING -> buildCeilingOutline(grade, step, facing);
			case WALL_UP -> buildWallOutline(grade, step, facing, false);
			case WALL_DOWN -> buildWallOutline(grade, step, facing, true);
			case HORIZONTAL_LEFT -> buildHorizontalOutline(grade, step, facing, false);
			case HORIZONTAL_RIGHT -> buildHorizontalOutline(grade, step, facing, true);
		};
	}

	private static VoxelShape buildFloorOutline(int grade, int step, Direction facing) {
		final double basePx = 16.0 * step / grade;
		final double wedgePx = 16.0 / grade;
		final double sliceDepthPx = 16.0 / SLICES;
		VoxelShape shape = VoxelShapes.empty();
		if (basePx > 0) {
			shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0, 0, 0, 1, basePx / 16.0, 1));
		}
		for (int i = 0; i < SLICES; i++) {
			double topPx = basePx + wedgePx * (i + 1) / SLICES;
			double loPx = i * sliceDepthPx;
			double hiPx = (i + 1) * sliceDepthPx;
			shape = VoxelShapes.union(shape, switch (facing) {
				case NORTH -> VoxelShapes.cuboid(0, basePx / 16.0, (16 - hiPx) / 16.0, 1, topPx / 16.0, (16 - loPx) / 16.0);
				case SOUTH -> VoxelShapes.cuboid(0, basePx / 16.0, loPx / 16.0, 1, topPx / 16.0, hiPx / 16.0);
				case EAST -> VoxelShapes.cuboid(loPx / 16.0, basePx / 16.0, 0, hiPx / 16.0, topPx / 16.0, 1);
				case WEST -> VoxelShapes.cuboid((16 - hiPx) / 16.0, basePx / 16.0, 0, (16 - loPx) / 16.0, topPx / 16.0, 1);
				default -> throw new IllegalStateException();
			});
		}
		return shape;
	}

	private static VoxelShape buildCeilingOutline(int grade, int step, Direction facing) {
		final double basePx = 16.0 * step / grade;
		final double wedgePx = 16.0 / grade;
		final double sliceDepthPx = 16.0 / SLICES;
		VoxelShape shape = VoxelShapes.empty();
		if (basePx > 0) {
			shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0, (16 - basePx) / 16.0, 0, 1, 1, 1));
		}
		for (int i = 0; i < SLICES; i++) {
			double topPx = basePx + wedgePx * (i + 1) / SLICES;
			double loPx = i * sliceDepthPx;
			double hiPx = (i + 1) * sliceDepthPx;
			double yMin = (16 - topPx) / 16.0;
			double yMax = (16 - basePx) / 16.0;
			shape = VoxelShapes.union(shape, switch (facing) {
				case NORTH -> VoxelShapes.cuboid(0, yMin, (16 - hiPx) / 16.0, 1, yMax, (16 - loPx) / 16.0);
				case SOUTH -> VoxelShapes.cuboid(0, yMin, loPx / 16.0, 1, yMax, hiPx / 16.0);
				case EAST -> VoxelShapes.cuboid(loPx / 16.0, yMin, 0, hiPx / 16.0, yMax, 1);
				case WEST -> VoxelShapes.cuboid((16 - hiPx) / 16.0, yMin, 0, (16 - loPx) / 16.0, yMax, 1);
				default -> throw new IllegalStateException();
			});
		}
		return shape;
	}

	private static VoxelShape buildWallOutline(int grade, int step, Direction facing, boolean thickAtBottom) {
		final double basePx = 16.0 * step / grade;
		final double wedgePx = 16.0 / grade;
		final double sliceDepthPx = 16.0 / SLICES;
		VoxelShape shape = VoxelShapes.empty();
		if (basePx > 0) {
			shape = VoxelShapes.union(shape, wallSupportShape(facing, basePx));
		}
		for (int i = 0; i < SLICES; i++) {
			double sPx = basePx + wedgePx * (i + 1) / SLICES;
			double yLoPx = thickAtBottom ? (SLICES - 1 - i) * sliceDepthPx : i * sliceDepthPx;
			double yHiPx = yLoPx + sliceDepthPx;
			shape = VoxelShapes.union(shape, wallSliceShape(facing, basePx, sPx, yLoPx, yHiPx));
		}
		return shape;
	}

	private static VoxelShape wallSupportShape(Direction facing, double baseDepthPx) {
		double bd = baseDepthPx / 16.0;
		return switch (facing) {
			case NORTH -> VoxelShapes.cuboid(0, 0, 0, 1, 1, bd);
			case EAST  -> VoxelShapes.cuboid(1 - bd, 0, 0, 1, 1, 1);
			case SOUTH -> VoxelShapes.cuboid(0, 0, 1 - bd, 1, 1, 1);
			case WEST  -> VoxelShapes.cuboid(0, 0, 0, bd, 1, 1);
			default -> throw new IllegalStateException();
		};
	}

	private static VoxelShape wallSliceShape(Direction facing, double basePx, double slicePx,
	                                          double yLoPx, double yHiPx) {
		double bd = basePx / 16.0;
		double sd = slicePx / 16.0;
		double yl = yLoPx / 16.0;
		double yh = yHiPx / 16.0;
		return switch (facing) {
			case NORTH -> VoxelShapes.cuboid(0, yl, bd, 1, yh, sd);
			case EAST  -> VoxelShapes.cuboid(1 - sd, yl, 0, 1 - bd, yh, 1);
			case SOUTH -> VoxelShapes.cuboid(0, yl, 1 - sd, 1, yh, 1 - bd);
			case WEST  -> VoxelShapes.cuboid(bd, yl, 0, sd, yh, 1);
			default -> throw new IllegalStateException();
		};
	}

	private static VoxelShape buildHorizontalOutline(int grade, int step, Direction facing, boolean rightHanded) {
		final double basePx = 16.0 * step / grade;
		final double wedgePx = 16.0 / grade;
		final double sliceDepthPx = 16.0 / SLICES;
		VoxelShape shape = VoxelShapes.empty();
		if (basePx > 0) {
			shape = VoxelShapes.union(shape, horizontalSupportShape(facing, rightHanded, basePx));
		}
		for (int i = 0; i < SLICES; i++) {
			double sPx = basePx + wedgePx * (i + 1) / SLICES;
			double loPx = i * sliceDepthPx;
			double hiPx = (i + 1) * sliceDepthPx;
			shape = VoxelShapes.union(shape, horizontalSliceShape(facing, rightHanded, basePx, sPx, loPx, hiPx));
		}
		return shape;
	}

	private static VoxelShape horizontalSupportShape(Direction facing, boolean rightHanded, double basePx) {
		double d = basePx / 16.0;
		return switch (facing) {
			case NORTH -> rightHanded ? VoxelShapes.cuboid(1 - d, 0, 0, 1, 1, 1) : VoxelShapes.cuboid(0, 0, 0, d, 1, 1);
			case EAST -> rightHanded ? VoxelShapes.cuboid(0, 0, 1 - d, 1, 1, 1) : VoxelShapes.cuboid(0, 0, 0, 1, 1, d);
			case SOUTH -> rightHanded ? VoxelShapes.cuboid(0, 0, 0, d, 1, 1) : VoxelShapes.cuboid(1 - d, 0, 0, 1, 1, 1);
			case WEST -> rightHanded ? VoxelShapes.cuboid(0, 0, 0, 1, 1, d) : VoxelShapes.cuboid(0, 0, 1 - d, 1, 1, 1);
			default -> throw new IllegalStateException();
		};
	}

	private static VoxelShape horizontalSliceShape(Direction facing, boolean rightHanded,
	                                                double basePx, double slicePx,
	                                                double slicePosLoPx, double slicePosHiPx) {
		double bd = basePx / 16.0;
		double sd = slicePx / 16.0;
		double pl = slicePosLoPx / 16.0;
		double ph = slicePosHiPx / 16.0;
		return switch (facing) {
			case NORTH -> rightHanded
					? VoxelShapes.cuboid(1 - sd, 0, 1 - ph, 1 - bd, 1, 1 - pl)
					: VoxelShapes.cuboid(bd, 0, 1 - ph, sd, 1, 1 - pl);
			case EAST -> rightHanded
					? VoxelShapes.cuboid(pl, 0, 1 - sd, ph, 1, 1 - bd)
					: VoxelShapes.cuboid(pl, 0, bd, ph, 1, sd);
			case SOUTH -> rightHanded
					? VoxelShapes.cuboid(bd, 0, pl, sd, 1, ph)
					: VoxelShapes.cuboid(1 - sd, 0, pl, 1 - bd, 1, ph);
			case WEST -> rightHanded
					? VoxelShapes.cuboid(1 - ph, 0, bd, 1 - pl, 1, sd)
					: VoxelShapes.cuboid(1 - ph, 0, 1 - sd, 1 - pl, 1, 1 - bd);
			default -> throw new IllegalStateException();
		};
	}

	// ═══════════════════════════════════════════════════════════════════════════════
	// Collision shapes — 2-box (60/40 split) for grades ≥ 2; 3-box for 1:1
	// ═══════════════════════════════════════════════════════════════════════════════
	//
	// Grades 2+ use a 2-box collision split 60/40 along the climb axis: lower box
	// covers 60% of the block (away from the high end), upper box is a 40% strip at
	// the high end. Heights:
	//   bot = baseHeight         (= bottom edge of wedge, = previous step's topHeight)
	//   top = baseHeight + wedge (= top edge of wedge, = next step's baseHeight)
	//
	// Sneak-back placement works because the lower box's height equals the player's
	// feet height (touch, not overlap), and the upper box is far enough from the
	// block edge that the player's sneak-extended bbox can't reach it.
	//
	// Visual disconnect (player feet below the visible wedge): up to ~0.6 × wedge at
	// the high-end transition. For 1:2 that's ~5 px; for 1:4 that's ~2.5 px.
	//
	// Stepup within a single ramp block = wedge height (one step at the 60% mark):
	//   1:4 → 4 px ✓   1:3 → ~5.3 px ✓   1:2 → 8 px ✓
	//
	// ── 1:1 special case ──
	// Sneak-up construction doesn't apply to 1:1 (climbing 1:1 = stepping to the next
	// block level vertically), and the steep visual benefits from collision matching
	// the rendered shape exactly. So 1:1 uses the 16-slice outline shape directly as
	// its collision. Walking up requires 16 small (1-px) auto-stepups so it's slower
	// than the 2-box approach, but there's no visual disconnect — feet always sit on
	// the visible wedge surface.

	private VoxelShape buildCollisionShape(RampOrientation orientation, Direction facing) {
		// 1:1 grade: collision matches outline exactly (16-slice wedge). See the
		// comment block above for rationale.
		if (grade == 1) {
			return buildOutlineShape(orientation, facing);
		}

		double basePx = 16.0 * step / grade;
		double wedgePx = 16.0 / grade;
		double bot = basePx / 16.0;
		double top = (basePx + wedgePx) / 16.0;

		// Step 0 (A ramp) fall-through fix for FLOOR: baseHeight=0 makes the lower
		// collision box have zero height. A player walking onto a suspended step 0
		// from an adjacent block higher than the ramp's thin end falls through. Fix:
		// bump the lower box up to topHeight so step 0 acts as a flat slab for
		// collision (visual wedge unchanged). Side effect: sneak-back placing step 0
		// from an adjacent block SHORTER than topHeight fails — players need to place
		// from terrain at floor level or from a block at least as tall as topHeight.
		// Only FLOOR needs this fix; CEILING/WALL/HORIZONTAL aren't walked on.
		double floorBot = (orientation == RampOrientation.FLOOR && step == 0) ? top : bot;

		return switch (orientation) {
			case FLOOR -> floorCollision(facing, floorBot, top);
			case CEILING -> ceilingCollision(facing, bot, top);
			case WALL_UP -> wallCollision(facing, bot, top, false);
			case WALL_DOWN -> wallCollision(facing, bot, top, true);
			case HORIZONTAL_LEFT -> horizontalCollision(facing, bot, top, false);
			case HORIZONTAL_RIGHT -> horizontalCollision(facing, bot, top, true);
		};
	}

	/** Cuboid that returns empty if any dimension is non-positive. Step 0's low box has zero height. */
	private static VoxelShape boxOrEmpty(double x1, double y1, double z1, double x2, double y2, double z2) {
		if (x2 - x1 <= 1e-6 || y2 - y1 <= 1e-6 || z2 - z1 <= 1e-6) return VoxelShapes.empty();
		return VoxelShapes.cuboid(x1, y1, z1, x2, y2, z2);
	}

	private static VoxelShape unionAll(VoxelShape... shapes) {
		VoxelShape acc = VoxelShapes.empty();
		for (VoxelShape s : shapes) {
			if (!s.isEmpty()) acc = VoxelShapes.union(acc, s);
		}
		return acc;
	}

	/** Width of the upper-edge collision strip at the high end (40% of block). */
	private static final double STEPUP_OFFSET = 0.4;

	/** FLOOR: 90% lower box at low end, 10% upper box at FACING (high) end. */
	private static VoxelShape floorCollision(Direction facing, double bot, double top) {
		double s = STEPUP_OFFSET;
		return switch (facing) {
			case NORTH -> unionAll(  // high end at -z
					boxOrEmpty(0, 0, s, 1, bot, 1),     // south 90%, bot height
					boxOrEmpty(0, 0, 0, 1, top, s));    // north 10%, top height
			case SOUTH -> unionAll(
					boxOrEmpty(0, 0, 0, 1, bot, 1 - s),
					boxOrEmpty(0, 0, 1 - s, 1, top, 1));
			case EAST -> unionAll(
					boxOrEmpty(0, 0, 0, 1 - s, bot, 1),
					boxOrEmpty(1 - s, 0, 0, 1, top, 1));
			case WEST -> unionAll(
					boxOrEmpty(s, 0, 0, 1, bot, 1),
					boxOrEmpty(0, 0, 0, s, top, 1));
			default -> throw new IllegalStateException();
		};
	}

	/** CEILING: same split as FLOOR but boxes hang from y=1. */
	private static VoxelShape ceilingCollision(Direction facing, double bot, double top) {
		double s = STEPUP_OFFSET;
		return switch (facing) {
			case NORTH -> unionAll(
					boxOrEmpty(0, 1 - bot, s, 1, 1, 1),
					boxOrEmpty(0, 1 - top, 0, 1, 1, s));
			case SOUTH -> unionAll(
					boxOrEmpty(0, 1 - bot, 0, 1, 1, 1 - s),
					boxOrEmpty(0, 1 - top, 1 - s, 1, 1, 1));
			case EAST -> unionAll(
					boxOrEmpty(0, 1 - bot, 0, 1 - s, 1, 1),
					boxOrEmpty(1 - s, 1 - top, 0, 1, 1, 1));
			case WEST -> unionAll(
					boxOrEmpty(s, 1 - bot, 0, 1, 1, 1),
					boxOrEmpty(0, 1 - top, 0, s, 1, 1));
			default -> throw new IllegalStateException();
		};
	}

	/** WALL: 90% lower y range at thin-side depth, 10% strip at high y end at thick depth.
	 *  WALL_DOWN flips so the 10% strip is at the bottom (the high end of the wedge). */
	private static VoxelShape wallCollision(Direction facing, double bot, double top, boolean thickAtBottom) {
		double s = STEPUP_OFFSET;
		// thinYStart..thinYEnd holds the bot depth; thickYStart..thickYEnd holds the top depth.
		double thinYStart, thinYEnd, thickYStart, thickYEnd;
		if (thickAtBottom) {
			thickYStart = 0; thickYEnd = s;          // bottom 10% — high end of wedge
			thinYStart = s;  thinYEnd = 1;           // upper 90%
		} else {
			thinYStart = 0;     thinYEnd = 1 - s;    // lower 90%
			thickYStart = 1 - s; thickYEnd = 1;      // top 10% — high end of wedge
		}
		return switch (facing) {
			case NORTH -> unionAll(
					boxOrEmpty(0, thinYStart, 0, 1, thinYEnd, bot),
					boxOrEmpty(0, thickYStart, 0, 1, thickYEnd, top));
			case EAST -> unionAll(
					boxOrEmpty(1 - bot, thinYStart, 0, 1, thinYEnd, 1),
					boxOrEmpty(1 - top, thickYStart, 0, 1, thickYEnd, 1));
			case SOUTH -> unionAll(
					boxOrEmpty(0, thinYStart, 1 - bot, 1, thinYEnd, 1),
					boxOrEmpty(0, thickYStart, 1 - top, 1, thickYEnd, 1));
			case WEST -> unionAll(
					boxOrEmpty(0, thinYStart, 0, bot, thinYEnd, 1),
					boxOrEmpty(0, thickYStart, 0, top, thickYEnd, 1));
			default -> throw new IllegalStateException();
		};
	}

	/** HORIZONTAL: same 90/10 split as FLOOR but full y; mirrored in x for rightHanded. */
	private static VoxelShape horizontalCollision(Direction facing, double bot, double top, boolean rightHanded) {
		double s = STEPUP_OFFSET;
		return switch (facing) {
			case NORTH -> rightHanded
					? unionAll(
							boxOrEmpty(1 - bot, 0, s, 1, 1, 1),
							boxOrEmpty(1 - top, 0, 0, 1, 1, s))
					: unionAll(
							boxOrEmpty(0, 0, s, bot, 1, 1),
							boxOrEmpty(0, 0, 0, top, 1, s));
			case EAST -> rightHanded
					? unionAll(
							boxOrEmpty(0, 0, 1 - bot, 1 - s, 1, 1),
							boxOrEmpty(1 - s, 0, 1 - top, 1, 1, 1))
					: unionAll(
							boxOrEmpty(0, 0, 0, 1 - s, 1, bot),
							boxOrEmpty(1 - s, 0, 0, 1, 1, top));
			case SOUTH -> rightHanded
					? unionAll(
							boxOrEmpty(0, 0, 0, bot, 1, 1 - s),
							boxOrEmpty(0, 0, 1 - s, top, 1, 1))
					: unionAll(
							boxOrEmpty(1 - bot, 0, 0, 1, 1, 1 - s),
							boxOrEmpty(1 - top, 0, 1 - s, 1, 1, 1));
			case WEST -> rightHanded
					? unionAll(
							boxOrEmpty(s, 0, 0, 1, 1, bot),
							boxOrEmpty(0, 0, 0, s, 1, top))
					: unionAll(
							boxOrEmpty(s, 0, 1 - bot, 1, 1, 1),
							boxOrEmpty(0, 0, 1 - top, s, 1, 1));
			default -> throw new IllegalStateException();
		};
	}

}
