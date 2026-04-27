package com.majod.llmcraft.action;

import com.majod.llmbridge.Conversation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player {@link UndoStack} + the most recent {@link Conversation} so {@code /undo}
 * and {@code /iterate} can pick up where the last {@code /imagine} left off.
 *
 * In-memory only — sessions reset on server restart. Persistence (NBT round-trip)
 * is deferred until cross-session context becomes a real need.
 *
 * Server-thread only.
 */
public final class PlayerSessions {

	public static final class Session {
		public final UndoStack undo = new UndoStack();
		public Conversation lastImagine;  // null until /imagine runs at least once
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private PlayerSessions() {}

	public static Session get(UUID playerId) {
		return SESSIONS.computeIfAbsent(playerId, k -> new Session());
	}

	public static Optional<Session> peek(UUID playerId) {
		return Optional.ofNullable(SESSIONS.get(playerId));
	}

	/** Drop a player's session (e.g. on logout). Safe to call for unknown players. */
	public static void clear(UUID playerId) {
		SESSIONS.remove(playerId);
	}

	/** Test seam — wipe all sessions. */
	public static void clearAll() {
		SESSIONS.clear();
	}
}
