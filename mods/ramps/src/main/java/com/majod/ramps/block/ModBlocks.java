package com.majod.ramps.block;

import com.majod.ramps.RampsMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModBlocks {
	private ModBlocks() {}

	/**
	 * Materials we support. Each owns its texture, vanilla `Settings` source,
	 * and the vanilla stair item it clusters after in the creative tab.
	 */
	public enum Material {
		OAK("oak", Blocks.OAK_PLANKS, () -> Items.OAK_STAIRS),
		STONE("stone", Blocks.STONE, () -> Items.STONE_STAIRS),
		COBBLESTONE("cobblestone", Blocks.COBBLESTONE, () -> Items.COBBLESTONE_STAIRS);

		public final String name;
		public final Block settingsSource;
		private final java.util.function.Supplier<Item> creativeTabAnchor;

		Material(String name, Block settingsSource, java.util.function.Supplier<Item> creativeTabAnchor) {
			this.name = name;
			this.settingsSource = settingsSource;
			this.creativeTabAnchor = creativeTabAnchor;
		}

		public Item creativeTabAnchor() { return creativeTabAnchor.get(); }
	}

	/** Step letters: index 0 = "a", 1 = "b", 2 = "c", 3 = "d". */
	public static final String[] STEP_LETTERS = {"a", "b", "c", "d"};

	/** Grades to support. Each grade N has N step-pieces. */
	public static final List<Integer> GRADES = List.of(2, 3, 4);

	/** Lookup: material → grade → step (0-indexed) → block. */
	public static final Map<Material, Map<Integer, List<RampBlock>>> RAMPS = registerAll();

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
			// Cluster each material's blocks after its vanilla stair, in
			// (grade ascending, step ascending) order — so a 1:2 a/b come first,
			// then 1:3 a/b/c, then 1:4 a/b/c/d.
			for (Material material : Material.values()) {
				List<ItemConvertible> rampsForMaterial = new ArrayList<>();
				for (int grade : GRADES) {
					rampsForMaterial.addAll(RAMPS.get(material).get(grade));
				}
				entries.addAfter(material.creativeTabAnchor(),
						rampsForMaterial.toArray(new ItemConvertible[0]));
			}
		});
		int total = Material.values().length * GRADES.stream().mapToInt(Integer::intValue).sum();
		RampsMod.LOGGER.info("Registered {} ramp blocks ({} materials × {} step-pieces across grades)",
				total, Material.values().length, GRADES.stream().mapToInt(Integer::intValue).sum());
	}

	private static Map<Material, Map<Integer, List<RampBlock>>> registerAll() {
		Map<Material, Map<Integer, List<RampBlock>>> out = new EnumMap<>(Material.class);
		for (Material material : Material.values()) {
			Map<Integer, List<RampBlock>> byGrade = new HashMap<>();
			for (int grade : GRADES) {
				List<RampBlock> bySteps = new ArrayList<>(grade);
				for (int step = 0; step < grade; step++) {
					String name = material.name + "_ramp_1_" + grade + "_" + STEP_LETTERS[step];
					bySteps.add(registerRamp(name, grade, step, material.settingsSource));
				}
				byGrade.put(grade, bySteps);
			}
			out.put(material, byGrade);
		}
		return out;
	}

	private static RampBlock registerRamp(String name, int grade, int step, Block settingsSource) {
		Identifier id = Identifier.of(RampsMod.MOD_ID, name);
		RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);

		AbstractBlock.Settings settings = AbstractBlock.Settings.copy(settingsSource).registryKey(blockKey);
		RampBlock block = new RampBlock(grade, step, settings);
		Registry.register(Registries.BLOCK, blockKey, block);
		Registry.register(Registries.ITEM, itemKey,
				new BlockItem(block, new Item.Settings()
						.registryKey(itemKey)
						.useBlockPrefixedTranslationKey()));
		return block;
	}
}
