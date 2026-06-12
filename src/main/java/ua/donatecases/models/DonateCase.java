package ua.donatecases.models;

import org.bukkit.Location;
import java.util.List;

public class DonateCase {

    private final String name;
    private final CaseType type;
    private Location blockLocation; // прив'язаний блок (сундук/шалкер/ендер-сундук)
    private List<CaseReward> rewards;

    public DonateCase(String name, CaseType type, List<CaseReward> rewards) {
        this.name = name;
        this.type = type;
        this.rewards = rewards;
    }

    public String getName() { return name; }
    public CaseType getType() { return type; }
    public Location getBlockLocation() { return blockLocation; }
    public void setBlockLocation(Location loc) { this.blockLocation = loc; }
    public List<CaseReward> getRewards() { return rewards; }
    public void setRewards(List<CaseReward> rewards) { this.rewards = rewards; }

    /** Вибирає нагороду з урахуванням ваги */
    public CaseReward rollReward() {
        int total = rewards.stream().mapToInt(CaseReward::getWeight).sum();
        int roll = (int) (Math.random() * total);
        int cumulative = 0;
        for (CaseReward r : rewards) {
            cumulative += r.getWeight();
            if (roll < cumulative) return r;
        }
        return rewards.get(rewards.size() - 1);
    }
}
