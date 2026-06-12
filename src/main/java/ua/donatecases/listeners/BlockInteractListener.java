package ua.donatecases.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import ua.donatecases.DonateCases;
import ua.donatecases.managers.CaseManager;
import ua.donatecases.managers.ConfigManager;
import ua.donatecases.models.DonateCase;

import java.util.Map;

public class BlockInteractListener implements Listener {

    private final DonateCases plugin;
    private final CaseManager caseManager;
    private final ConfigManager configManager;

    public BlockInteractListener(DonateCases plugin) {
        this.plugin = plugin;
        this.caseManager = plugin.getCaseManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!caseManager.isCaseBlock(block.getType())) return;

        Player player = event.getPlayer();

        // ─── Режим прив'язки (адмін тільки що створив кейс) ───
        String pendingCase = caseManager.getPendingLink(player.getUniqueId());
        if (pendingCase != null && player.hasPermission("donatecases.admin")) {
            boolean linked = caseManager.linkCaseToBlock(pendingCase, block);
            if (linked) {
                caseManager.removePendingLink(player.getUniqueId());
                player.sendMessage(configManager.getMessage("case_linked",
                        Map.of("name", pendingCase)));
            } else {
                player.sendMessage(configManager.getMessage("case_not_block"));
            }
            event.setCancelled(true);
            return;
        }

        // ─── Відкриття кейсу за прив'язаним блоком ───
        DonateCase dc = caseManager.getCaseByBlock(block);
        if (dc == null) return;

        event.setCancelled(true); // не відкривати інвентар блоку
        caseManager.openCase(player, dc);
    }
}
