package com.majod.llmcraft.item;

import com.majod.llmcraft.LlmCraftMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public final class ModItems {
	private ModItems() {}

	public static final Item ECHO_STONE = register("echo_stone", Item::new, new Item.Settings());

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(ECHO_STONE));
		LlmCraftMod.LOGGER.info("Registered items");
	}

	private static Item register(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(LlmCraftMod.MOD_ID, name));
		return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
	}
}
