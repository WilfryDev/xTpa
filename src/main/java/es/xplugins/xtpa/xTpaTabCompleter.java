package es.xplugins.xtpa;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class xTpaTabCompleter implements TabCompleter {

    private final xTpa plugin;

    public xTpaTabCompleter(xTpa plugin) {
        this.plugin = plugin;
    }

    @Override

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("xtpa")) {

            if (args.length == 1) {

                String adminPerm = plugin.getConfig().getString("permissions.admin", "xtpa.admin");

                if (adminPerm.equalsIgnoreCase("default") || sender.hasPermission(adminPerm) || sender.isOp()) {
                    if ("reload".startsWith(args[0].toLowerCase())) {

                        completions.add("reload");
                    }
                }
            }
        }

        else if (command.getName().equalsIgnoreCase("tpa")) {

            if (args.length == 1) {

                for (Player p : Bukkit.getOnlinePlayers()) {

                    if (!p.getName().equalsIgnoreCase(sender.getName()) && p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }

        return completions;
    }
}