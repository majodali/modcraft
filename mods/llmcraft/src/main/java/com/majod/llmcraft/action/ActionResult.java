package com.majod.llmcraft.action;

/**
 * Outcome of dispatching one {@link Action}. Mirrors the shape of an Anthropic tool_result
 * block — {@code content} goes back to the model verbatim, {@code isError} sets the
 * is_error flag so the model can decide to retry or apologize.
 *
 * Successful builds typically return a short summary ("Filled 64 blocks of stone").
 * Errors return a human-readable reason ("Region volume 8000 exceeds 4096-block limit");
 * the model is good at adapting when given a clear failure mode.
 */
public record ActionResult(String content, boolean isError) {

	public static ActionResult ok(String content) {
		return new ActionResult(content, false);
	}

	public static ActionResult error(String reason) {
		return new ActionResult(reason, true);
	}
}
