package com.majod.ramps.block;

import net.minecraft.util.StringIdentifiable;

/**
 * Placement orientation for a ramp block. Combined with FACING (4 horizontal directions),
 * gives all valid in-world placements (24 total per block: 6 orientations × 4 facings).
 *
 * Geometry per orientation (for FACING=NORTH; rotated by FACING in code):
 *
 *   FLOOR             — wedge sits on floor; thickness in y; high end at -z (north).
 *                       Triangular faces on east and west.
 *
 *   CEILING           — wedge hangs from ceiling; mirror of FLOOR in y.
 *                       Triangular faces on east and west.
 *
 *   WALL_UP           — wedge against the wall on FACING side; thickness in axis perpendicular
 *                       to wall; thick end up. Player can climb up by jumping on the steps.
 *                       Triangular faces on east and west (perpendicular to wall).
 *
 *   WALL_DOWN         — like WALL_UP but thick end down (mirror in y).
 *
 *   HORIZONTAL_LEFT   — wedge in horizontal plane (full y height); thickness in x;
 *                       back wall on west (left when looking toward thick end). Thick at -z.
 *                       Triangular faces on top and bottom.
 *
 *   HORIZONTAL_RIGHT  — mirror of HORIZONTAL_LEFT in x; back wall on east. Thick at -z.
 *                       Triangular faces on top and bottom.
 *
 * Naming note: "left" and "right" are from the perspective of someone looking toward
 * the FACING direction (i.e. toward the high end / thick end of the wedge).
 */
public enum RampOrientation implements StringIdentifiable {
	FLOOR("floor"),
	CEILING("ceiling"),
	WALL_UP("wall_up"),
	WALL_DOWN("wall_down"),
	HORIZONTAL_LEFT("horizontal_left"),
	HORIZONTAL_RIGHT("horizontal_right");

	private final String name;

	RampOrientation(String name) {
		this.name = name;
	}

	@Override
	public String asString() {
		return name;
	}
}
