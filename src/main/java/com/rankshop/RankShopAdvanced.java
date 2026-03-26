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
        
        Objects.requireNonNull(getCommand("rsa")).setExecutor(new AdminCommand());
        Objects.requireNonNull(getCommand("rsatoken")).setExecutor(new TokenCommand());
        Objects.requireNonNull(getCommand("tienda")).setExecutor(new ShopCommand());
        
        getServer().getPluginManager().registerEvents(this, this);
        
        loadRanks();
        
        getLogger().info("=========================================");
        getLogger().info("  RankShopAdvanced v1.0.0");
        getLogger().info("  IP detectada: " + serverIp + ":" + serverPort);
        getLogger().info("  ¡Plugin iniciado!");
        getLogger().info("  Usa /tienda para abrir la tienda");
        getLogger().info("  Usa /rsatoken para generar token");
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
            getLogger().info("IP detectada: " + serverIp + ":" + serverPort);
        } catch (Exception e) {
            try {
                serverIp = InetAddress.getLocalHost().getHostAddress();
                getLogger().warning("Usando IP local: " + serverIp);
            } catch (Exception ex) {
                serverIp = "localhost";
                getLogger().warning("No se pudo detectar IP, usando localhost");
            }
        }
    }
    
    private void loadRanks() {
        if (getConfig().contains("ranks")) {
            for (String key : getConfig().getConfigurationSection("ranks").getKeys(false)) {
                int id = getConfig().getInt("ranks." + key + ".id");
                String name = getConfig().getString("ranks." + key + ".name");
                double price = getConfig().getDouble("ranks." + key + ".price");
                String duration = getConfig().getString("ranks." + key + ".duration");
                String icon = getConfig().getString("ranks." + key + ".icon");
                String benefits = getConfig().getString("ranks." + key + ".benefits");
                String commands = getConfig().getString("ranks." + key + ".commands");
                String color = getConfig().getString("ranks." + key + ".color");
                ranks.add(new RankData(id, name, price, duration, icon, benefits, commands, color));
            }
        } else {
            ranks.add(new RankData(1, "VIP", 9.99, "30 días", "👑", 
                "Vuelo en zonas seguras, /workbench, Tag [VIP] exclusivo",
                "give {player} diamond 5", "&a"));
            ranks.add(new RankData(2, "VIP+", 19.99, "30 días", "🪙",
                "Todo VIP + /feed, /heal, Tag [VIP+] dorado",
                "give {player} gold 10", "&6"));
            ranks.add(new RankData(3, "MVP", 34.99, "Permanente", "⭐",
                "Todo VIP+ + Doble XP, Partículas exclusivas",
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
    
    public void syncRanksFromPanel(List<RankData> newRanks) {
        ranks = newRanks;
        saveRanks();
        getLogger().info("Rangos sincronizados desde el panel: " + ranks.size() + " rangos");
        Bukkit.broadcastMessage(ChatColor.GREEN + "🏪 ¡La tienda de rangos ha sido actualizada!");
    }
    
    public String generateToken(Player player) {
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        activeTokens.put(token, System.currentTimeMillis() + (30 * 60 * 1000));
        registerWithPanel(player, token);
        return token;
    }
    
    private void registerWithPanel(Player player, String token) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL("https://el77373883.github.io/Dashboard-RankShopAdvanced/api/register");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                String json = String.format(
                    "{\"token\":\"%s\",\"ip\":\"%s\",\"port\":%d,\"serverName\":\"%s\",\"owner\":\"%s\"}",
                    token, serverIp, serverPort, getServer().getName(), player.getName()
                );
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                
                getLogger().info("✅ Servidor registrado en el panel web");
                getLogger().info("🔑 Token: " + token);
                
            } catch (Exception e) {
                getLogger().warning("Error al registrar en panel: " + e.getMessage());
            }
        });
    }
    
    public boolean validateToken(String token) {
        Long expiry = activeTokens.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            activeTokens.remove(token);
            return false;
        }
        return true;
    }
    
    public void openRankShop(Player player) {
        if (ranks.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No hay rangos disponibles");
            return;
        }
        
        int size = (int) Math.ceil(ranks.size() / 9.0) * 9;
        if (size < 9) size = 9;
        if (size > 54) size = 54;
        
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
        
        for (String benefit : rank.benefits.split(",")) {
            lore.add(ChatColor.WHITE + "  • " + benefit.trim());
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
            case "🔥": return Material.BLAZE_POWDER;
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
            
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "=========================================");
            player.sendMessage(ChatColor.GOLD + "🏪 " + rank.name + ChatColor.YELLOW + " - $" + rank.price);
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "✨ Beneficios:");
            for (String b : rank.benefits.split(",")) {
                player.sendMessage(ChatColor.WHITE + "  • " + b.trim());
            }
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "⏰ Duración: " + ChatColor.WHITE + rank.duration);
            player.sendMessage(ChatColor.GRAY + "Compra en: https://el77373883.github.io/Dashboard-RankShopAdvanced/");
            player.sendMessage(ChatColor.GOLD + "=========================================");
        }
    }
    
    private String getServerToken() {
        for (String token : activeTokens.keySet()) {
            return token;
        }
        return "NO_TOKEN";
    }
    
    public void executeRankCommands(Player player, RankData rank) {
        if (rank.commands == null || rank.commands.isEmpty()) return;
        for (String cmd : rank.commands.split(",")) {
            String command = cmd.trim().replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
        player.sendMessage(ChatColor.GREEN + "✅ ¡Has recibido el rango " + rank.name + ChatColor.GREEN + "!");
    }
    
    public static RankShopAdvanced getInstance() {
        return instance;
    }
    
    public List<RankData> getRanks() {
        return ranks;
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
                        String benefits, String commands, String color) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.duration = duration;
            this.icon = icon;
            this.benefits = benefits;
            this.commands = commands;
            this.color = color;
        }
    }
    
    public class TokenCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Solo jugadores");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("rankshop.admin")) {
                player.sendMessage(ChatColor.RED + "No tienes permiso");
                return true;
            }
            
            String token = generateToken(player);
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "=========================================");
            player.sendMessage(ChatColor.GREEN + "🔑 Token generado: " + ChatColor.WHITE + token);
            player.sendMessage(ChatColor.YELLOW + "🌐 Panel: https://el77373883.github.io/Dashboard-RankShopAdvanced/");
            player.sendMessage(ChatColor.GRAY + "⚠️ Válido por 30 minutos");
            player.sendMessage(ChatColor.GOLD + "=========================================");
            return true;
        }
    }
    
    public class ShopCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Solo jugadores");
                return true;
            }
            Player player = (Player) sender;
            openRankShop(player);
            return true;
        }
    }
    
    public class AdminCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!sender.hasPermission("rankshop.admin")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso");
                return true;
            }
            
            if (args.length == 0) {
                sender.sendMessage(ChatColor.GOLD + "=== RankShopAdvanced ===");
                sender.sendMessage(ChatColor.YELLOW + "/rsatoken " + ChatColor.GRAY + "- Generar token");
                sender.sendMessage(ChatColor.YELLOW + "/tienda " + ChatColor.GRAY + "- Abrir tienda");
                sender.sendMessage(ChatColor.YELLOW + "/rsa reload " + ChatColor.GRAY + "- Recargar");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadRanks();
                sender.sendMessage(ChatColor.GREEN + "✅ Configuración recargada");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("dar") && args.length >= 3) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Jugador no encontrado");
                    return true;
                }
                String rankName = args[2];
                RankData rank = ranks.stream().filter(r -> r.name.equalsIgnoreCase(rankName)).findFirst().orElse(null);
                if (rank == null) {
                    sender.sendMessage(ChatColor.RED + "Rango no encontrado");
                    return true;
                }
                executeRankCommands(target, rank);
                sender.sendMessage(ChatColor.GREEN + "✅ Rango dado a " + target.getName());
                return true;
            }
            
            return true;
        }
    }
}
