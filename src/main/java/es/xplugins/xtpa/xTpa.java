package es.xplugins.xtpa;

import es.xplugins.xtpa.api.xTpaAPI;
import es.xplugins.xtpa.utils.ChatUtils;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class xTpa extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private final Map<UUID, Long> tpaTimes = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Map<UUID, Location> startTeleportLocations = new HashMap<>();

    @Override
    public void onEnable() {

        saveDefaultConfig();
        new xTpaAPI(this);
        getServer().getPluginManager().registerEvents(this, this);

        getCommand("tpa").setExecutor(this);
        getCommand("tpaccept").setExecutor(this);
        getCommand("tpdeny").setExecutor(this);
        getCommand("xtpa").setExecutor(this);

        xTpaTabCompleter completer = new xTpaTabCompleter(this);
        getCommand("tpa").setTabCompleter(completer);
        getCommand("xtpa").setTabCompleter(completer);
    }

    public Map<UUID, UUID> getTpaRequests() { return tpaRequests; }
    public Map<UUID, Long> getTpaTimes() { return tpaTimes; }

    private boolean checkPerm(CommandSender s, String path) {
        String perm = getConfig().getString(path);
        if (perm == null || perm.equalsIgnoreCase("default") || perm.equalsIgnoreCase("none")) return true;
        return s.hasPermission(perm) || s.isOp();
    }

    private void sendMsg(Player p, String path) {

        boolean center = getConfig().getBoolean("settings.center-messages");
        String prefix = getConfig().getString("messages.prefix", "");
        String msg = getConfig().getString("messages." + path, "");
        if (!msg.isEmpty()) {
            ChatUtils.sendMessage(p, prefix + msg, center);
        }
    }

    private void sendMsg(Player p, String path, String targetName) {

        boolean center = getConfig().getBoolean("settings.center-messages");
        String prefix = getConfig().getString("messages.prefix", "");
        String msg = getConfig().getString("messages." + path, "").replace("%xtpa_player%", targetName);
        if (!msg.isEmpty()) {
            ChatUtils.sendMessage(p, prefix + msg, center);
        }
    }

    @Override

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        boolean center = getConfig().getBoolean("settings.center-messages");

        if (cmd.getName().equalsIgnoreCase("xtpa")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload") && checkPerm(sender, "permissions.admin")) {
                reloadConfig();
                sender.sendMessage(ChatUtils.colorize("&a ✔ &fConfiguración recargada."));
                return true;
            }
            handleHelp(sender, center);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("tpa") && args.length > 0 && args[0].equalsIgnoreCase("help")) {
            handleHelp(sender, center);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Comando solo para jugadores.");
            return true;
        }

        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("tpa")) {

            if (!checkPerm(player, "permissions.tpa")) {

                sendMsg(player, "no-permission");
                return true;
            }

            if (args.length == 0) {

                handleHelp(player, center);
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sendMsg(player, "player-not-found");
                return true;
            }

            if (target.getUniqueId().equals(player.getUniqueId())) {

                sendMsg(player, "self-tpa");
                return true;
            }

            int cdSeconds = getConfig().getInt("settings.cooldown-seconds");

            if (!checkPerm(player, "permissions.bypass-cooldown") && cooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = ((cooldowns.get(player.getUniqueId()) / 1000) + cdSeconds) - (System.currentTimeMillis() / 1000);
                if (timeLeft > 0) {

                    String msg = getConfig().getString("messages.cooldown").replace("%xtpa_time%", String.valueOf(timeLeft));
                    ChatUtils.sendMessage(player, getConfig().getString("messages.prefix", "") + msg, center);
                    return true;
                }
            }

            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            xTpaAPI.getInstance().createRequest(player, target);

            sendMsg(player, "tpa-sent", target.getName());

            if (getConfig().getBoolean("visuals.title.enabled")) {

                target.sendTitle(

                        ChatUtils.colorize(getConfig().getString("visuals.title.title")),
                        ChatUtils.colorize(getConfig().getString("visuals.title.subtitle").replace("%xtpa_player%", player.getName())),
                        getConfig().getInt("visuals.title.fade-in"),
                        getConfig().getInt("visuals.title.stay"),
                        getConfig().getInt("visuals.title.fade-out")
                );
            }

            if (getConfig().getBoolean("visuals.actionbar.enabled")) {

                String ab = ChatUtils.colorize(getConfig().getString("visuals.actionbar.message").replace("%xtpa_player%", player.getName()));
                target.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ab));
            }

            playSound(target, "sounds.received-tpa");
            sendInteractiveMessage(target, player);
            return true;
        }

        // ACEPTAR/DENEGAR
        if (cmd.getName().equalsIgnoreCase("tpaccept") || cmd.getName().equalsIgnoreCase("tpdeny")) {
            if (!tpaRequests.containsKey(player.getUniqueId())) {

                sendMsg(player, "tpa-no-pending");
                return true;
            }

            long timeSent = tpaTimes.get(player.getUniqueId());
            long timeout = getConfig().getInt("settings.tpa-timeout-seconds") * 1000L;

            if (System.currentTimeMillis() - timeSent > timeout) {

                sendMsg(player, "tpa-expired");
                tpaRequests.remove(player.getUniqueId());
                tpaTimes.remove(player.getUniqueId());
                return true;
            }

            UUID senderUUID = tpaRequests.get(player.getUniqueId());
            Player senderPlayer = Bukkit.getPlayer(senderUUID);

            if (senderPlayer == null || !senderPlayer.isOnline()) {

                sendMsg(player, "player-not-found");
                tpaRequests.remove(player.getUniqueId());
                tpaTimes.remove(player.getUniqueId());
                return true;
            }

            if (cmd.getName().equalsIgnoreCase("tpaccept")) {

                if (!checkPerm(player, "permissions.tpaccept")) {

                    sendMsg(player, "no-permission");
                    return true;
                }
                handleTeleportAccept(player, senderPlayer);
            } else {
                if (!checkPerm(player, "permissions.tpdeny")) {

                    sendMsg(player, "no-permission");
                    return true;
                }
                sendMsg(player, "tpa-denied-target");
                sendMsg(senderPlayer, "tpa-denied-sender", player.getName());
                senderPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }

            tpaRequests.remove(player.getUniqueId());
            tpaTimes.remove(player.getUniqueId());
            return true;
        }

        return false;
    }

    private void handleHelp(CommandSender sender, boolean center) {

        String helpType = checkPerm(sender, "permissions.admin") ? "help.admin" : "help.user";
        List<String> helpList = getConfig().getStringList(helpType);

        for (String s : helpList) {
            if (sender instanceof Player) {
                ChatUtils.sendMessage((Player) sender, ChatUtils.colorize(s), center);
            } else {
                sender.sendMessage(ChatUtils.colorize(s));
            }
        }
    }

    private void sendInteractiveMessage(Player target, Player sender) {

        String time = String.valueOf(getConfig().getInt("settings.tpa-timeout-seconds"));
        boolean center = getConfig().getBoolean("settings.center-messages");
        String prefix = getConfig().getString("messages.prefix", "");

        String line1 = getConfig().getString("messages.received-request.line1").replace("%xtpa_player%", sender.getName());
        ChatUtils.sendMessage(target, prefix + line1, center);

        String line2 = ChatUtils.colorize(getConfig().getString("messages.received-request.line2-accept"));
        TextComponent acceptComp = new TextComponent(line2);
        acceptComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"));
        acceptComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatUtils.colorize(getConfig().getString("messages.received-request.hover-accept")))));
        target.spigot().sendMessage(acceptComp);

        String line3 = ChatUtils.colorize(getConfig().getString("messages.received-request.line3-deny"));
        TextComponent denyComp = new TextComponent(line3);
        denyComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"));
        denyComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatUtils.colorize(getConfig().getString("messages.received-request.hover-deny")))));
        target.spigot().sendMessage(denyComp);

        String line4 = getConfig().getString("messages.received-request.line4").replace("%xtpa_time%", time);
        ChatUtils.sendMessage(target, prefix + line4, center);
    }

    private void handleTeleportAccept(Player target, Player senderPlayer) {

        sendMsg(target, "tpa-accepted-target");
        sendMsg(senderPlayer, "tpa-accepted-sender", target.getName());

        int delaySeconds = getConfig().getInt("settings.teleport-delay-seconds");
        if (checkPerm(senderPlayer, "permissions.bypass-delay") || delaySeconds <= 0) {
            teleportFinal(senderPlayer, target);
            return;
        }

        UUID senderUuid = senderPlayer.getUniqueId();
        sendMsg(senderPlayer, "teleport-starting-sender", target.getName());

        String notice = getConfig().getString("messages.teleport-delay-notice-sender").replace("%xtpa_time%", String.valueOf(delaySeconds));
        ChatUtils.sendMessage(senderPlayer, getConfig().getString("messages.prefix", "") + notice, getConfig().getBoolean("settings.center-messages"));

        playSound(senderPlayer, "sounds.teleport-start");

        if (getConfig().getBoolean("settings.teleport-move-cancel")) {
            startTeleportLocations.put(senderUuid, senderPlayer.getLocation().getBlock().getLocation());
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {

            if (pendingTeleports.containsKey(senderUuid)) {

                if (!target.isOnline()) {
                    sendMsg(senderPlayer, "teleport-cancelled-quit");
                    cancelPendingTeleport(senderPlayer);
                    return;
                }
                teleportFinal(senderPlayer, target);
            }
        }, delaySeconds * 20L);

        pendingTeleports.put(senderUuid, task);
    }

    private void teleportFinal(Player senderPlayer, Player target) {

        senderPlayer.teleport(target.getLocation());
        playSound(senderPlayer, "sounds.teleport-success");
        sendMsg(senderPlayer, "teleport-success-sender", target.getName());

        pendingTeleports.remove(senderPlayer.getUniqueId());
        startTeleportLocations.remove(senderPlayer.getUniqueId());
    }

    private void cancelPendingTeleport(Player senderPlayer) {

        UUID uuid = senderPlayer.getUniqueId();
        if (pendingTeleports.containsKey(uuid)) {
            pendingTeleports.get(uuid).cancel();
            senderPlayer.playSound(senderPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            pendingTeleports.remove(uuid);
            startTeleportLocations.remove(uuid);
        }
    }

    private void playSound(Player player, String path) {

        if (getConfig().getBoolean(path + ".enabled")) {

            try {
                Sound s = Sound.valueOf(getConfig().getString(path + ".sound"));
                player.playSound(player.getLocation(), s, (float) getConfig().getDouble(path + ".volume"), (float) getConfig().getDouble(path + ".pitch"));
            } catch (Exception ignored) {}
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pendingTeleports.containsKey(uuid) && getConfig().getBoolean("settings.teleport-move-cancel")) {
            Location startLoc = startTeleportLocations.get(uuid);

            if (startLoc != null) {

                Location currentBlockLoc = player.getLocation().getBlock().getLocation();
                if (!startLoc.equals(currentBlockLoc)) {
                    sendMsg(player, "teleport-cancelled-moved");
                    cancelPendingTeleport(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        UUID uuid = event.getPlayer().getUniqueId();
        tpaRequests.remove(uuid);
        tpaTimes.remove(uuid);
        tpaRequests.values().removeIf(senderUuid -> senderUuid.equals(uuid));
        cooldowns.remove(uuid);

        if (pendingTeleports.containsKey(uuid)) {

            cancelPendingTeleport(event.getPlayer());
        }
    }
}