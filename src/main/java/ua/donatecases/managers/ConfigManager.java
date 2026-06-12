package ua.donatecases.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ua.donatecases.DonateCases;
import ua.donatecases.models.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigManager {

    private final DonateCases plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    // Separate file for cases
    private FileConfiguration casesConfig;
    private File casesFile;

    // Separate file for timed permissions
    private FileConfiguration timedConfig;
    private File timedFile;

    public ConfigManager(DonateCases plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        // Messages
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("messages.yml", false);
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Cases
        casesFile = new File(plugin.getDataFolder(), "cases.yml");
        if (!casesFile.exists()) {
            try { casesFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        casesConfig = YamlConfiguration.loadConfiguration(casesFile);

        // Timed permissions
        timedFile = new File(plugin.getDataFolder(), "timed_permissions.yml");
        if (!timedFile.exists()) {
            try { timedFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        timedConfig = YamlConfiguration.loadConfiguration(timedFile);
    }

    // ─────── Messages ───────

    public String getMessage(String key) {
        String raw = messagesConfig.getString(key, "&cMessage not found: " + key);
        return colorize(raw);
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String msg = getMessage(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return msg;
    }

    // ─────── Cases ───────

    public void saveCase(DonateCase dc) {
        String path = "cases." + dc.getName();
        casesConfig.set(path + ".type", dc.getType().getId());

        if (dc.getBlockLocation() != null) {
            Location loc = dc.getBlockLocation();
            casesConfig.set(path + ".world", loc.getWorld().getName());
            casesConfig.set(path + ".x", loc.getBlockX());
            casesConfig.set(path + ".y", loc.getBlockY());
            casesConfig.set(path + ".z", loc.getBlockZ());
        }

        // Save rewards
        List<Map<String, Object>> rewardList = new ArrayList<>();
        for (CaseReward r : dc.getRewards()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("display", r.getDisplayName());
            map.put("type", r.getRewardType().name());
            map.put("weight", r.getWeight());
            if (r.getCommands() != null && !r.getCommands().isEmpty())
                map.put("commands", r.getCommands());
            if (r.getTimedGroup() != null) {
                map.put("timed_group", r.getTimedGroup());
                map.put("timed_duration_ms", r.getTimedDurationMs());
            }
            rewardList.add(map);
        }
        casesConfig.set(path + ".rewards", rewardList);
        saveCasesFile();
    }

    public Map<String, DonateCase> loadAllCases() {
        Map<String, DonateCase> map = new LinkedHashMap<>();
        ConfigurationSection section = casesConfig.getConfigurationSection("cases");
        if (section == null) return map;

        for (String name : section.getKeys(false)) {
            String typStr = section.getString(name + ".type", "privilege");
            CaseType type = CaseType.fromString(typStr);
            if (type == null) type = CaseType.PRIVILEGE;

            List<CaseReward> rewards = loadRewards(section.getList(name + ".rewards"));
            DonateCase dc = new DonateCase(name, type, rewards);

            // Load block location
            String world = section.getString(name + ".world");
            if (world != null) {
                World w = Bukkit.getWorld(world);
                if (w != null) {
                    int x = section.getInt(name + ".x");
                    int y = section.getInt(name + ".y");
                    int z = section.getInt(name + ".z");
                    dc.setBlockLocation(new Location(w, x, y, z));
                }
            }
            map.put(name.toLowerCase(), dc);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<CaseReward> loadRewards(List<?> raw) {
        List<CaseReward> list = new ArrayList<>();
        if (raw == null) return list;
        for (Object obj : raw) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) obj;
            String id = (String) m.getOrDefault("id", "unknown");
            String display = (String) m.getOrDefault("display", id);
            String typeStr = (String) m.getOrDefault("type", "COMMAND");
            CaseReward.RewardType rType;
            try { rType = CaseReward.RewardType.valueOf(typeStr); }
            catch (Exception e) { rType = CaseReward.RewardType.COMMAND; }
            int weight = (int) m.getOrDefault("weight", 10);
            List<String> commands = (List<String>) m.getOrDefault("commands", new ArrayList<>());
            String timedGroup = (String) m.get("timed_group");
            long timedDuration = m.containsKey("timed_duration_ms")
                    ? ((Number) m.get("timed_duration_ms")).longValue() : 0L;
            list.add(new CaseReward(id, display, rType, weight, commands, timedGroup, timedDuration));
        }
        return list;
    }

    private void saveCasesFile() {
        try { casesConfig.save(casesFile); }
        catch (IOException e) { e.printStackTrace(); }
    }

    // ─────── Timed Permissions ───────

    public void saveTimedPermission(TimedPermission tp) {
        String path = "active." + tp.getPlayerUuid().toString();
        timedConfig.set(path + ".group", tp.getGroup());
        timedConfig.set(path + ".expiry", tp.getExpiryTimestamp());
        saveTimedFile();
    }

    public void removeTimedPermission(UUID uuid) {
        timedConfig.set("active." + uuid.toString(), null);
        saveTimedFile();
    }

    public Map<UUID, TimedPermission> loadAllTimedPermissions() {
        Map<UUID, TimedPermission> map = new LinkedHashMap<>();
        ConfigurationSection section = timedConfig.getConfigurationSection("active");
        if (section == null) return map;
        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String group = section.getString(uuidStr + ".group");
                long expiry = section.getLong(uuidStr + ".expiry");
                map.put(uuid, new TimedPermission(uuid, group, expiry));
            } catch (Exception ignored) {}
        }
        return map;
    }

    private void saveTimedFile() {
        try { timedConfig.save(timedFile); }
        catch (IOException e) { e.printStackTrace(); }
    }

    // ─────── Helpers ───────

    public List<String> getAllowedWorlds() {
        return plugin.getConfig().getStringList("allowed_worlds");
    }

    private String colorize(String s) {
        return s.replace("&", "§");
    }
}
