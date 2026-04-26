package com.majod.ramps.block;

import com.majod.ramps.RampsMod;
import com.majod.ramps.item.ModItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModBlocks {
	private ModBlocks() {}

	/** Materials we support. Each owns its texture and vanilla `Settings` source. */
	public enum Material {
		OAK("oak", Blocks.OAK_PLANKS),
		STONE("stone", Blocks.STONE),
		COBBLESTONE("cobblestone", Blocks.COBBLESTONE);

		public final String name;
		public final Block settingsSource;

		Material(String name, Block settingsSource) {
			this.name = name;
			this.settingsSource = settingsSource;
		}
	}

	/** Custom creative tab for all ramp blocks. */
	public static final RegistryKey<ItemGroup> RAMPS_TAB_KEY =
			RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(RampsMod.MOD_ID, "ramps"));

	/** Step letters: index 0 = "a", 1 = "b", 2 = "c", 3 = "d". */
	public static final String[] STEP_LETTERS = {"a", "b", "c", "d"};

	/** Grades to support. Each grade N has N step-pieces. Grade 1 is the steep ramp
	 *  equivalent to vanilla stairs in slope (1 block climb in 1 horizontal block). */
	public static final List<Integer> GRADES = List.of(1, 2, 3, 4);

	/** Lookup: material → grade → step (0-indexed) → block. */
	public static final Map<Material, Map<Integer, List<RampBlock>>> RAMPS = registerAll();

	public static void register() {
		// Custom Ramps tab: Wrench first (it's the most-used item once you have ramps),
		// then blocks in (material, grade, step) order.
		ItemGroup rampsTab = FabricItemGroup.builder()
				.icon(() -> new ItemStack(RAMPS.get(Material.OAK).get(3).get(0)))  // oak 1:3-a
				.displayName(Text.translatable("itemGroup.ramps.ramps"))
				.entries((displayContext, entries) -> {
					entries.add(ModItems.RAMP_WRENCH);
					for (Material material : Material.values()) {
						for (int grade : GRADES) {
							for (RampBlock block : RAMPS.get(material).get(grade)) {
								entries.add(block);
							}
						}
					}
				})
				.build();
		Registry.register(Registries.ITEM_GROUP, RAMPS_TAB_KEY.getValue(), rampsTab);

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
