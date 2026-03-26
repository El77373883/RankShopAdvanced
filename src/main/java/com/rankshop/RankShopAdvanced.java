package com.rankshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RankShopAdvanced extends JavaPlugin implements Listener {
    
    private static RankShopAdvanced instance;
    private List<RankData> ranks;
    private Map<String, Long> activeTokens;
    private String serverIp = "";
    private int serverPort = 25565;
    
    @Override
    public void onEnable() {
        instance = this;
        ranks = new ArrayList<>();
        activeTokens = new HashMap<>();
        
        detectServerInfo();
        
        getCommand("rsa").setExecutor(new AdminCommand());
        getCommand("rsatoken").setExecutor(new TokenCommand());
        getCommand("tienda").setExecutor(new ShopCommand());
        
        getServer().getPluginManager().registerEvents(this, this);
        
        loadRanks();
        
        getLogger().info("=========================================");
        getLogger().info("  RankShopAdvanced v1.0.0");
        getLogger().info("  IP detectada: " + serverIp + ":" + serverPort);
        getLogger().info("  ¡Plugin iniciado!");
        getLogger().info("=========================================");
    }
    
    private void detectServerInfo() {
        try {
            URL url = new URL("https://api.ipify.org");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                serverIp = reader.readLine();
            }
            serverPort = getServer().getPort();
        } catch (Exception e) {
            try {
                serverIp = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                serverIp = "localhost";
            }
        }
    }
    
    private void loadRanks() {
        if (getConfig().contains("ranks")) {
            // Cargar desde config
        } else {
            ranks.add(new RankData(1, "VIP", 9.99, "30 días", "👑", 
                "Vuelo, /workbench, Tag [VIP]",
                "give {player} diamond 5", "&a"));
            ranks.add(new RankData(2, "VIP+", 19.99, "30 días", "🪙",
                "Todo VIP + /feed, /heal",
                "give {player} gold 10", "&6"));
            ranks.add(new RankData(3, "MVP", 34.99, "Permanente", "⭐",
                "Todo VIP+ + Doble XP, Partículas",
                "give {player} emerald 5", "&b"));
            saveRanks();
        }
    }
    
    private void saveRanks() {
        getConfig().set("ranks", null);
        for (int i = 0; i < ranks.size(); i++) {
            RankData r = ranks.get(i);
            getConfig().set("ranks." + i + ".id", r.id);
            getConfig().set("ranks." + i + ".name", r.name);
            getConfig().set("ranks." + i + ".price", r.price);
            getConfig().set("ranks." + i + ".duration", r.duration);
            getConfig().set("ranks." + i + ".icon", r.icon);
            getConfig().set("ranks." + i + ".benefits", r.benefits);
            getConfig().set("ranks." + i + ".commands", r.commands);
            getConfig().set("ranks." + i + ".color", r.color);
        }
        saveConfig();
    }
    
    public String generateToken(Player player) {
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        activeTokens.put(token, System.currentTimeMillis() + (30 * 60 * 1000));
        
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL("https://el77373883.github.io/Dashboard-RankShopAdvanced/api/register");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                String json = String.format(
                    "{\"token\":\"%s\",\"ip\":\"%s\",\"port\":%d,\"serverName\":\"%s\"}",
                    token, serverIp, serverPort, getServer().getName()
                );
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                
                getLogger().info("✅ Servidor registrado - Token: " + token);
            } catch (Exception e) {
                getLogger().warning("Error: " + e.getMessage());
            }
        });
        
        return token;
    }
    
    public void openRankShop(Player player) {
        if (ranks.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No hay rangos");
            return;
        }
        
        int size = (int) Math.ceil(ranks.size() / 9.0) * 9;
        if (size < 9) size = 9;
        
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.GOLD + "🏪 Tienda de Rangos");
        
        for (int i = 0; i < ranks.size(); i++) {
            RankData rank = ranks.get(i);
            ItemStack item = createRankItem(rank);
            inv.setItem(i, item);
        }
        
        player.openInventory(inv);
    }
    
    private ItemStack createRankItem(RankData rank) {
        Material material = getMaterialFromIcon(rank.icon);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', rank.color + rank.name));
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.YELLOW + "💰 Precio: " + ChatColor.WHITE + "$" + rank.price);
        lore.add(ChatColor.YELLOW + "⏰ Duración: " + ChatColor.WHITE + rank.duration);
        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.GREEN + "✨ Beneficios:");
        for (String b : rank.benefits.split(",")) {
            lore.add(ChatColor.WHITE + "  • " + b.trim());
        }
        lore.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.GOLD + "🖱️ Haz clic para comprar");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private Material getMaterialFromIcon(String icon) {
        switch (icon) {
            case "💎": return Material.DIAMOND;
            case "🪙": return Material.GOLD_INGOT;
            case "⭐": return Material.NETHER_STAR;
            case "👑": return Material.GOLDEN_HELMET;
            default: return Material.PAPER;
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.GOLD + "🏪 Tienda de Rangos")) return;
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        if (slot >= 0 && slot < ranks.size()) {
            RankData rank = ranks.get(slot);
            player.closeInventory();
            
            player.sendMessage(ChatColor.GREEN + "====================================");
            player.sendMessage(ChatColor.GOLD + "🏪 " + rank.name + ChatColor.GREEN + " - $" + rank.price);
            player.sendMessage(ChatColor.YELLOW + "✨ Beneficios:");
            for (String b : rank.benefits.split(",")) {
                player.sendMessage(ChatColor.WHITE + "  • " + b.trim());
            }
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Compra en: " + ChatColor.WHITE + "https://el77373883.github.io/Dashboard-RankShopAdvanced/");
            player.sendMessage(ChatColor.GREEN + "====================================");
        }
    }
    
    public static RankShopAdvanced getInstance() {
        return instance;
    }
    
    public static class RankData {
        public int id;
        public String name;
        public double price;
        public String duration;
        public String icon;
        public String benefits;
        public String commands;
        public String color;
        
        public RankData(int id, String name, double price, String duration, String icon, 
                        String benefits, String commands, String color
