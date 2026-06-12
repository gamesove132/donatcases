package ua.donatecases;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ua.donatecases.commands.CaseCommand;
import ua.donatecases.listeners.BlockInteractListener;
import ua.donatecases.managers.CaseManager;
import ua.donatecases.managers.ConfigManager;

public class DonateCases extends JavaPlugin {

    private ConfigManager configManager;
    private CaseManager caseManager;
    private Permission vaultPermission;

    @Override
    public void onEnable() {
        // Менеджери
        configManager = new ConfigManager(this);
        configManager.load();

        caseManager = new CaseManager(this, configManager);
        caseManager.load();

        // Vault
        setupVault();

        // Команди
        CaseCommand caseCmd = new CaseCommand(this);
        getCommand("case").setExecutor(caseCmd);
        getCommand("case").setTabCompleter(caseCmd);

        // Слухачі
        Bukkit.getPluginManager().registerEvents(new BlockInteractListener(this), this);

        // Тиккер для перевірки тимчасових привілеїв (кожну хвилину)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> Bukkit.getScheduler().runTask(this,
                        () -> caseManager.checkExpiredPermissions()),
                1200L, 1200L);

        getLogger().info("DonateCases увімкнено! Кейсів завантажено: "
                + caseManager.getAllCases().size());
    }

    @Override
    public void onDisable() {
        getLogger().info("DonateCases вимкнено.");
    }

    public void reload() {
        configManager.load();
        caseManager.load();
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault не знайдено — групи видаватимуться через LuckPerms команди.");
            return;
        }
        RegisteredServiceProvider<Permission> rsp =
                Bukkit.getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            vaultPermission = rsp.getProvider();
            getLogger().info("Vault Permission підключено: " + vaultPermission.getName());
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public CaseManager getCaseManager() { return caseManager; }
    public Permission getVaultPermission() { return vaultPermission; }
}
