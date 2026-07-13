package com.lanxinai.data.paltform.ducklake.scheduler;

import java.util.Locale;
import java.util.Set;

public final class SchedulerRunStates {

    private static final Set<String> TERMINAL = Set.of(
            "SUCCESS", "FAILURE", "STOP", "KILL", "PAUSE", "FORCED_SUCCESS");

    private SchedulerRunStates() {
    }

    public static String normalize(String state) {
        if (state == null || state.isBlank()) return "UNKNOWN";
        String normalized = state.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("Scheduler state is too long");
        }
        return normalized;
    }

    public static boolean isTerminal(String state) {
        return TERMINAL.contains(normalize(state));
    }

    public static Set<String> terminalStates() {
        return TERMINAL;
    }
}
