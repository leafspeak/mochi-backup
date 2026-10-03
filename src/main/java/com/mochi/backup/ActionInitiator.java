package com.mochi.backup;

/**
 * Enum representing possible sources of action
 */
public enum ActionInitiator {
    Player("Player", "by"),
    ServerConsole("Server Console", "from"),
    Timer("Timer", "by"),
    Shutdown("Server Shutdown", "by"),
    Restore("Backup Restoration", "because of");

    private final String name;
    private final String prefix;

    ActionInitiator(String name, String prefix) {
        this.name = name;
        this.prefix = prefix;
    }

    public String getName() { return name; }
    public String getPrefix() { return prefix + ": "; }
}
