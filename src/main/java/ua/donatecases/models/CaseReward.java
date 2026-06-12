package ua.donatecases.models;

import java.util.List;

public class CaseReward {

    public enum RewardType {
        COMMAND,   // Виконати команду (видача привілею, кіт тощо)
        ITEM,      // Видати предмет
        TIMED_PERM // Видати тимчасовий привілей
    }

    private final String id;
    private final String displayName;
    private final RewardType rewardType;
    private final int weight; // чим більше — тим частіше випадає
    private final List<String> commands; // {player} замінюється на нік
    private final String timedGroup;     // для TIMED_PERM: назва групи LuckPerms
    private final long timedDurationMs;  // для TIMED_PERM: тривалість у мілісекундах

    public CaseReward(String id, String displayName, RewardType rewardType,
                      int weight, List<String> commands,
                      String timedGroup, long timedDurationMs) {
        this.id = id;
        this.displayName = displayName;
        this.rewardType = rewardType;
        this.weight = weight;
        this.commands = commands;
        this.timedGroup = timedGroup;
        this.timedDurationMs = timedDurationMs;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public RewardType getRewardType() { return rewardType; }
    public int getWeight() { return weight; }
    public List<String> getCommands() { return commands; }
    public String getTimedGroup() { return timedGroup; }
    public long getTimedDurationMs() { return timedDurationMs; }
}
