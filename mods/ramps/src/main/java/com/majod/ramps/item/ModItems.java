package com.majod.ramps.item;

import com.majod.ramps.RampsMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModItems {
	private ModItems() {}

	public static final Item RAMP_WRENCH = registerItem("ramp_wrench",
			key -> new RampWrenchItem(new Item.Settings().registryKey(key).maxCount(1)));

	public static void register() {
		// Triggers static initialization above. Class-load forces the field assignment
		// which calls Registry.register; nothing else to do here.
		RampsMod.LOGGER.info("Registered Ramps items");
	}

	@FunctionalInterface
	private interface ItemFactory<T extends Item> {
		T create(RegistryKey<Item> key);
	}

	private static <T extends Item> T registerItem(String name, ItemFactory<T> factory) {
		Identifier id = Identifier.of(RampsMod.MOD_ID, name);
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
		T item = factory.create(key);
		Registry.register(Registries.ITEM, key, item);
		return item;
	}
}
