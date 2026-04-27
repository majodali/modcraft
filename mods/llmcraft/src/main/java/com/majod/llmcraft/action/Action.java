package com.majod.llmcraft.action;

import com.google.gson.JsonObject;

/**
 * One concrete LLM-callable operation. Sealed so {@link ActionDispatcher} can switch
 * exhaustively over the action space.
 *
 * Each variant is a plain immutable record; deserialization from a tool_use input
 * happens in {@link #fromToolUse}.
 *
 * Adding a new action:
 * <ol>
 *   <li>Add a {@code record … implements Action} below.</li>
 *   <li>Add a case to {@link #fromToolUse}.</li>
 *   <li>Define its {@link Tool} in {@link Tools}.</li>
 *   <li>Add an execution branch in {@link ActionDispatcher#execute}.</li>
 * </ol>
 */
public sealed interface Action {

	/** Tool name as exposed to the LLM. Used by {@link ActionDispatcher} for logging. */
	String toolName();

	/** Place a single block at an absolute world position. */
	record PlaceBlock(int x, int y, int z, String material) implements Action {
		@Override public String toolName() { return "place_block"; }
	}

	/**
	 * Fill an axis-aligned cuboid with the given material. Bounds are inclusive on
	 * both ends — {@code (0,0,0) → (0,0,0)} fills exactly one block.
	 *
	 * Volume is capped server-side; see {@link ActionDispatcher#MAX_REGION_VOLUME}.
	 */
	record FillRegion(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, String material)
			implements Action {
		@Override public String toolName() { return "fill_region"; }
	}

	/**
	 * Read back the block palette + counts inside an axis-aligned cuboid. Same
	 * volume cap as {@link FillRegion}. Used by the LLM to "look" at what's there.
	 */
	record QueryBlocks(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) implements Action {
		@Override public String toolName() { return "query_blocks"; }
	}

	/** Send a chat message back to the player. The LLM's voice. */
	record Chat(String message) implements Action {
		@Override public String toolName() { return "chat"; }
	}

	/**
	 * Parse an Action from a tool_use block's name + input JSON. Throws
	 * {@link IllegalArgumentException} for unknown tool names or missing required
	 * fields — the dispatcher catches this and reports it back to the LLM as a
	 * tool_result with isError=true so the model can recover.
	 */
	static Action fromToolUse(String toolName, JsonObject input) {
		return switch (toolName) {
			case "place_block" -> new PlaceBlock(
					requireInt(input, "x"),
					requireInt(input, "y"),
					requireInt(input, "z"),
					requireString(input, "material"));
			case "fill_region" -> new FillRegion(
					requireInt(input, "from_x"),
					requireInt(input, "from_y"),
					requireInt(input, "from_z"),
					requireInt(input, "to_x"),
					requireInt(input, "to_y"),
					requireInt(input, "to_z"),
					requireString(input, "material"));
			case "query_blocks" -> new QueryBlocks(
					requireInt(input, "from_x"),
					requireInt(input, "from_y"),
					requireInt(input, "from_z"),
					requireInt(input, "to_x"),
					requireInt(input, "to_y"),
					requireInt(input, "to_z"));
			case "chat" -> new Chat(requireString(input, "message"));
			default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
		};
	}

	private static int requireInt(JsonObject input, String key) {
		if (!input.has(key)) throw new IllegalArgumentException("Missing required field: " + key);
		return input.get(key).getAsInt();
	}

	private static String requireString(JsonObject input, String key) {
		if (!input.has(key)) throw new IllegalArgumentException("Missing required field: " + key);
		return input.get(key).getAsString();
	}
}
