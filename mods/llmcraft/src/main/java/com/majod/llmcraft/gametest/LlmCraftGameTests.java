package com.majod.llmcraft.gametest;

import com.majod.llmcraft.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * In-world Fabric GameTests for LLMCraft.
 *
 * Loaded only when the {@code -Dfabric-api.gametest} JVM flag is set (i.e. via the
 * {@code runGametest} Gradle task). The {@code fabric-gametest} entrypoint in
 * fabric.mod.json wires this class up.
 */
public class LlmCraftGameTests implements FabricGameTest {

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void echoStoneCanBeSpawnedAsItemEntity(TestContext context) {
		context.spawnItem(ModItems.ECHO_STONE, 1.0f, 2.0f, 1.0f);
		context.complete();
	}
}
