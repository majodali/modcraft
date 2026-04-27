package com.majod.llmbridge;

import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * A tool the model is allowed to call. {@code inputSchema} is a JSON-Schema object
 * describing the shape of a valid {@code input} payload — Anthropic enforces this
 * server-side, so the model never returns a malformed call when this client is used.
 *
 * Build the schema explicitly:
 * <pre>{@code
 * JsonObject schema = new JsonObject();
 * schema.addProperty("type", "object");
 * JsonObject props = new JsonObject();
 * JsonObject xProp = new JsonObject(); xProp.addProperty("type", "integer");
 * props.add("x", xProp);
 * schema.add("properties", props);
 * JsonArray required = new JsonArray(); required.add("x");
 * schema.add("required", required);
 * new Tool("ping", "Echo coordinate back", schema);
 * }</pre>
 */
public record Tool(String name, String description, JsonObject inputSchema) {

	public Tool {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(inputSchema, "inputSchema");
	}
}
