package ua.donatecases.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ua.donatecases.DonateCases;
import ua.donatecases.managers.CaseManager;
import ua.donatecases.managers.ConfigManager;
import ua.donatecases.models.*;

import java.util.*;

public class CaseCommand implements CommandExecutor, TabCompleter {

    private final DonateCases plugin;
    private final CaseManager caseManager;
    private final ConfigManager configManager;

    public CaseCommand(DonateCases plugin) {
        this.plugin = plugin;
        this.caseManager = plugin.getCaseManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(configManager.getMessage("usage_case"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "open"   -> handleOpen(sender, args);
            case "create" -> handleCreate(sender, args);
            case "list"   -> handleList(sender);
            case "give"   -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            default       -> sender.sendMessage(configManager.getMessage("usage_case"));
        }
        return true;
    }

    // ─────── /case open [назва] ───────
    private void handleOpen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("player_only"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(configManager.getMessage("usage_case"));
            return;
        }

        String name = args[1];
        DonateCase dc = caseManager.getCase(name);
        if (dc == null) {
            sender.sendMessage(configManager.getMessage("case_not_found",
                    Map.of("name", name)));
            return;
        }

        caseManager.openCase(player, dc);
    }

    // ─────── /case create <назва> <тип> ───────
    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("donatecases.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(configManager.getMessage("usage_create"));
            return;
        }

        String name = args[1];
        String typeStr = args[2].toLowerCase();
        CaseType type = CaseType.fromString(typeStr);

        if (type == null) {
            sender.sendMessage(configManager.getMessage("case_invalid_type"));
            return;
        }

        if (caseManager.caseExists(name)) {
            sender.sendMessage(configManager.getMessage("case_already_exists",
                    Map.of("name", name)));
            return;
        }

        caseManager.createCase(name, type);
        sender.sendMessage(configManager.getMessage("case_created",
                Map.of("name", name, "type", typeStr)));

        // Якщо адмін — гравець, ставимо в режим очікування прив'язки
        if (sender instanceof Player player) {
            caseManager.setPendingLink(player.getUniqueId(), name);
            player.sendMessage(configManager.getMessage("case_placed_hint"));
        }
    }

    // ─────── /case list ───────
    private void handleList(CommandSender sender) {
        Collection<DonateCase> all = caseManager.getAllCases();
        if (all.isEmpty()) {
            sender.sendMessage(configManager.getMessage("no_cases"));
            return;
        }

        sender.sendMessage(configManager.getMessage("case_list_header"));
        for (DonateCase dc : all) {
            String world = "—", x = "—", y = "—", z = "—";
            if (dc.getBlockLocation() != null) {
                world = dc.getBlockLocation().getWorld().getName();
                x = String.valueOf(dc.getBlockLocation().getBlockX());
                y = String.valueOf(dc.getBlockLocation().getBlockY());
                z = String.valueOf(dc.getBlockLocation().getBlockZ());
            }
            Map<String, String> ph = new HashMap<>();
            ph.put("name", dc.getName());
            ph.put("type", dc.getType().getId());
            ph.put("world", world);
            ph.put("x", x);
            ph.put("y", y);
            ph.put("z", z);
            sender.sendMessage(configManager.getMessage("case_list_entry", ph));
        }
    }

    // ─────── /case give <гравець> <кейс> ───────
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("donatecases.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(configManager.getMessage("usage_give"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(configManager.getMessage("give_no_player"));
            return;
        }

        String name = args[2];
        DonateCase dc = caseManager.getCase(name);
        if (dc == null) {
            sender.sendMessage(configManager.getMessage("case_not_found",
                    Map.of("name", name)));
            return;
        }

        // Безпосереднє відкриття кейсу для гравця
        caseManager.openCase(target, dc);
        sender.sendMessage(configManager.getMessage("give_success",
                Map.of("name", name, "player", target.getName())));
    }

    // ─────── /case reload ───────
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("donatecases.admin")) {
            sender.sendMessage(configManager.getMessage("no_permission"));
            return;
        }
        plugin.reload();
        sender.sendMessage(configManager.getMessage("reload_done"));
    }

    // ─────── Tab complete ───────
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("open", "list", "reload"));
            if (sender.hasPermission("donatecases.admin")) {
                subs.add("create");
                subs.add("give");
            }
            String input = args[0].toLowerCase();
            subs.stream().filter(s -> s.startsWith(input)).forEach(suggestions::add);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "open", "give" -> {
                    String input = args[1].toLowerCase();
                    caseManager.getAllCases().stream()
                            .map(DonateCase::getName)
                            .filter(n -> n.startsWith(input))
                            .forEach(suggestions::add);
                }
                case "create" -> suggestions.add("<назва_кейсу>");
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("create")) {
                String input = args[2].toLowerCase();
                for (CaseType t : CaseType.values()) {
                    if (t.getId().startsWith(input)) suggestions.add(t.getId());
                }
            } else if (args[0].equalsIgnoreCase("give")) {
                // гравці
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .forEach(suggestions::add);
            }
        }

        return suggestions;
    }
}
