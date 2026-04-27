package com.majod.ramps.item;

import com.majod.ramps.block.RampBlock;
import com.majod.ramps.block.RampOrientation;
import com.majod.ramps.block.SlabBlock;
import com.majod.ramps.block.SlabOrientation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Tool for adjusting placed ramp and slab blocks in-place.
 *
 * On a ramp:
 *   Right-click           → cycle ORIENTATION (6 options) forward
 *   Sneak + right-click   → cycle FACING (4 options) clockwise
 *
 * On a slab:
 *   Right-click           → cycle ORIENTATION (6 options) forward
 *   Sneak + right-click   → cycle ORIENTATION backward (slabs have no FACING)
 *
 * On anything else: pass through.
 *
 * Splitting orientation cycling out of the block's own onUse keeps regular
 * right-click free for direct ramp-on-ramp placement (no sneak required), and
 * avoids the "accidentally rotated my ramp" problem during normal play.
 */
public class RampWrenchItem extends Item {
	public RampWrenchItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);

		PlayerEntity player = context.getPlayer();
		boolean sneaking = player != null && player.isSneaking();

		BlockState newState;
		if (state.getBlock() instanceof RampBlock) {
			newState = sneaking
					? state.with(RampBlock.FACING, rotateClockwise(state.get(RampBlock.FACING)))
					: state.with(RampBlock.ORIENTATION, nextRampOrientation(state.get(RampBlock.ORIENTATION)));
		} else if (state.getBlock() instanceof SlabBlock) {
			newState = state.with(SlabBlock.ORIENTATION,
					cycleSlabOrientation(state.get(SlabBlock.ORIENTATION), sneaking));
		} else {
			return ActionResult.PASS;
		}

		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		world.setBlockState(pos, newState);
		// Distinct pitch for facing/reverse-cycle vs forward-cycle so the user can tell
		// which dimension just changed without looking.
		world.playSound(null, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS,
				0.7f, sneaking ? 1.4f : 0.9f);
		return ActionResult.SUCCESS;
	}

	private static Direction rotateClockwise(Direction facing) {
		return switch (facing) {
			case NORTH -> Direction.EAST;
			case EAST -> Direction.SOUTH;
			case SOUTH -> Direction.WEST;
			case WEST -> Direction.NORTH;
			default -> throw new IllegalArgumentException("Non-horizontal facing: " + facing);
		};
	}

	private static RampOrientation nextRampOrientation(RampOrientation current) {
		RampOrientation[] all = RampOrientation.values();
		return all[(current.ordinal() + 1) % all.length];
	}

	private static SlabOrientation cycleSlabOrientation(SlabOrientation current, boolean reverse) {
		SlabOrientation[] all = SlabOrientation.values();
		int n = all.length;
		int next = reverse ? (current.ordinal() - 1 + n) % n : (current.ordinal() + 1) % n;
		return all[next];
	}
}
