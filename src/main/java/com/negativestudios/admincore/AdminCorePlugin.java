package com.negativestudios.admincore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BlockIterator;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class AdminCorePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> incognito = new HashSet<>();
    private final Set<UUID> hiddenNick = new HashSet<>();
    private final Map<UUID, String> nicknames = new HashMap<>();
    private final Map<UUID, Material> signMaterials = new HashMap<>();
    private final Map<UUID, Inventory> openedInventories = new HashMap<>();
    private final Map<UUID, UUID> inventoryOwners = new HashMap<>();
    private final Map<UUID, Inventory> openedEnderChests = new HashMap<>();
    private final Map<UUID, UUID> enderChestOwners = new HashMap<>();

    private File dataFile;
    private YamlConfiguration data;
    private final Map<String, String> placedBlocks = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        String[] commands = {"vanish", "nick", "incognito", "hidenick", "blockinfo", "invsee", "ecsee", "sign", "setsign"};
        for (String name : commands) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreVisuals(player);
        }
        getLogger().info("AdminCorePlugin activado.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : data.getConfigurationSection("placed-blocks") == null
                ? Collections.<String>emptyList()
                : data.getConfigurationSection("placed-blocks").getKeys(false)) {
            placedBlocks.put(key, data.getString("placed-blocks." + key));
        }

        for (String key : data.getConfigurationSection("sign-defaults") == null
                ? Collections.<String>emptyList()
                : data.getConfigurationSection("sign-defaults").getKeys(false)) {
            try {
                signMaterials.put(UUID.fromString(key), Material.valueOf(data.getString("sign-defaults." + key)));
            } catch (Exception ignored) {
            }
        }
    }

    private void saveData() {
        if (data == null) return;
        for (Map.Entry<String, String> entry : placedBlocks.entrySet()) {
            data.set("placed-blocks." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, Material> entry : signMaterials.entrySet()) {
            data.set("sign-defaults." + entry.getKey(), entry.getValue().name());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("No se pudo guardar data.yml: " + e.getMessage());
        }
    }

    private boolean isAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cEste comando solo puede usarlo un jugador."));
            return false;
        }
        if (!player.isOp()) {
            player.sendMessage(color("&cNo tienes permiso para usar esta función."));
            return false;
        }
        return true;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void message(Player player, String text) {
        player.sendMessage(color("&8[&bAdminCore&8] " + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAdmin(sender)) return true;
        Player player = (Player) sender;

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "vanish" -> toggleVanish(player);
            case "nick" -> handleNick(player, args);
            case "incognito" -> toggleIncognito(player);
            case "hidenick" -> toggleHiddenNick(player);
            case "blockinfo" -> showBlockInfo(player);
            case "invsee" -> openInventory(player, args);
            case "ecsee" -> openEnderChest(player, args);
            case "sign" -> giveSign(player, args);
            case "setsign" -> setSign(player, args);
        }
        return true;
    }

    private void toggleVanish(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            for (Player target : Bukkit.getOnlinePlayers()) target.showPlayer(this, player);
            message(player, "&aVanish desactivado.");
        } else {
            vanished.add(player.getUniqueId());
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.isOp() || target.equals(player)) target.hidePlayer(this, player);
            }
            message(player, "&aVanish activado. &7Los jugadores no OP no podrán verte.");
        }
    }

    private void handleNick(Player player, String[] args) {
        if (args.length == 0) {
            message(player, "&eUso: &f/nick <nombre|off>");
            return;
        }

        if (args[0].equalsIgnoreCase("off")) {
            nicknames.remove(player.getUniqueId());
            applyName(player);
            message(player, "&aNombre personalizado desactivado.");
            return;
        }

        String nickname = String.join(" ", args);
        if (nickname.length() > 16) {
            message(player, "&cEl nombre no puede superar 16 caracteres.");
            return;
        }

        nicknames.put(player.getUniqueId(), nickname);
        incognito.remove(player.getUniqueId());
        applyName(player);
        message(player, "&aAhora apareces como &f" + nickname + "&a.");
    }

    private void toggleIncognito(Player player) {
        UUID uuid = player.getUniqueId();
        if (incognito.remove(uuid)) {
            applyName(player);
            message(player, "&aModo incógnito desactivado.");
            return;
        }

        incognito.add(uuid);
        nicknames.remove(uuid);
        applyName(player);
        message(player, "&aModo incógnito activado.");
    }

    private String randomIncognitoName() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_";
        StringBuilder result = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            result.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return result.toString();
    }

    private void toggleHiddenNick(Player player) {
        UUID uuid = player.getUniqueId();
        if (hiddenNick.remove(uuid)) {
            setNameTagVisible(player, true);
            message(player, "&aTu nickname vuelve a mostrarse sobre tu cabeza.");
        } else {
            hiddenNick.add(uuid);
            setNameTagVisible(player, false);
            message(player, "&aTu nickname está oculto sobre tu cabeza.");
        }
    }

    private void setNameTagVisible(Player player, boolean visible) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "ac_" + player.getUniqueId().toString().replace("-", "").substring(0, 12);
        Team team = board.getTeam(teamName);

        if (team == null) team = board.registerNewTeam(teamName);
        if (!team.getEntries().contains(player.getName())) team.addEntry(player.getName());
        team.setOption(Team.Option.NAME_TAG_VISIBILITY,
                visible ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER);
    }

    private void applyName(Player player) {
        UUID uuid = player.getUniqueId();
        String shown;

        if (incognito.contains(uuid)) {
            shown = randomIncognitoName();
        } else {
            shown = nicknames.getOrDefault(uuid, player.getName());
        }

        player.setDisplayName(shown);
        player.setPlayerListName(shown);
    }

    private void showBlockInfo(Player player) {
        Block block = getTargetBlock(player, 100);
        if (block == null || block.getType().isAir()) {
            message(player, "&cNo estás mirando un bloque válido.");
            return;
        }

        String key = locationKey(block);
        String owner = placedBlocks.get(key);

        if (owner == null) {
            message(player, "&eNo tengo registrado quién colocó este bloque.");
            return;
        }

        message(player, "&7Bloque: &f" + block.getType().name());
        message(player, "&7Colocado por: &b" + owner);
    }

    private Block getTargetBlock(Player player, int maxDistance) {
        BlockIterator iterator = new BlockIterator(player, maxDistance);
        Block last = null;
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (!block.getType().isAir()) return block;
            last = block;
        }
        return last;
    }

    private String locationKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        placedBlocks.put(locationKey(block), player.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this, () -> {
            restoreVisuals(player);
            for (UUID uuid : vanished) {
                Player vanishedPlayer = Bukkit.getPlayer(uuid);
                if (vanishedPlayer != null && !player.isOp() && !player.equals(vanishedPlayer)) {
                    player.hidePlayer(this, vanishedPlayer);
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        vanished.remove(uuid);
        incognito.remove(uuid);
        hiddenNick.remove(uuid);
        nicknames.remove(uuid);
    }

    private void restoreVisuals(Player player) {
        applyName(player);
        if (hiddenNick.contains(player.getUniqueId())) setNameTagVisible(player, false);
    }

    private void openInventory(Player viewer, String[] args) {
        if (args.length != 1) {
            message(viewer, "&eUso: &f/invsee <jugador>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            message(viewer, "&cEl jugador debe estar conectado para abrir su inventario en tiempo real.");
            message(viewer, "&7La edición de inventarios offline requiere acceso NMS específico de 1.20.1.");
            return;
        }

        Inventory inventory = target.getInventory();
        viewer.openInventory(inventory);
        openedInventories.put(viewer.getUniqueId(), inventory);
        inventoryOwners.put(viewer.getUniqueId(), target.getUniqueId());
        message(viewer, "&aInventario de &f" + target.getName() + "&a abierto.");
    }

    private void openEnderChest(Player viewer, String[] args) {
        if (args.length != 1) {
            message(viewer, "&eUso: &f/ecsee <jugador>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            message(viewer, "&cEl jugador debe estar conectado para abrir su ender chest en tiempo real.");
            message(viewer, "&7La edición offline requiere acceso NMS específico de 1.20.1.");
            return;
        }

        Inventory inventory = target.getEnderChest();
        viewer.openInventory(inventory);
        openedEnderChests.put(viewer.getUniqueId(), inventory);
        enderChestOwners.put(viewer.getUniqueId(), target.getUniqueId());
        message(viewer, "&aEnder chest de &f" + target.getName() + "&a abierto.");
    }

    private void giveSign(Player player, String[] args) {
        if (args.length == 0) {
            message(player, "&eUso: &f/sign \"mensaje\"");
            return;
        }

        String text = String.join(" ", args);
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            text = text.substring(1, text.length() - 1);
        }

        Material material = signMaterials.getOrDefault(player.getUniqueId(), Material.OAK_SIGN);
        ItemStack sign = new ItemStack(material);
        var meta = sign.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&bLetrero: &f" + text));
            meta.setLore(Collections.singletonList(color("&7Mensaje: &f" + text)));
            sign.setItemMeta(meta);
        }

        player.getInventory().addItem(sign);
        message(player, "&aHas recibido un &f" + material.name() + "&a.");
    }

    private void setSign(Player player, String[] args) {
        if (args.length != 1) {
            message(player, "&eUso: &f/setsign <material>");
            return;
        }

        String input = args[0].toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(input);

        if (material == null || !material.name().endsWith("_SIGN")) {
            message(player, "&cEse material no es un letrero válido.");
            return;
        }

        signMaterials.put(player.getUniqueId(), material);
        saveData();
        message(player, "&aTu letrero predeterminado ahora es &f" + material.name() + "&a.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) return Collections.emptyList();

        String name = command.getName().toLowerCase(Locale.ROOT);
        if ((name.equals("invsee") || name.equals("ecsee") || name.equals("nick")) && args.length == 1) {
            List<String> result = new ArrayList<>();
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(target.getName());
                }
            }
            if (name.equals("nick") && "off".startsWith(args[0].toLowerCase(Locale.ROOT))) result.add("off");
            return result;
        }

        if (name.equals("setsign") && args.length == 1) {
            List<String> result = new ArrayList<>();
            for (Material material : Material.values()) {
                if (material.name().endsWith("_SIGN") &&
                        material.name().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(material.name().toLowerCase(Locale.ROOT));
                }
            }
            return result;
        }

        return Collections.emptyList();
    }
}
