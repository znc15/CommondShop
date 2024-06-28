package com.littlesheep;

import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class commondshop extends JavaPlugin implements Listener {

    private Economy economy;
    private PlayerPointsAPI playerPointsAPI;
    private FileConfiguration config;
    private FileConfiguration langConfig;
    private Map<String, String> messages = new HashMap<>();
    private Map<UUID, String> pendingPurchases = new HashMap<>();
    private static final String UPDATE_URL = "https://api.tcbmc.cc/update/commondshop/update.json";
    private static final String UPDATE_PAGE = "https://github.com/znc15/commondshop";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        loadLanguageFile();

        if (!setupEconomy()) {
            getLogger().severe("Vault plugin not found or no economy provider found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupPlayerPoints()) {
            getLogger().severe("PlayerPoints plugin not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);

        if (config.getBoolean("update-check", true)) {
            checkForUpdates();
        }

        if (config.getBoolean("enable-metrics", true)) {
            int pluginId = 22430; // bStats 插件ID
            Metrics metrics = new Metrics(this, pluginId);
            getLogger().info("bStats metrics enabled.");
        }

        printStartupMessage();
        getLogger().info("CommondShop plugin enabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault plugin not found.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No economy provider found.");
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private boolean setupPlayerPoints() {
        PlayerPoints playerPoints = (PlayerPoints) getServer().getPluginManager().getPlugin("PlayerPoints");
        if (playerPoints == null) {
            return false;
        }
        playerPointsAPI = new PlayerPointsAPI(playerPoints);
        return playerPointsAPI != null;
    }

    private void loadLanguageFile() {
        String lang = config.getString("language", "zh_cn");
        String langFileName = "lang/" + lang + ".yml";
        File langFile = new File(getDataFolder(), langFileName);
        if (!langFile.exists()) {
            saveResource(langFileName, false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
        for (String key : langConfig.getKeys(false)) {
            messages.put(key, ChatColor.translateAlternateColorCodes('&', langConfig.getString(key)));
        }
    }

    private void checkForUpdates() {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL(UPDATE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                conn.disconnect();

                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(content.toString());
                String latestVersion = (String) json.get("version");
                String currentVersion = getDescription().getVersion();

                if (latestVersion != null && !currentVersion.equals(latestVersion)) {
                    getLogger().info(ChatColor.RED + "插件需要更新！最新版本: " + latestVersion + "，当前版本: " + currentVersion);
                    getLogger().info("更新地址: " + UPDATE_PAGE);
                } else {
                    getLogger().info(ChatColor.GREEN + "您的插件为最新版本。");
                }
            } catch (Exception e) {
                getLogger().severe("无法检查更新: " + e.getMessage());
            }
        });
    }

    private void printStartupMessage() {
        String author = getDescription().getAuthors().get(0);
        getLogger().info("==========================================");
        getLogger().info(getDescription().getName());
        getLogger().info("Version/版本: " + getDescription().getVersion());
        getLogger().info("Author/作者: " + String.join(", ", getDescription().getAuthors()));
        getLogger().info("QQ Group/QQ群: 690216634");
        getLogger().info("Github: https://github.com/znc15/commondshop");
        getLogger().info("-v-");
        getLogger().info("==========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("CommondShop plugin disabled.");
    }

    private String getMessage(String key) {
        return messages.getOrDefault(key, key);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("shop")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.hasPermission("commondshop.reload")) {
                        player.sendMessage(getMessage("no_permission_message"));
                        return true;
                    }
                }
                reloadConfig();
                config = getConfig();
                loadLanguageFile();
                sender.sendMessage(getMessage("reload_success_message"));
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(getMessage("only_players_message"));
                return true;
            }

            Player player = (Player) sender;
            if (args.length == 1) {
                String itemName = args[0];

                if (!config.contains(itemName)) {
                    player.sendMessage(getMessage("item_not_found_message"));
                    return true;
                }

                pendingPurchases.put(player.getUniqueId(), itemName);
                player.sendMessage(getMessage("enter_amount_message"));
            } else {
                player.sendMessage(getMessage("usage_message"));
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (pendingPurchases.containsKey(playerId)) {
            event.setCancelled(true);
            String itemName = pendingPurchases.get(playerId);
            int amount;
            try {
                amount = Integer.parseInt(event.getMessage());
            } catch (NumberFormatException e) {
                player.sendMessage(getMessage("invalid_amount_message"));
                return;
            }

            int price = config.getInt(itemName + ".price") * amount;
            String currency = config.getString(itemName + ".currency");
            String command = config.getString(itemName + ".command");

            boolean transactionSuccess = false;
            if ("points".equalsIgnoreCase(currency)) {
                if (playerPointsAPI.take(player.getUniqueId(), price)) {
                    transactionSuccess = true;
                } else {
                    player.sendMessage(getMessage("not_enough_points_message"));
                }
            } else if ("money".equalsIgnoreCase(currency)) {
                if (economy.withdrawPlayer(player, price).transactionSuccess()) {
                    transactionSuccess = true;
                } else {
                    player.sendMessage(getMessage("not_enough_money_message"));
                }
            }

            if (transactionSuccess) {
                ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                String formattedCommand = command.replace("{amount}", String.valueOf(amount)).replace("{player}", player.getName());
                Bukkit.dispatchCommand(console, formattedCommand);
                player.sendMessage(getMessage("purchase_successful_message"));
                pendingPurchases.remove(playerId);
            }
        }
    }
}
