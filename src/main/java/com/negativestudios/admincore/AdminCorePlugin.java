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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> incognito = new HashSet<>();
    private final Set<UUID> hiddenNick = new HashSet<>();
    private final Set<UUID> vanishAll = new HashSet<>();
    private final Set<UUID> vanishTab = new HashSet<>();
    private final Map<UUID, String> nicknames = new HashMap<>();
    private final Map<UUID, Inventory> openedInventories = new HashMap<>();
    private final Map<UUID, UUID> inventoryOwners = new HashMap<>();
    private final Map<UUID, Inventory> openedEnderChests = new HashMap<>();
    private final Map<UUID, UUID> enderChestOwners = new HashMap<>();
    private final Map<UUID, Language> languages = new HashMap<>();

    private enum Language { SPANISH, ENGLISH }

    private File dataFile;
    private YamlConfiguration data;
    private final Map<String, String> placedBlocks = new HashMap<>();
    private final Map<String, Deque<ChestOpenRecord>> chestHistory = new HashMap<>();

    private record ChestOpenRecord(String player, long timestamp) {}

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();

        String[] commands = {"admincore", "vanish", "nick", "incognito", "hidenick", "blockinfo", "chesthistory", "invsee", "ecsee", "help", "plugins", "admin"};
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

        if (data.getConfigurationSection("chest-history") != null) {
            for (String key : data.getConfigurationSection("chest-history").getKeys(false)) {
                List<?> entries = data.getList("chest-history." + key);
                if (entries == null) continue;
                Deque<ChestOpenRecord> history = new ArrayDeque<>();
                for (Object entry : entries) {
                    if (!(entry instanceof Map<?, ?> map)) continue;
                    Object playerName = map.get("player");
                    Object timestamp = map.get("timestamp");
                    if (playerName instanceof String name && timestamp instanceof Number time) {
                        history.addLast(new ChestOpenRecord(name, time.longValue()));
                    }
                }
                while (history.size() > 10) history.removeFirst();
                if (!history.isEmpty()) chestHistory.put(key, history);
            }
        }

        if (data.getConfigurationSection("languages") != null) {
            for (String key : data.getConfigurationSection("languages").getKeys(false)) {
                try {
                    languages.put(UUID.fromString(key), Language.valueOf(data.getString("languages." + key)));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void saveData() {
        if (data == null) return;
        for (Map.Entry<String, String> entry : placedBlocks.entrySet()) {
            data.set("placed-blocks." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, Language> entry : languages.entrySet()) {
            data.set("languages." + entry.getKey(), entry.getValue().name());
        }

        for (Map.Entry<String, Deque<ChestOpenRecord>> entry : chestHistory.entrySet()) {
            List<Map<String, Object>> history = new ArrayList<>();
            for (ChestOpenRecord record : entry.getValue()) {
                Map<String, Object> saved = new LinkedHashMap<>();
                saved.put("player", record.player());
                saved.put("timestamp", record.timestamp());
                history.add(saved);
            }
            data.set("chest-history." + entry.getKey(), history);
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("No se pudo guardar data.yml: " + e.getMessage());
        }
    }

    private Language getLanguage(Player player) {
        return languages.getOrDefault(player.getUniqueId(), Language.SPANISH);
    }

    private boolean isEnglish(Player player) {
        return getLanguage(player) == Language.ENGLISH;
    }

    private boolean isAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cEste comando solo puede usarlo un jugador."));
            return false;
        }
        if (!player.isOp()) {
            player.sendMessage(color(isEnglish(player) ? "&cYou do not have permission to use this feature." : "&cNo tienes permiso para usar esta función."));
            return false;
        }
        return true;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void message(Player player, String spanish, String english) {
        message(player, isEnglish(player) ? english : spanish);
    }

    private void message(Player player, String text) {
        player.sendMessage(color("&8[&bAdminCore&8] " + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("admincore")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color("&cEste comando solo puede usarlo un jugador."));
                return true;
            }
            if (!player.isOp()) {
                player.sendMessage(color(isEnglish(player) ? "&cYou do not have permission to use this command." : "&cNo tienes permiso para usar este comando."));
                return true;
            }
            handleLanguage(player, args);
            return true;
        }

        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("help")) {
            handleAdminHelp(sender, args);
            return true;
        }

        if (commandName.equals("plugins")) {
            handlePlugins(sender);
            return true;
        }

        if (commandName.equals("admin")) {
            if (!isAdmin(sender)) return true;
            handleAdmin(playerFrom(sender), args);
            return true;
        }

        if (!isAdmin(sender)) return true;
        Player player = (Player) sender;

        switch (commandName) {
            case "vanish" -> handleVanish(player, args);
            case "nick" -> handleNick(player, args);
            case "incognito" -> toggleIncognito(player);
            case "hidenick" -> toggleHiddenNick(player);
            case "blockinfo" -> showBlockInfo(player);
            case "chesthistory" -> showChestHistory(player);
            case "invsee" -> openInventory(player, args);
            case "ecsee" -> openEnderChest(player, args);
        }
        return true;
    }


    private Player playerFrom(CommandSender sender) {
        return (Player) sender;
    }

    private void handleAdmin(Player player, String[] args) {
        if (args.length != 2 || (!args[0].equalsIgnoreCase("freeze") && !args[0].equalsIgnoreCase("unfreeze"))) {
            message(player, "&eUso: &f/admin freeze <jugador> &7o &f/admin unfreeze <jugador>",
                    "&eUsage: &f/admin freeze <player> &7or &f/admin unfreeze <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            message(player, "&cEse jugador no está conectado.", "&cThat player is not online.");
            return;
        }

        UUID uuid = target.getUniqueId();
        if (args[0].equalsIgnoreCase("freeze")) {
            if (frozen.add(uuid)) {
                message(player, "&aHas congelado a &f" + target.getName() + "&a.", "&aYou froze &f" + target.getName() + "&a.");
                message(target, "&cHas sido congelado por un administrador.", "&cYou have been frozen by an administrator.");
            } else {
                message(player, "&eEse jugador ya está congelado.", "&eThat player is already frozen.");
            }
        } else {
            if (frozen.remove(uuid)) {
                message(player, "&aHas descongelado a &f" + target.getName() + "&a.", "&aYou unfroze &f" + target.getName() + "&a.");
                message(target, "&aHas sido descongelado.", "&aYou have been unfrozen.");
            } else {
                message(player, "&eEse jugador no está congelado.", "&eThat player is not frozen.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!frozen.contains(player.getUniqueId())) return;

        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    private void handleAdminHelp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cEste comando solo puede usarlo un jugador."));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(color("&eUso: &f/help <mensaje>"));
            return;
        }

        String message = String.join(" ", args);
        String formatted = color("&a[" + player.getName() + "] dice: &f" + message);

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.isOp()) target.sendMessage(formatted);
        }
    }

    private void handlePlugins(CommandSender sender) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage(color("&cNo tienes permisos suficientes."));
            return;
        }

        List<String> pluginNames = new ArrayList<>();
        for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.isEnabled()) pluginNames.add(plugin.getName());
        }

        player.sendMessage(color("&aPlugins (" + pluginNames.size() + "): &f" + String.join("&7, &f", pluginNames)));
    }


    private void handleLanguage(Player player, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    message(player, "&cNúmero de página no válido.", "&cInvalid page number.");
                    return;
                }
            }
            showHelp(player, page);
            return;
        }

        if (args.length != 2 || !args[0].equalsIgnoreCase("lenguage")) {
            message(player,
                    "&eUso: &f/admincore lenguage <español|English>",
                    "&eUsage: &f/admincore lenguage <español|English>");
            return;
        }

        if (args[1].equalsIgnoreCase("español") || args[1].equalsIgnoreCase("spanish")) {
            languages.put(player.getUniqueId(), Language.SPANISH);
            saveData();
            message(player, "&aIdioma cambiado a &fEspañol&a.", "&aLanguage changed to &fSpanish&a.");
        } else if (args[1].equalsIgnoreCase("english")) {
            languages.put(player.getUniqueId(), Language.ENGLISH);
            saveData();
            message(player, "&aIdioma cambiado a &fEnglish&a.", "&aLanguage changed to &fEnglish&a.");
        } else {
            message(player,
                    "&cIdioma no válido. Usa &fEspañol &co &fEnglish&c.",
                    "&cInvalid language. Use &fEspañol &cor &fEnglish&c.");
        }
    }

    private void handleVanish(Player player, String[] args) {
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            if (vanished.remove(uuid)) {
                updateVanishVisibility(player);
                applyName(player);
                message(player, "&aVanish desactivado.", "&aVanish disabled.");
            } else {
                vanished.add(uuid);
                updateVanishVisibility(player);
                applyName(player);
                message(player, "&aVanish activado. &7Los jugadores no OP no podrán verte.",
                        "&aVanish enabled. &7Non-OP players will not be able to see you.");
            }
            return;
        }

        boolean hasAll = false;
        boolean hasTab = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("all")) hasAll = true;
            else if (arg.equalsIgnoreCase("tab")) hasTab = true;
            else {
                message(player,
                        "&eUso: &f/vanish &7| &f/vanish all &7| &f/vanish tab &7| &f/vanish all tab",
                        "&eUsage: &f/vanish &7| &f/vanish all &7| &f/vanish tab &7| &f/vanish all tab");
                return;
            }
        }

        if (hasAll && hasTab) {
            if (vanishAll.contains(uuid) && vanishTab.contains(uuid)) {
                vanishAll.remove(uuid);
                vanishTab.remove(uuid);
                message(player, "&aVisibilidad para OP y nombre en tab restaurados.",
                        "&aVisibility for OPs and tab name restored.");
            } else {
                vanishAll.add(uuid);
                vanishTab.add(uuid);
                message(player, "&aVanish configurado para ocultarte de todos y ocultar tu nombre en tab.",
                        "&aVanish configured to hide you from everyone and hide your tab name.");
            }
        } else if (hasAll) {
            if (vanishAll.remove(uuid)) {
                message(player, "&aAhora los OP pueden verte en vanish.", "&aOPs can now see you in vanish.");
            } else {
                vanishAll.add(uuid);
                message(player, "&aAhora nadie puede verte en vanish, ni siquiera los OP.",
                        "&aNow nobody can see you in vanish, including OPs.");
            }
        } else {
            if (vanishTab.remove(uuid)) {
                message(player, "&aTu nombre vuelve a mostrarse en el tabulador.",
                        "&aYour name is visible in the tab list again.");
            } else {
                vanishTab.add(uuid);
                message(player, "&aTu nombre está oculto en el tabulador.",
                        "&aYour name is hidden in the tab list.");
            }
        }

        updateVanishVisibility(player);
        applyName(player);
    }

    private void updateVanishVisibility(Player player) {
        UUID uuid = player.getUniqueId();
        if (!vanished.contains(uuid)) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                target.showPlayer(this, player);
            }
            return;
        }

        boolean hideFromOps = vanishAll.contains(uuid);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;
            if (hideFromOps || !target.isOp()) target.hidePlayer(this, player);
            else target.showPlayer(this, player);
        }
    }

    private void handleNick(Player player, String[] args) {
        if (args.length == 0) {
            message(player, "&eUso: &f/nick <nombre|off>", "&eUsage: &f/nick <name|off>");
            return;
        }

        if (args[0].equalsIgnoreCase("off")) {
            nicknames.remove(player.getUniqueId());
            applyName(player);
            message(player, "&aNombre personalizado desactivado.", "&aCustom name disabled.");
            return;
        }

        String nickname = String.join(" ", args);
        if (nickname.length() > 16) {
            message(player, "&cEl nombre no puede superar 16 caracteres.", "&cThe name cannot exceed 16 characters.");
            return;
        }

        nicknames.put(player.getUniqueId(), nickname);
        incognito.remove(player.getUniqueId());
        applyName(player);
        message(player, "&aAhora apareces como &f" + nickname + "&a.", "&aYou now appear as &f" + nickname + "&a.");
    }

    private void toggleIncognito(Player player) {
        UUID uuid = player.getUniqueId();
        if (incognito.remove(uuid)) {
            applyName(player);
            message(player, "&aModo incógnito desactivado.", "&aIncognito mode disabled.");
            return;
        }

        incognito.add(uuid);
        nicknames.remove(uuid);
        applyName(player);
        message(player, "&aModo incógnito activado.", "&aIncognito mode enabled.");
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
            message(player, "&aTu nickname vuelve a mostrarse sobre tu cabeza.", "&aYour nickname is visible above your head again.");
        } else {
            hiddenNick.add(uuid);
            setNameTagVisible(player, false);
            message(player, "&aTu nickname está oculto sobre tu cabeza.", "&aYour nickname is hidden above your head.");
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
        player.setPlayerListName(vanished.contains(uuid) && vanishTab.contains(uuid) ? "" : shown);
    }

    private void showHelp(Player player, int page) {
        String[][] spanish = {
                {"/vanish", "Ocultarte de jugadores que no sean OP."},
                {"/vanish all", "Ocultarte de todos, incluidos los OP."},
                {"/vanish tab", "Ocultar tu nombre del tabulador."},
                {"/vanish all tab", "Combinar ambas opciones de vanish."},
                {"/nick <nombre|off>", "Cambiar o restaurar tu nombre mostrado."},
                {"/incognito", "Usar un nombre aleatorio que cambia."},
                {"/hidenick", "Ocultar o mostrar tu nombre sobre la cabeza."},
                {"/blockinfo", "Mostrar quién colocó el bloque que miras."},
                {"/invsee <jugador>", "Abrir y editar el inventario de un jugador."},
                {"/ecsee <jugador>", "Abrir y editar el ender chest de un jugador."},
                {"/admincore lenguage <español|English>", "Cambiar tu idioma."},
                {"/admincore help [página]", "Mostrar la ayuda paginada."}
        };

        String[][] english = {
                {"/vanish", "Hide from non-OP players."},
                {"/vanish all", "Hide from everyone, including OPs."},
                {"/vanish tab", "Hide your name from the tab list."},
                {"/vanish all tab", "Combine both vanish options."},
                {"/nick <name|off>", "Change or restore your displayed name."},
                {"/incognito", "Use a changing random-style name."},
                {"/hidenick", "Hide or show your name above your head."},
                {"/blockinfo", "Show who placed the block you are looking at."},
                {"/invsee <player>", "Open and edit a player's inventory."},
                {"/ecsee <player>", "Open and edit a player's ender chest."},
                {"/admincore lenguage <español|English>", "Change your language."},
                {"/admincore help [page]", "Show the paginated help."}
        };

        String[][] commands = isEnglish(player) ? english : spanish;
        int perPage = 5;
        int totalPages = (int) Math.ceil(commands.length / (double) perPage);

        if (page < 1 || page > totalPages) {
            message(player,
                    "&c&l✦ &cLa página debe estar entre &f&l1 &cy &f&l" + totalPages + "&c.",
                    "&c&l✦ &cThe page must be between &f&l1 &cand &f&l" + totalPages + "&c.");
            return;
        }

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, commands.length);

        player.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(color("&b&l✦ ADMINCORE &8» &f&lAYUDA &7(" + page + "/" + totalPages + ")"));
        player.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        for (int i = start; i < end; i++) {
            player.sendMessage(color("&8▸ &b&l" + commands[i][0] + " &8— &7" + commands[i][1]));
        }

        player.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        if (page > 1 && page < totalPages) {
            player.sendMessage(color("&7« &f&l/admincore help " + (page - 1)
                    + " &8| &f&l/admincore help " + (page + 1) + " &7»"));
        } else if (page > 1) {
            player.sendMessage(color("&7« &f&l/admincore help " + (page - 1)));
        } else if (page < totalPages) {
            player.sendMessage(color("&f&l/admincore help " + (page + 1) + " &7»"));
        } else {
            player.sendMessage(color("&8• &7No hay más comandos en esta página."));
        }

        player.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void showChestHistory(Player player) {
        Block block = getTargetBlock(player, 100);
        if (block == null || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) {
            message(player, "&cDebes estar mirando un cofre válido.", "&cYou must be looking at a valid chest.");
            return;
        }

        Deque<ChestOpenRecord> history = chestHistory.get(locationKey(block));
        player.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(color("&b&l✦ ADMINCORE &8» &f&lHISTORIAL DEL COFRE"));
        player.sendMessage(color("&7Cofre: &f" + block.getType().name()));

        if (history == null || history.isEmpty()) {
            player.sendMessage(color("&7No hay aperturas registradas para este cofre."));
        } else {
            int number = 1;
            List<ChestOpenRecord> entries = new ArrayList<>(history);
            Collections.reverse(entries);
            for (ChestOpenRecord record : entries) {
                String time = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date(record.timestamp()));
                player.sendMessage(color("&8▸ &b#" + number + " &f" + record.player() + " &8— &7" + time));
                number++;
            }
        }

        player.sendMessage(color("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory() instanceof DoubleChestInventory doubleChest)) {
            if (event.getInventory().getHolder() instanceof org.bukkit.block.Chest chest) {
                recordChestOpen(chest.getBlock(), player);
            }
            return;
        }

        if (doubleChest.getLeftSide() instanceof org.bukkit.block.Chest left) {
            recordChestOpen(left.getBlock(), player);
        }
        if (doubleChest.getRightSide() instanceof org.bukkit.block.Chest right) {
            recordChestOpen(right.getBlock(), player);
        }
    }

    private void recordChestOpen(Block block, Player player) {
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;

        String key = locationKey(block);
        Deque<ChestOpenRecord> history = chestHistory.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        history.addLast(new ChestOpenRecord(player.getName(), System.currentTimeMillis()));
        while (history.size() > 10) history.removeFirst();

        saveData();
    }

    private void showBlockInfo(Player player) {
        Block block = getTargetBlock(player, 100);
        if (block == null || block.getType().isAir()) {
            message(player, "&cNo estás mirando un bloque válido.", "&cYou are not looking at a valid block.");
            return;
        }

        String key = locationKey(block);
        String owner = placedBlocks.get(key);

        if (owner == null) {
            message(player, "&eNo tengo registrado quién colocó este bloque.", "&eI do not have a record of who placed this block.");
            return;
        }

        message(player, "&7Bloque: &f" + block.getType().name(), "&7Block: &f" + block.getType().name());
        message(player, "&7Colocado por: &b" + owner, "&7Placed by: &b" + owner);
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
                if (vanishedPlayer != null) updateVanishVisibility(vanishedPlayer);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        vanished.remove(uuid);
        vanishAll.remove(uuid);
        vanishTab.remove(uuid);
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
            message(viewer, "&eUso: &f/invsee <jugador>", "&eUsage: &f/invsee <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            message(viewer, "&cEl jugador debe estar conectado para abrir su inventario en tiempo real.", "&cThe player must be online to open their inventory in real time.");
            message(viewer, "&7La edición de inventarios offline requiere acceso NMS específico de 1.20.1.", "&7Offline inventory editing requires 1.20.1-specific NMS access.");
            return;
        }

        Inventory inventory = target.getInventory();
        viewer.openInventory(inventory);
        openedInventories.put(viewer.getUniqueId(), inventory);
        inventoryOwners.put(viewer.getUniqueId(), target.getUniqueId());
        message(viewer, "&aInventario de &f" + target.getName() + "&a abierto.", "&aInventory of &f" + target.getName() + "&a opened.");
    }

    private void openEnderChest(Player viewer, String[] args) {
        if (args.length != 1) {
            message(viewer, "&eUso: &f/ecsee <jugador>", "&eUsage: &f/ecsee <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            message(viewer, "&cEl jugador debe estar conectado para abrir su ender chest en tiempo real.", "&cThe player must be online to open their ender chest in real time.");
            message(viewer, "&7La edición offline requiere acceso NMS específico de 1.20.1.", "&7Offline editing requires 1.20.1-specific NMS access.");
            return;
        }

        Inventory inventory = target.getEnderChest();
        viewer.openInventory(inventory);
        openedEnderChests.put(viewer.getUniqueId(), inventory);
        enderChestOwners.put(viewer.getUniqueId(), target.getUniqueId());
        message(viewer, "&aEnder chest de &f" + target.getName() + "&a abierto.", "&aEnder chest of &f" + target.getName() + "&a opened.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) return Collections.emptyList();

        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("admincore") && args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String option : Arrays.asList("lenguage", "help")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(option);
            }
            return result;
        }

        if (name.equals("admincore") && args.length == 2 && args[0].equalsIgnoreCase("help")) {
            List<String> result = new ArrayList<>();
            for (int page = 1; page <= 3; page++) {
                String option = String.valueOf(page);
                if (option.startsWith(args[1])) result.add(option);
            }
            return result;
        }

        if (name.equals("vanish") && args.length >= 1) {
            List<String> result = new ArrayList<>();
            for (String option : Arrays.asList("all", "tab")) {
                if (option.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT)) && !Arrays.asList(args).contains(option)) {
                    result.add(option);
                }
            }
            return result;
        }

        if (name.equals("chesthistory")) {
            return Collections.emptyList();
        }

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

        if (name.equals("admin") && args.length == 2) {
            if (args[0].equalsIgnoreCase("freeze") || args[0].equalsIgnoreCase("unfreeze")) {
                List<String> result = new ArrayList<>();
                for (Player target : Bukkit.getOnlinePlayers()) {
                    if (target.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(target.getName());
                    }
                }
                return result;
            }
            return Collections.emptyList();
        }

        if (name.equals("admin") && args.length == 1) {
            List<String> result = new ArrayList<>();
            for (String option : Arrays.asList("freeze", "unfreeze")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(option);
            }
            return result;
        }

        return Collections.emptyList();
    }
}
