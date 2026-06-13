package ua.donatecases.managers;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import ua.donatecases.DonateCases;
import ua.donatecases.models.*;

import java.util.*;

public class CaseManager {

    // Тривалості для тимчасових кейсів
    public static final long TIMED_LONG_MS  = 21L * 24 * 60 * 60 * 1000; // 3 тижні
    public static final long TIMED_SHORT_MS =  3L * 24 * 60 * 60 * 1000; // 3 дні

    private final DonateCases plugin;
    private final ConfigManager configManager;

    // Кейси: назва → об'єкт
    private final Map<String, DonateCase> cases = new LinkedHashMap<>();

    // Активні тимчасові привілеї: uuid → об'єкт
    private final Map<UUID, TimedPermission> timedPermissions = new LinkedHashMap<>();

    // Очікування прив'язки: uuid гравця → назва кейсу
    private final Map<UUID, String> pendingLink = new HashMap<>();

    // Кейси за локацією блоку: "world,x,y,z" → назва кейсу
    private final Map<String, String> locationIndex = new HashMap<>();

    public CaseManager(DonateCases plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void load() {
        cases.clear();
        cases.putAll(configManager.loadAllCases());

        // Будуємо індекс локацій
        locationIndex.clear();
        for (DonateCase dc : cases.values()) {
            if (dc.getBlockLocation() != null) {
                locationIndex.put(locationKey(dc.getBlockLocation()), dc.getName());
            }
        }

        timedPermissions.clear();
        timedPermissions.putAll(configManager.loadAllTimedPermissions());
    }

    // ─────── CRUD кейсів ───────

    public boolean caseExists(String name) {
        return cases.containsKey(name.toLowerCase());
    }

    public DonateCase getCase(String name) {
        return cases.get(name.toLowerCase());
    }

    public Collection<DonateCase> getAllCases() {
        return cases.values();
    }

    /** Створює новий кейс з дефолтними нагородами для типу */
    public DonateCase createCase(String name, CaseType type) {
        List<CaseReward> defaultRewards = buildDefaultRewards(type);
        DonateCase dc = new DonateCase(name, type, defaultRewards);
        cases.put(name.toLowerCase(), dc);
        configManager.saveCase(dc);
        return dc;
    }

    /** Прив'язати кейс до блоку */
    public boolean linkCaseToBlock(String caseName, Block block) {
        if (!isCaseBlock(block.getType())) return false;
        DonateCase dc = getCase(caseName);
        if (dc == null) return false;

        // Видаляємо старий індекс якщо є
        if (dc.getBlockLocation() != null) {
            locationIndex.remove(locationKey(dc.getBlockLocation()));
        }

        dc.setBlockLocation(block.getLocation());
        locationIndex.put(locationKey(block.getLocation()), caseName.toLowerCase());
        configManager.saveCase(dc);
        return true;
    }

    public DonateCase getCaseByBlock(Block block) {
        String key = locationKey(block.getLocation());
        String name = locationIndex.get(key);
        return name != null ? cases.get(name) : null;
    }

    // ─────── Відкриття кейсу ───────

    public void openCase(Player player, DonateCase dc) {
        // Перевірка дозволеного світу (Multiverse сумісність)
        List<String> allowedWorlds = configManager.getAllowedWorlds();
        if (!allowedWorlds.isEmpty() &&
                !allowedWorlds.contains(player.getWorld().getName())) {
            player.sendMessage(configManager.getMessage("world_not_allowed"));
            return;
        }

        // Перевірка дозволу на тип кейсу
        String permNode = "donatecases.open." + dc.getType().getId();
        if (!player.hasPermission(permNode)) {
            player.sendMessage(configManager.getMessage("no_permission"));
            return;
        }

        // Анімація
        playOpenAnimation(player, dc);

        // Вибір нагороди
        CaseReward reward = dc.rollReward();
        giveReward(player, dc, reward);
    }

    private void giveReward(Player player, DonateCase dc, CaseReward reward) {
        switch (reward.getRewardType()) {
            case COMMAND -> {
                for (String cmd : reward.getCommands()) {
                    String finalCmd = cmd.replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                }
                Map<String, String> ph = new HashMap<>();
                ph.put("reward", reward.getDisplayName());
                String msgKey = switch (dc.getType()) {
                    case KITS -> "reward_kit";
                    case WHALE -> "reward_whale";
                    default -> "reward_privilege";
                };
                player.sendMessage(configManager.getMessage(msgKey, ph));
            }
            case TIMED_PERM -> {
                long duration = reward.getTimedDurationMs() > 0
                        ? reward.getTimedDurationMs()
                        : (dc.getType() == CaseType.TIMED_SHORT ? TIMED_SHORT_MS : TIMED_LONG_MS);

                long expiry = System.currentTimeMillis() + duration;
                TimedPermission tp = new TimedPermission(player.getUniqueId(), reward.getTimedGroup(), expiry);
                timedPermissions.put(player.getUniqueId(), tp);
                configManager.saveTimedPermission(tp);

                // Видаємо групу через LuckPerms / Vault
                grantTimedGroup(player, reward.getTimedGroup());

                Map<String, String> ph = new HashMap<>();
                ph.put("group", reward.getTimedGroup());
                ph.put("duration", tp.getFormattedRemaining());
                player.sendMessage(configManager.getMessage("reward_timed", ph));
            }
            case ITEM -> {
                // Видача предметів через команди або InventoryAPI (команди зручніше)
                for (String cmd : reward.getCommands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            cmd.replace("{player}", player.getName()));
                }
                Map<String, String> ph = new HashMap<>();
                ph.put("reward", reward.getDisplayName());
                player.sendMessage(configManager.getMessage("reward_privilege", ph));
            }
        }
    }

    // ─────── Тимчасові привілеї ───────

    public void checkExpiredPermissions() {
        Iterator<Map.Entry<UUID, TimedPermission>> iter = timedPermissions.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, TimedPermission> entry = iter.next();
            TimedPermission tp = entry.getValue();
            if (tp.isExpired()) {
                revokeTimedGroup(entry.getKey(), tp.getGroup());
                configManager.removeTimedPermission(entry.getKey());
                iter.remove();

                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    Map<String, String> ph = new HashMap<>();
                    ph.put("group", tp.getGroup());
                    p.sendMessage(configManager.getMessage("timed_expired", ph));
                }
            } else {
                // Попередження за 1 годину до кінця
                long remaining = tp.getRemainingMs();
                if (remaining < 3600_000L && remaining > 3540_000L) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && p.isOnline()) {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("group", tp.getGroup());
                        ph.put("time", tp.getFormattedRemaining());
                        p.sendMessage(configManager.getMessage("timed_expire_soon", ph));
                    }
                }
            }
        }
    }

    private void grantTimedGroup(Player player, String group) {
        Permission perms = plugin.getVaultPermission();
        if (perms != null) {
            perms.playerAddGroup(player.getWorld().getName(), player.getName(), group);
        } else {
            // Фолбек через LuckPerms command
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " parent add " + group);
        }
    }

    private void revokeTimedGroup(UUID uuid, String group) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) return;
        Permission perms = plugin.getVaultPermission();
        if (perms != null) {
            // Vault не підтримує оффлайн — фолбек
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + name + " parent remove " + group);
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + name + " parent remove " + group);
        }
    }

    // ─────── Pending link ───────

    public void setPendingLink(UUID uuid, String caseName) {
        pendingLink.put(uuid, caseName);
    }

    public String getPendingLink(UUID uuid) {
        return pendingLink.get(uuid);
    }

    public void removePendingLink(UUID uuid) {
        pendingLink.remove(uuid);
    }

    // ─────── Helpers ───────

    public boolean isCaseBlock(Material mat) {
        return mat == Material.CHEST
                || mat == Material.TRAPPED_CHEST
                || mat == Material.SHULKER_BOX
                || mat == Material.ENDER_CHEST
                || isMaterialShulkerBox(mat);
    }

    private boolean isMaterialShulkerBox(Material mat) {
        return mat.name().endsWith("_SHULKER_BOX");
    }

    private String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void playOpenAnimation(Player player, DonateCase dc) {
        // Ефекти при відкритті
        if (dc.getBlockLocation() != null) {
            Location loc = dc.getBlockLocation().clone().add(0.5, 1.2, 0.5);
            player.getWorld().spawnParticle(
                    org.bukkit.Particle.FIREWORK, loc, 40, 0.3, 0.3, 0.3, 0.1);
        }
        player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }
    /** Дефолтні нагороди для кожного типу кейсу */
    private List<CaseReward> buildDefaultRewards(CaseType type) {
        List<CaseReward> list = new ArrayList<>();
        switch (type) {
            case PRIVILEGE -> {
                list.add(new CaseReward("vip", "VIP привілей", CaseReward.RewardType.COMMAND, 50,
                        List.of("lp user {player} parent add vip"), null, 0));
                list.add(new CaseReward("premium", "Premium привілей", CaseReward.RewardType.COMMAND, 30,
                        List.of("lp user {player} parent add premium"), null, 0));
                list.add(new CaseReward("elite", "Elite привілей", CaseReward.RewardType.COMMAND, 20,
                        List.of("lp user {player} parent add elite"), null, 0));
            }
            case WHALE -> {
                list.add(new CaseReward("kit_whale", "Кіт Кита", CaseReward.RewardType.COMMAND, 40,
                        List.of("kit give {player} whale"), null, 0));
                list.add(new CaseReward("money_whale", "$50000", CaseReward.RewardType.COMMAND, 35,
                        List.of("eco give {player} 50000"), null, 0));
                list.add(new CaseReward("rank_whale", "Ранг Whale", CaseReward.RewardType.COMMAND, 25,
                        List.of("lp user {player} parent add whale"), null, 0));
            }
            case TIMED_LONG -> {
                list.add(new CaseReward("vip_3weeks", "VIP на 3 тижні", CaseReward.RewardType.TIMED_PERM, 50,
                        List.of(), "vip", TIMED_LONG_MS));
                list.add(new CaseReward("premium_3weeks", "Premium на 3 тижні", CaseReward.RewardType.TIMED_PERM, 30,
                        List.of(), "premium", TIMED_LONG_MS));
                list.add(new CaseReward("elite_3weeks", "Elite на 3 тижні", CaseReward.RewardType.TIMED_PERM, 20,
                        List.of(), "elite", TIMED_LONG_MS));
            }
            case TIMED_SHORT -> {
                list.add(new CaseReward("vip_3days", "VIP на 3 дні", CaseReward.RewardType.TIMED_PERM, 50,
                        List.of(), "vip", TIMED_SHORT_MS));
                list.add(new CaseReward("premium_3days", "Premium на 3 дні", CaseReward.RewardType.TIMED_PERM, 30,
                        List.of(), "premium", TIMED_SHORT_MS));
                list.add(new CaseReward("elite_3days", "Elite на 3 дні", CaseReward.RewardType.TIMED_PERM, 20,
                        List.of(), "elite", TIMED_SHORT_MS));
            }
            case KITS -> {
                list.add(new CaseReward("kit_starter", "Стартовий кіт", CaseReward.RewardType.COMMAND, 50,
                        List.of("kit give {player} starter"), null, 0));
                list.add(new CaseReward("kit_warrior", "Кіт Воїна", CaseReward.RewardType.COMMAND, 30,
                        List.of("kit give {player} warrior"), null, 0));
                list.add(new CaseReward("kit_legend", "Легендарний кіт", CaseReward.RewardType.COMMAND, 20,
                        List.of("kit give {player} legend"), null, 0));
            }
        }
        return list;
    }
}
