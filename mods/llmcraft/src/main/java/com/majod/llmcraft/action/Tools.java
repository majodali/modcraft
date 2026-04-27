package com.majod.llmcraft.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.majod.llmbridge.Tool;

import java.util.List;

/**
 * Tool definitions exposed to the LLM. Each one declares a JSON Schema for its inputs;
 * Anthropic enforces the schema server-side so we never receive a malformed tool_use.
 *
 * Adding a new tool: define a static factory below, register it in {@link #ALL}, and
 * add a parsing case in {@link Action#fromToolUse}.
 */
public final class Tools {
	private Tools() {}

	public static final Tool PLACE_BLOCK = new Tool(
			"place_block",
			"Place a single Minecraft block at an absolute world coordinate. "
					+ "The previous block at that position is overwritten (and recorded for undo).",
			intCoordsSchema(
					List.of("x", "y", "z", "material"),
					field("x",        "integer", "Absolute world X coordinate"),
					field("y",        "integer", "Absolute world Y coordinate (vertical)"),
					field("z",        "integer", "Absolute world Z coordinate"),
					field("material", "string",  "Block id, e.g. \"minecraft:stone\" or \"minecraft:oak_planks\"")));

	public static final Tool FILL_REGION = new Tool(
			"fill_region",
			"Fill an axis-aligned cuboid (inclusive on both ends) with a single material. "
					+ "Volume must be ≤ 4096 blocks (a 16x16x16 cube). Use multiple calls for "
					+ "larger structures or for mixed materials.",
			intCoordsSchema(
					List.of("from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "material"),
					field("from_x",   "integer", "Lower-corner X (inclusive)"),
					field("from_y",   "integer", "Lower-corner Y (inclusive)"),
					field("from_z",   "integer", "Lower-corner Z (inclusive)"),
					field("to_x",     "integer", "Upper-corner X (inclusive)"),
					field("to_y",     "integer", "Upper-corner Y (inclusive)"),
					field("to_z",     "integer", "Upper-corner Z (inclusive)"),
					field("material", "string",  "Block id, e.g. \"minecraft:stone\"")));

	public static final Tool QUERY_BLOCKS = new Tool(
			"query_blocks",
			"Read back the block palette and counts inside an axis-aligned cuboid. "
					+ "Use this to see what's currently there before deciding what to build. "
					+ "Same 4096-block volume cap as fill_region.",
			intCoordsSchema(
					List.of("from_x", "from_y", "from_z", "to_x", "to_y", "to_z"),
					field("from_x", "integer", "Lower-corner X (inclusive)"),
					field("from_y", "integer", "Lower-corner Y (inclusive)"),
					field("from_z", "integer", "Lower-corner Z (inclusive)"),
					field("to_x",   "integer", "Upper-corner X (inclusive)"),
					field("to_y",   "integer", "Upper-corner Y (inclusive)"),
					field("to_z",   "integer", "Upper-corner Z (inclusive)")));

	public static final Tool CHAT = new Tool(
			"chat",
			"Say something to the player in chat. Use sparingly — for clarifying questions, "
					+ "describing what you're about to do, or summarizing a finished build.",
			intCoordsSchema(
					List.of("message"),
					field("message", "string", "The message to display in the player's chat")));

	/** All tools available to /imagine and /iterate. Order doesn't matter to the model. */
	public static final List<Tool> ALL = List.of(PLACE_BLOCK, FILL_REGION, QUERY_BLOCKS, CHAT);

	// ─── schema helpers ─────────────────────────────────────────────────────────

	private record FieldSpec(String name, String type, String description) {}

	private static FieldSpec field(String name, String type, String description) {
		return new FieldSpec(name, type, description);
	}

	private static JsonObject intCoordsSchema(List<String> required, FieldSpec... fields) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");

		JsonObject props = new JsonObject();
		for (FieldSpec f : fields) {
			JsonObject p = new JsonObject();
			p.addProperty("type", f.type);
			p.addProperty("description", f.description);
			props.add(f.name, p);
		}
		schema.add("properties", props);

		JsonArray req = new JsonArray();
		required.forEach(req::add);
		schema.add("required", req);

		return schema;
	}
}
