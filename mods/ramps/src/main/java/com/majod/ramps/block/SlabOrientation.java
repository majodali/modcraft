package com.majod.ramps.block;

import net.minecraft.util.StringIdentifiable;

/**
 * Placement orientation for a slab. 6 values cover all axis-aligned attachments:
 * floor, ceiling, and one per horizontal wall.
 *
 * Slab geometry (height = h, in fraction of a block):
 *
 *   FLOOR        — sits on cell floor.   x=[0,1]      y=[0, h]   z=[0,1]
 *   CEILING      — hangs from cell roof. x=[0,1]      y=[1-h, 1] z=[0,1]
 *   WALL_NORTH   — flush against -z wall. x=[0,1]     y=[0,1]    z=[0, h]
 *   WALL_EAST    — flush against +x wall. x=[1-h, 1]  y=[0,1]    z=[0,1]
 *   WALL_SOUTH   — flush against +z wall. x=[0,1]     y=[0,1]    z=[1-h, 1]
 *   WALL_WEST    — flush against -x wall. x=[0, h]    y=[0,1]    z=[0,1]
 *
 * Naming convention: WALL_X means the slab is attached to wall X of its own cell —
 * i.e. a WALL_NORTH slab occupies the north (-z) side of the cell.
 *
 * No separate FACING property because each wall has its own enum value.
 */
public enum SlabOrientation implements StringIdentifiable {
	FLOOR("floor"),
	CEILING("ceiling"),
	WALL_NORTH("wall_north"),
	WALL_EAST("wall_east"),
	WALL_SOUTH("wall_south"),
	WALL_WEST("wall_west");

	private final String name;

	SlabOrientation(String name) {
		this.name = name;
	}

	@Override
	public String asString() {
		return name;
	}
}
