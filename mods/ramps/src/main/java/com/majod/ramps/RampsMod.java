package com.majod.ramps;

import com.majod.ramps.block.ModBlocks;
import com.majod.ramps.item.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RampsMod implements ModInitializer {
	public static final String MOD_ID = "ramps";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Items registered before blocks so the creative tab can reference RAMP_WRENCH.
		ModItems.register();
		ModBlocks.register();
		LOGGER.info("Ramps mod initialized.");
	}
}
