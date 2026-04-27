#!/usr/bin/env bash
# Generates the highly-repetitive JSON files for mods/ramps. Step counts depend on
# the GRADES list — currently {1,2,3,4,6} → 1+2+3+4+6 = 16 step pieces per material.
#   - 16 model templates per orientation (FLOOR + HORIZONTAL_LEFT + HORIZONTAL_RIGHT = 48 total)
#     CEILING and WALL_UP/WALL_DOWN reuse FLOOR templates via blockstate JSON rotation.
#   - Per (material × grade × step): child block models for each orientation that has its
#     own template (FLOOR + HORIZONTAL_L + HORIZONTAL_R = 144 child models)
#   - Per (material × grade × step): blockstate file with 24 variants (4 facings × 6 orientations)
#   - 48 item asset files
#   - 48 loot tables
#   - Recipes: per material, 5 base recipes (one per grade) + 11 upgrade recipes = 16; ×3 materials = 48
#
# Run from the repo root: bash scripts/gen_ramps.sh
# Java is hand-maintained — this script only emits JSON boilerplate.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RES="$ROOT/mods/ramps/src/main/resources"
cd "$RES"

declare -A TEX=(
	[oak]="minecraft:block/oak_planks"
	[stone]="minecraft:block/stone"
	[cobblestone]="minecraft:block/cobblestone"
)
declare -A INGRED=(
	[oak]="minecraft:oak_planks"
	[stone]="minecraft:stone"
	[cobblestone]="minecraft:cobblestone"
)
LETTERS=(a b c d e f)

mkdir -p assets/ramps/models/block assets/ramps/blockstates assets/ramps/items \
         data/ramps/loot_table/blocks data/ramps/recipe

# Format a number as up to 3 decimal places, dropping trailing zeros.
num() {
	awk -v n="$1" 'BEGIN {
		s = sprintf("%.3f", n)
		sub(/0+$/, "", s)
		sub(/\.$/, "", s)
		print s
	}'
}

# -----------------------------------------------------------------------------
# Templates
# -----------------------------------------------------------------------------
# All templates: 9 wedge slices stacking along z (slice i at z = [(7-i)*2, (8-i)*2]),
# with optional support box for step > 0. Geometry differs per orientation:
#
#   FLOOR             — support box on bottom (y=[0, baseHeight]); slices stack in y.
#                       Slice i top: y = baseHeight + wedge * (i+1) / 8.
#                       Slice extent: x=[0,16], y=[0, sliceTop], z=slice_z.
#
#   HORIZONTAL_LEFT   — support wall on -x side (x=[0, baseDepth]); slices grow in +x.
#                       Slice extent: x=[baseDepth, sliceDepth], y=[0,16], z=slice_z.
#
#   HORIZONTAL_RIGHT  — mirror of LEFT in x; support wall on +x side.
#                       Slice extent: x=[16-sliceDepth, 16-baseDepth], y=[0,16], z=slice_z.
#
# CEILING reuses FLOOR templates via blockstate rotation (x=180 + adjusted y).

SLICES=16  # must match RampBlock.SLICES

# gen_floor_template <grade> <step>
gen_floor_template() {
	local grade=$1 step=$2
	local letter="${LETTERS[$step]}"
	local out="assets/ramps/models/block/template_ramp_1_${grade}_${letter}.json"

	local base_h
	base_h=$(awk -v g="$grade" -v s="$step" 'BEGIN { printf "%g", 16*s/g }')
	local wedge_h
	wedge_h=$(awk -v g="$grade" 'BEGIN { printf "%g", 16/g }')

	{
		echo '{'
		echo '	"parent": "minecraft:block/block",'
		echo '	"textures": { "particle": "#all" },'
		echo '	"elements": ['

		# Support box (step > 0): x=[0,16], y=[0,baseHeight], z=[0,16].
		if [[ "$step" -gt 0 ]]; then
			cat <<EOF
		{ "from": [0, 0, 0], "to": [16, $(num "$base_h"), 16], "faces": {
			"down":  { "texture": "#all", "cullface": "down" },
			"up":    { "texture": "#all" },
			"north": { "texture": "#all", "cullface": "north" },
			"south": { "texture": "#all", "cullface": "south" },
			"east":  { "texture": "#all", "cullface": "east" },
			"west":  { "texture": "#all", "cullface": "west" }
		}},
EOF
		fi

		# SLICES wedge slices stacking in y, climbing toward -z.
		local slice_px
		slice_px=$(awk -v s="$SLICES" 'BEGIN { printf "%g", 16/s }')
		for i in $(seq 0 $((SLICES - 1))); do
			local z_high
			z_high=$(awk -v s="$SLICES" -v i="$i" 'BEGIN { printf "%g", (s-i)*16/s }')
			local z_low
			z_low=$(awk -v s="$SLICES" -v i="$i" 'BEGIN { printf "%g", (s-i-1)*16/s }')
			local slice_top
			slice_top=$(awk -v b="$base_h" -v w="$wedge_h" -v i="$i" -v s="$SLICES" 'BEGIN { printf "%g", b + w*(i+1)/s }')

			local cull_down
			cull_down=$(if [[ "$step" -eq 0 ]]; then echo ', "cullface": "down"'; else echo ''; fi)
			local cull_south
			cull_south=$(if [[ "$i" -eq 0 ]]; then echo ', "cullface": "south"'; else echo ''; fi)
			local cull_north
			cull_north=$(if [[ "$i" -eq $((SLICES - 1)) ]]; then echo ', "cullface": "north"'; else echo ''; fi)
			local trailing
			trailing=$(if [[ "$i" -lt $((SLICES - 1)) ]]; then echo ','; else echo ''; fi)

			cat <<EOF
		{ "from": [0, $(num "$base_h"), $z_low], "to": [16, $(num "$slice_top"), $z_high], "faces": {
			"down":  { "texture": "#all"${cull_down} },
			"up":    { "texture": "#all" },
			"north": { "texture": "#all"${cull_north} },
			"south": { "texture": "#all"${cull_south} },
			"east":  { "texture": "#all" },
			"west":  { "texture": "#all" }
		}}${trailing}
EOF
		done

		echo '	]'
		echo '}'
	} > "$out"
}

# gen_horiz_template <grade> <step> <hand>   where hand = "left" or "right"
gen_horiz_template() {
	local grade=$1 step=$2 hand=$3
	local letter="${LETTERS[$step]}"
	local out="assets/ramps/models/block/template_ramp_1_${grade}_${letter}_horiz_${hand}.json"

	local base_d
	base_d=$(awk -v g="$grade" -v s="$step" 'BEGIN { printf "%g", 16*s/g }')
	local wedge_d
	wedge_d=$(awk -v g="$grade" 'BEGIN { printf "%g", 16/g }')
	local base_d_inv
	base_d_inv=$(awk -v b="$base_d" 'BEGIN { printf "%g", 16-b }')

	{
		echo '{'
		echo '	"parent": "minecraft:block/block",'
		echo '	"textures": { "particle": "#all" },'
		echo '	"elements": ['

		# Support wall (step > 0): full y, full z, x range depends on handedness.
		# LEFT  → x=[0, baseDepth]      back wall on west
		# RIGHT → x=[16-baseDepth, 16]  back wall on east
		if [[ "$step" -gt 0 ]]; then
			local sx_lo sx_hi cull_back cull_front
			if [[ "$hand" == "left" ]]; then
				sx_lo=0; sx_hi=$(num "$base_d")
				cull_back='west'; cull_front='east'
			else
				sx_lo=$(num "$base_d_inv"); sx_hi=16
				cull_back='east'; cull_front='west'
			fi
			cat <<EOF
		{ "from": [${sx_lo}, 0, 0], "to": [${sx_hi}, 16, 16], "faces": {
			"down":  { "texture": "#all", "cullface": "down" },
			"up":    { "texture": "#all", "cullface": "up" },
			"north": { "texture": "#all", "cullface": "north" },
			"south": { "texture": "#all", "cullface": "south" },
			"east":  { "texture": "#all" },
			"west":  { "texture": "#all" }
		}},
EOF
		fi

		# SLICES wedge slices: full y, x extent grows along z (last slice at z=[0, slice_px] is thickest).
		for i in $(seq 0 $((SLICES - 1))); do
			local z_high
			z_high=$(awk -v s="$SLICES" -v i="$i" 'BEGIN { printf "%g", (s-i)*16/s }')
			local z_low
			z_low=$(awk -v s="$SLICES" -v i="$i" 'BEGIN { printf "%g", (s-i-1)*16/s }')
			local slice_d
			slice_d=$(awk -v b="$base_d" -v w="$wedge_d" -v i="$i" -v s="$SLICES" 'BEGIN { printf "%g", b + w*(i+1)/s }')

			# Slice x range
			local sx_lo sx_hi
			if [[ "$hand" == "left" ]]; then
				sx_lo=$(num "$base_d"); sx_hi=$(num "$slice_d")
			else
				local slice_d_inv
				slice_d_inv=$(awk -v sd="$slice_d" 'BEGIN { printf "%g", 16-sd }')
				sx_lo=$(num "$slice_d_inv"); sx_hi=$(num "$base_d_inv")
			fi

			# Cullfaces for slice
			local cull_up=', "cullface": "up"'
			local cull_down=', "cullface": "down"'
			local cull_south=''
			if [[ "$i" -eq 0 ]]; then cull_south=', "cullface": "south"'; fi
			local cull_north=''
			if [[ "$i" -eq $((SLICES - 1)) ]]; then cull_north=', "cullface": "north"'; fi
			local trailing=','
			if [[ "$i" -eq $((SLICES - 1)) ]]; then trailing=''; fi

			# Decide which slice face touches the back wall direction (and thus may need cullface)
			local west_clause='"west":  { "texture": "#all" }'
			local east_clause='"east":  { "texture": "#all" }'
			if [[ "$hand" == "left" && "$step" -eq 0 ]]; then
				west_clause='"west":  { "texture": "#all", "cullface": "west" }'
			elif [[ "$hand" == "right" && "$step" -eq 0 ]]; then
				east_clause='"east":  { "texture": "#all", "cullface": "east" }'
			fi

			cat <<EOF
		{ "from": [${sx_lo}, 0, ${z_low}], "to": [${sx_hi}, 16, ${z_high}], "faces": {
			"down":  { "texture": "#all"${cull_down} },
			"up":    { "texture": "#all"${cull_up} },
			"north": { "texture": "#all"${cull_north} },
			"south": { "texture": "#all"${cull_south} },
			${east_clause},
			${west_clause}
		}}${trailing}
EOF
		done

		echo '	]'
		echo '}'
	} > "$out"
}

# Generate all templates (grade 1 = vanilla-stair-equivalent slope; grade 6 = shallow)
for grade in 1 2 3 4 6; do
	for step in $(seq 0 $((grade - 1))); do
		gen_floor_template "$grade" "$step"
		gen_horiz_template "$grade" "$step" left
		gen_horiz_template "$grade" "$step" right
	done
done

# -----------------------------------------------------------------------------
# Per (material × grade × step): child models, blockstate, item asset, loot table
# -----------------------------------------------------------------------------

# CEILING and WALL reuse FLOOR's child model via blockstate rotation. The base
# FLOOR_NORTH model has wedge climbing in +y with high end at -z. Each rotation
# below transforms the wall/floor surface and FACING direction:
#   FLOOR:  no x rotation; y rotates the high end direction
#     NORTH=0, EAST=90, SOUTH=180, WEST=270
#   CEILING: x=180 (flips upside-down + flips z, so high end now at +z); y rotates +z:
#     NORTH = x180 + y180  (+z back to -z)
#     EAST  = x180 + y270  (+z to +x — counter-clockwise from above)
#     SOUTH = x180         (+z stays as +z)
#     WEST  = x180 + y90   (+z to -x — clockwise from above)
#   WALL_UP: x=270 (slice 7 thickest ends up at top y; wall surface at -z = north)
#     y rotates wall from -z to FACING:
#     NORTH = x270         (wall stays at -z)
#     EAST  = x270 + y90   (wall at -z → +x)
#     SOUTH = x270 + y180  (wall at -z → +z)
#     WEST  = x270 + y270  (wall at -z → -x)
#   WALL_DOWN: x=90 (slice 7 ends up at bottom y; wall surface at +z = south)
#     y rotates wall from +z to FACING:
#     NORTH = x90 + y180   (wall at +z → -z)
#     EAST  = x90 + y270   (wall at +z → +x — counter-clockwise from above)
#     SOUTH = x90          (wall stays at +z)
#     WEST  = x90 + y90    (wall at +z → -x — clockwise from above)

for material in oak stone cobblestone; do
	texture="${TEX[$material]}"
	for grade in 1 2 3 4 6; do
		for step in $(seq 0 $((grade - 1))); do
			letter="${LETTERS[$step]}"
			base="${material}_ramp_1_${grade}_${letter}"

			# Child models: one per orientation that has a unique template
			cat > "assets/ramps/models/block/${base}.json" <<EOF
{
	"parent": "ramps:block/template_ramp_1_${grade}_${letter}",
	"textures": { "all": "${texture}" }
}
EOF
			cat > "assets/ramps/models/block/${base}_horiz_left.json" <<EOF
{
	"parent": "ramps:block/template_ramp_1_${grade}_${letter}_horiz_left",
	"textures": { "all": "${texture}" }
}
EOF
			cat > "assets/ramps/models/block/${base}_horiz_right.json" <<EOF
{
	"parent": "ramps:block/template_ramp_1_${grade}_${letter}_horiz_right",
	"textures": { "all": "${texture}" }
}
EOF

			# Blockstate: 4 facings × 4 orientations = 16 variants
			cat > "assets/ramps/blockstates/${base}.json" <<EOF
{
	"variants": {
		"facing=north,orientation=floor":            { "model": "ramps:block/${base}" },
		"facing=east,orientation=floor":             { "model": "ramps:block/${base}", "y": 90 },
		"facing=south,orientation=floor":            { "model": "ramps:block/${base}", "y": 180 },
		"facing=west,orientation=floor":             { "model": "ramps:block/${base}", "y": 270 },

		"facing=north,orientation=ceiling":          { "model": "ramps:block/${base}", "x": 180, "y": 180 },
		"facing=east,orientation=ceiling":           { "model": "ramps:block/${base}", "x": 180, "y": 270 },
		"facing=south,orientation=ceiling":          { "model": "ramps:block/${base}", "x": 180 },
		"facing=west,orientation=ceiling":           { "model": "ramps:block/${base}", "x": 180, "y": 90 },

		"facing=north,orientation=wall_up":          { "model": "ramps:block/${base}", "x": 270 },
		"facing=east,orientation=wall_up":           { "model": "ramps:block/${base}", "x": 270, "y": 90 },
		"facing=south,orientation=wall_up":          { "model": "ramps:block/${base}", "x": 270, "y": 180 },
		"facing=west,orientation=wall_up":           { "model": "ramps:block/${base}", "x": 270, "y": 270 },

		"facing=north,orientation=wall_down":        { "model": "ramps:block/${base}", "x": 90, "y": 180 },
		"facing=east,orientation=wall_down":         { "model": "ramps:block/${base}", "x": 90, "y": 270 },
		"facing=south,orientation=wall_down":        { "model": "ramps:block/${base}", "x": 90 },
		"facing=west,orientation=wall_down":         { "model": "ramps:block/${base}", "x": 90, "y": 90 },

		"facing=north,orientation=horizontal_left":  { "model": "ramps:block/${base}_horiz_left" },
		"facing=east,orientation=horizontal_left":   { "model": "ramps:block/${base}_horiz_left", "y": 90 },
		"facing=south,orientation=horizontal_left":  { "model": "ramps:block/${base}_horiz_left", "y": 180 },
		"facing=west,orientation=horizontal_left":   { "model": "ramps:block/${base}_horiz_left", "y": 270 },

		"facing=north,orientation=horizontal_right": { "model": "ramps:block/${base}_horiz_right" },
		"facing=east,orientation=horizontal_right":  { "model": "ramps:block/${base}_horiz_right", "y": 90 },
		"facing=south,orientation=horizontal_right": { "model": "ramps:block/${base}_horiz_right", "y": 180 },
		"facing=west,orientation=horizontal_right":  { "model": "ramps:block/${base}_horiz_right", "y": 270 }
	}
}
EOF

			cat > "assets/ramps/items/${base}.json" <<EOF
{
	"model": {
		"type": "minecraft:model",
		"model": "ramps:block/${base}"
	}
}
EOF

			cat > "data/ramps/loot_table/blocks/${base}.json" <<EOF
{
	"type": "minecraft:block",
	"pools": [{
		"rolls": 1.0, "bonus_rolls": 0.0,
		"entries": [{ "type": "minecraft:item", "name": "ramps:${base}" }],
		"conditions": [{ "condition": "minecraft:survives_explosion" }]
	}]
}
EOF
		done
	done
done

# -----------------------------------------------------------------------------
# Recipes (unchanged from prior version)
# -----------------------------------------------------------------------------
write_base_recipe() {
	local material=$1 grade=$2 pattern_json=$3 yield=$4
	local ingredient="${INGRED[$material]}"
	cat > "data/ramps/recipe/${material}_ramp_1_${grade}_a.json" <<EOF
{
	"type": "minecraft:crafting_shaped",
	"category": "building",
	"key": { "P": "${ingredient}" },
	"pattern": ${pattern_json},
	"result": { "id": "ramps:${material}_ramp_1_${grade}_a", "count": ${yield} }
}
EOF
}

write_upgrade_recipe() {
	local material=$1 grade=$2 target_step=$3 pattern_json=$4
	local letter="${LETTERS[$target_step]}"
	local source_block="ramps:${material}_ramp_1_${grade}_a"
	cat > "data/ramps/recipe/${material}_ramp_1_${grade}_${letter}.json" <<EOF
{
	"type": "minecraft:crafting_shaped",
	"category": "building",
	"key": { "R": "${source_block}" },
	"pattern": ${pattern_json},
	"result": { "id": "ramps:${material}_ramp_1_${grade}_${letter}", "count": 1 }
}
EOF
}

PATTERN_3R='["  R", " RR"]'
PATTERN_5R='[" RR", "RRR"]'
PATTERN_7R='["  R", "RRR", "RRR"]'
PATTERN_9R='["RRR", "RRR", "RRR"]'

# Shapeless recipe: 1 source-step + N filler-step (typically step-A) → 1 target-step.
# Used for grade 6 step-F (which would need 11 step-A blocks via the standard upgrade
# chain — too many for a 3×3 grid). Instead, craft step-F from 1 step-E + 2 step-A.
write_shapeless_recipe() {
	local material=$1 grade=$2 source_letter=$3 target_letter=$4 filler_count=$5
	local fillers=""
	for ((j=0; j<filler_count; j++)); do
		fillers+=$',\n\t\t"ramps:'"${material}_ramp_1_${grade}_a"'"'
	done
	cat > "data/ramps/recipe/${material}_ramp_1_${grade}_${target_letter}_from_${source_letter}.json" <<EOF
{
	"type": "minecraft:crafting_shapeless",
	"category": "building",
	"ingredients": [
		"ramps:${material}_ramp_1_${grade}_${source_letter}"${fillers}
	],
	"result": { "id": "ramps:${material}_ramp_1_${grade}_${target_letter}", "count": 1 }
}
EOF
}

for material in oak stone cobblestone; do
	# Base recipes (planks → step A); each grade gets a unique shape so MC's matcher
	# doesn't mask any of them. Yield scales roughly with how many step pieces a
	# full ramp needs (so one recipe makes ~2 full ramps' worth).
	write_base_recipe "$material" 1 '["PP", "PP"]'          4
	write_base_recipe "$material" 2 '["  P", " PP", "PPP"]' 4
	write_base_recipe "$material" 3 '[" PP", "PPP"]'        6
	write_base_recipe "$material" 4 '["  P", "PPP"]'        8
	write_base_recipe "$material" 6 '["PPP", "PPP"]'        12

	# Upgrade recipes (step-A → higher steps). Grade 1 has only one step → no upgrades.
	write_upgrade_recipe "$material" 2 1 "$PATTERN_3R"

	write_upgrade_recipe "$material" 3 1 "$PATTERN_3R"
	write_upgrade_recipe "$material" 3 2 "$PATTERN_5R"

	write_upgrade_recipe "$material" 4 1 "$PATTERN_3R"
	write_upgrade_recipe "$material" 4 2 "$PATTERN_5R"
	write_upgrade_recipe "$material" 4 3 "$PATTERN_7R"

	# 1:6 upgrade chain. Volumetric ratios (in wedge units): a=1, b=3, c=5, d=7, e=9, f=11.
	# Steps b/c/d/e fit in the 3×3 grid as shaped recipes; step F needs 11 step-A which
	# overflows, so f is crafted shapelessly from 1 step-E + 2 step-A (totaling 11 step-A
	# worth of input).
	write_upgrade_recipe   "$material" 6 1 "$PATTERN_3R"
	write_upgrade_recipe   "$material" 6 2 "$PATTERN_5R"
	write_upgrade_recipe   "$material" 6 3 "$PATTERN_7R"
	write_upgrade_recipe   "$material" 6 4 "$PATTERN_9R"
	write_shapeless_recipe "$material" 6 e f 2
done

# -----------------------------------------------------------------------------
# Ramp Wrench: tool item for adjusting orientation/facing of placed ramps.
# -----------------------------------------------------------------------------
cat > "assets/ramps/items/ramp_wrench.json" <<'EOF'
{
	"model": {
		"type": "minecraft:model",
		"model": "minecraft:item/iron_ingot"
	}
}
EOF

# Recipe: 2 iron ingots stacked over a stick (mini-pickaxe shape, 2x3 = 3 cells).
# Distinct from any block recipe → no conflicts.
cat > "data/ramps/recipe/ramp_wrench.json" <<'EOF'
{
	"type": "minecraft:crafting_shaped",
	"category": "equipment",
	"key": {
		"I": "minecraft:iron_ingot",
		"S": "minecraft:stick"
	},
	"pattern": [
		"I",
		"I",
		"S"
	],
	"result": { "id": "ramps:ramp_wrench", "count": 1 }
}
EOF

echo "Generated:"
echo "  templates:     $(ls assets/ramps/models/block/ | grep '^template_' | wc -l)"
echo "  child models:  $(ls assets/ramps/models/block/ | grep -v '^template_' | wc -l)"
echo "  blockstates:   $(ls assets/ramps/blockstates/ | wc -l)"
echo "  item assets:   $(ls assets/ramps/items/ | wc -l)"
echo "  loot tables:   $(ls data/ramps/loot_table/blocks/ | wc -l)"
echo "  recipes:       $(ls data/ramps/recipe/ | wc -l)"
