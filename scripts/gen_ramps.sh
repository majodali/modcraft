#!/usr/bin/env bash
# Generates the highly-repetitive JSON files for mods/ramps:
#   - 27 child block models (3 materials × 9 step-pieces across grades)
#   - 27 blockstate files
#   - 27 item asset files
#   - 27 loot tables
#   - 27 recipes (9 base planks→step-A + 18 upgrades step-A → step-B/C/D)
#
# Run from the repo root: bash scripts/gen_ramps.sh
# Templates and Java are hand-maintained — this script only emits the boilerplate.
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
LETTERS=(a b c d)

mkdir -p assets/ramps/models/block assets/ramps/blockstates assets/ramps/items \
         data/ramps/loot_table/blocks data/ramps/recipe

# -----------------------------------------------------------------------------
# Per (material × grade × step): child model, blockstate, item asset, loot table
# -----------------------------------------------------------------------------
for material in oak stone cobblestone; do
	texture="${TEX[$material]}"
	for grade in 2 3 4; do
		for step in $(seq 0 $((grade - 1))); do
			letter="${LETTERS[$step]}"
			base="${material}_ramp_1_${grade}_${letter}"

			cat > "assets/ramps/models/block/${base}.json" <<EOF
{
	"parent": "ramps:block/template_ramp_1_${grade}_${letter}",
	"textures": { "all": "${texture}" }
}
EOF

			cat > "assets/ramps/blockstates/${base}.json" <<EOF
{
	"variants": {
		"facing=north": { "model": "ramps:block/${base}" },
		"facing=east":  { "model": "ramps:block/${base}", "y": 90 },
		"facing=south": { "model": "ramps:block/${base}", "y": 180 },
		"facing=west":  { "model": "ramps:block/${base}", "y": 270 }
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
# Recipes
# -----------------------------------------------------------------------------
# Base recipes (planks → step A): different shape per grade so they don't conflict
#   1:2  __P/_PP/PPP   (6 planks → 4)
#   1:3  _PP/PPP       (5 planks → 6)
#   1:4  __P/PPP       (4 planks → 8)
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

# Upgrade recipes (N step-A → 1 higher-step) — patterns are volumetric:
#   __R/_RR     = 3 R    (step B for any grade)
#   _RR/RRR     = 5 R    (step C for grades 3 and 4)
#   __R/RRR/RRR = 7 R    (step D for grade 4)
# Different ingredient (= step-A item of that grade) per recipe → no collisions.
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

for material in oak stone cobblestone; do
	# Base recipes (planks → step A)
	write_base_recipe "$material" 2 '["  P", " PP", "PPP"]' 4
	write_base_recipe "$material" 3 '[" PP", "PPP"]'        6
	write_base_recipe "$material" 4 '["  P", "PPP"]'        8

	# 1:2 upgrades (1 total: a → b)
	write_upgrade_recipe "$material" 2 1 "$PATTERN_3R"

	# 1:3 upgrades (2 total: a → b, a → c)
	write_upgrade_recipe "$material" 3 1 "$PATTERN_3R"
	write_upgrade_recipe "$material" 3 2 "$PATTERN_5R"

	# 1:4 upgrades (3 total: a → b, a → c, a → d)
	write_upgrade_recipe "$material" 4 1 "$PATTERN_3R"
	write_upgrade_recipe "$material" 4 2 "$PATTERN_5R"
	write_upgrade_recipe "$material" 4 3 "$PATTERN_7R"
done

echo "Generated:"
echo "  child models:  $(ls assets/ramps/models/block/ | grep -v '^template_' | wc -l)"
echo "  blockstates:   $(ls assets/ramps/blockstates/ | wc -l)"
echo "  item assets:   $(ls assets/ramps/items/ | wc -l)"
echo "  loot tables:   $(ls data/ramps/loot_table/blocks/ | wc -l)"
echo "  recipes:       $(ls data/ramps/recipe/ | wc -l)"
