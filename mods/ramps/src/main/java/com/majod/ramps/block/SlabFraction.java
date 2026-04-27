package com.majod.ramps.block;

/**
 * Slab heights as fractions of a full block. Each enum value is a separate placeable
 * block (not a state property) so that `1/4-oak-slab` and `3/4-oak-slab` are visibly
 * different items in the inventory.
 *
 * `heightPx` is the slab's thickness in MC's standard 1/16-block pixel units.
 *
 * Note: 1/3 and 2/3 don't divide 16 cleanly. Using exact float math (16/3, 32/3) keeps
 * vertical alignment with 1:3 and 1:6 ramp step bases (which use the same divisors),
 * so a 1/3 slab placed side-on against a 1:3 step-B has its top exactly flush with
 * the wedge's bottom edge.
 */
public enum SlabFraction {
	QUARTER       ("quarter",        "1_4", 4.0),
	THIRD         ("third",          "1_3", 16.0 / 3.0),
	HALF          ("half",           "1_2", 8.0),
	TWO_THIRDS    ("two_thirds",     "2_3", 32.0 / 3.0),
	THREE_QUARTERS("three_quarters", "3_4", 12.0);

	/** Long-form name used in code/logs. */
	public final String name;

	/** Compact "n_d" form used in block-id and asset filenames (e.g. `oak_slab_1_4`). */
	public final String nd;

	/** Slab thickness in 1/16-block pixels. Range (0, 16). */
	public final double heightPx;

	SlabFraction(String name, String nd, double heightPx) {
		this.name = name;
		this.nd = nd;
		this.heightPx = heightPx;
	}
}
