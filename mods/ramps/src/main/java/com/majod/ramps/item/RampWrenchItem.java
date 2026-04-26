package com.majod.ramps.item;

import com.majod.ramps.block.RampBlock;
import com.majod.ramps.block.RampOrientation;
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
 * Tool for adjusting placed ramp blocks in-place.
 *
 *   Right-click on a ramp           → cycle ORIENTATION (6 options)
 *   Sneak + right-click on a ramp   → cycle FACING (4 options)
 *   Right-click on anything else    → no action (pass through)
 *
 * Splitting orientation cycling out of the block's own onUse keeps regular
 * right-click free for direct ramp-on-ramp placement (no sneak required), and
 * avoids the "accidentally rotated my ramp" problem during normal play.
 *
 * Worst-case clicks to reach any of 24 placements:
 *   6 (orientation) + 3 (facing) = 9 total — including any sequencing.
 * Typical fix is 1–3 clicks.
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

		if (!(state.getBlock() instanceof RampBlock)) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}

		PlayerEntity player = context.getPlayer();
		boolean sneaking = player != null && player.isSneaking();

		BlockState newState = sneaking
				? state.with(RampBlock.FACING, rotateClockwise(state.get(RampBlock.FACING)))
				: state.with(RampBlock.ORIENTATION, nextOrientation(state.get(RampBlock.ORIENTATION)));

		world.setBlockState(pos, newState);
		// Distinct pitch for facing vs orientation cycles so the user can tell
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

	private static RampOrientation nextOrientation(RampOrientation current) {
		RampOrientation[] all = RampOrientation.values();
		return all[(current.ordinal() + 1) % all.length];
	}
}
