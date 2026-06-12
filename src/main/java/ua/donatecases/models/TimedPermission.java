package ua.donatecases.models;

import java.util.UUID;

public class TimedPermission {

    private final UUID playerUuid;
    private final String group;
    private final long expiryTimestamp; // Unix ms

    public TimedPermission(UUID playerUuid, String group, long expiryTimestamp) {
        this.playerUuid = playerUuid;
        this.group = group;
        this.expiryTimestamp = expiryTimestamp;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getGroup() { return group; }
    public long getExpiryTimestamp() { return expiryTimestamp; }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTimestamp;
    }

    public long getRemainingMs() {
        return Math.max(0, expiryTimestamp - System.currentTimeMillis());
    }

    public String getFormattedRemaining() {
        long ms = getRemainingMs();
        long days = ms / 86400000L;
        long hours = (ms % 86400000L) / 3600000L;
        long minutes = (ms % 3600000L) / 60000L;
        if (days > 0) return days + "д " + hours + "г";
        if (hours > 0) return hours + "г " + minutes + "хв";
        return minutes + "хв";
    }
}
