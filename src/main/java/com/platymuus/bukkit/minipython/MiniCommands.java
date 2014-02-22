package com.platymuus.bukkit.minipython;

import com.platymuus.bukkit.minipython.loader.PythonLoader;
import com.platymuus.bukkit.minipython.loader.PythonPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class MiniCommands implements CommandExecutor {

    private MiniPythonPlugin plugin;

    public MiniCommands(MiniPythonPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minipython.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Please provide a subcommand: " + ChatColor.WHITE + "load, unload, reload, reloadall");
            return true;
        }

        if (args[0].equalsIgnoreCase("load")) {
            if (!checkArgLen(sender, args)) return true;
            load(sender, args[1]);
        } else if (args[0].equalsIgnoreCase("unload")) {
            if (!checkArgLen(sender, args)) return true;
            unload(sender, args[1]);
        } else if (args[0].equalsIgnoreCase("reload")) {
            if (!checkArgLen(sender, args)) return true;
            refresh(sender, args[1]);
        } else if (args[0].equalsIgnoreCase("reloadall")) {
            reload(sender);
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid subcommand " + ChatColor.WHITE + args[0] + ChatColor.RED + ", try /" + label);
        }
        return true;
    }

    private boolean checkArgLen(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "You must provide a plugin name or filename");
            return false;
        }
        return true;
    }

    private void load(CommandSender sender, String arg) {
        File file = new File("plugins", arg);
        if (!file.exists()) {
            String[] alts = {".py"};
            File newFile = null;
            int found = 0;
            for (String alt : alts) {
                File check = new File(file.getPath() + alt);
                if (check.exists()) {
                    newFile = check;
                    ++found;
                }
            }

            if (found == 1) {
                file = newFile;
            } else if (found == 0) {
                sender.sendMessage(ChatColor.RED + "File " + ChatColor.WHITE + arg + ChatColor.RED + " does not exist.");
                return;
            } else {
                sender.sendMessage(ChatColor.RED + "Multiple files matching " + ChatColor.WHITE + arg + ChatColor.RED + " exist.");
                return;
            }
        }
        arg = file.getName();

        boolean okay = false;
        for (Pattern filter : PythonLoader.PATTERNS) {
            if (filter.matcher(file.getName()).find()) {
                okay = true;
                break;
            }
        }
        if (!okay) {
            sender.sendMessage(ChatColor.RED + "File " + ChatColor.WHITE + arg + ChatColor.RED + " is not a Python plugin.");
            return;
        }

        for (Plugin pl : plugin.getServer().getPluginManager().getPlugins()) {
            if (pl instanceof PythonPlugin) {
                if (((PythonPlugin) pl).getFile().equals(file)) {
                    sender.sendMessage(ChatColor.RED + "File " + ChatColor.WHITE + arg + ChatColor.RED + " is already loaded (" + ChatColor.WHITE + pl.getName() + ChatColor.RED + ").");
                    return;
                }
            }
        }

        Plugin pl;
        try {
            pl = plugin.getServer().getPluginManager().loadPlugin(file);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to load " + ChatColor.WHITE + arg + ChatColor.RED + ":");
            sender.sendMessage(ChatColor.RED + e.getClass().getSimpleName() + ": " + ChatColor.WHITE + e.getMessage());
            e.printStackTrace();
            return;
        }

        plugin.getServer().getPluginManager().enablePlugin(pl);

        sender.sendMessage(ChatColor.GREEN + "Successfully loaded " + ChatColor.WHITE + pl.getName() + ChatColor.GREEN + " from " + ChatColor.WHITE + arg);
    }

    private void unload(CommandSender sender, String arg) {
        Plugin pl = plugin.getServer().getPluginManager().getPlugin(arg);
        if (pl == null) {
            sender.sendMessage(ChatColor.RED + "The plugin " + ChatColor.WHITE + arg + ChatColor.RED + " could not be found.");
            return;
        } else if (!(pl instanceof PythonPlugin)) {
            sender.sendMessage(ChatColor.RED + "The plugin " + ChatColor.WHITE + pl.getName() + ChatColor.RED + " is not a Python plugin.");
            return;
        }

        PythonPlugin pyPlugin = (PythonPlugin) pl;
        try {
            _unload(pyPlugin);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to unload " + ChatColor.WHITE + pyPlugin.getName() + ChatColor.RED + ":");
            sender.sendMessage(ChatColor.RED + e.getClass().getSimpleName() + ": " + ChatColor.WHITE + e.getMessage());
            e.printStackTrace();
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Successfully unloaded " + ChatColor.WHITE + pl.getName());
    }

    private void refresh(CommandSender sender, String arg) {
        Plugin pl = plugin.getServer().getPluginManager().getPlugin(arg);
        if (pl == null) {
            sender.sendMessage(ChatColor.RED + "The plugin " + ChatColor.WHITE + arg + ChatColor.RED + " could not be found.");
            return;
        } else if (!(pl instanceof PythonPlugin)) {
            sender.sendMessage(ChatColor.RED + "The plugin " + ChatColor.WHITE + pl.getName() + ChatColor.RED + " is not a Python plugin.");
            return;
        }

        PythonPlugin pyPlugin = (PythonPlugin) pl;
        File file = pyPlugin.getFile();

        try {
            _unload(pyPlugin);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to unload " + ChatColor.WHITE + pyPlugin.getName() + ChatColor.RED + ":");
            sender.sendMessage(ChatColor.RED + e.getClass().getSimpleName() + ": " + ChatColor.WHITE + e.getMessage());
            e.printStackTrace();
            return;
        }

        try {
            pl = plugin.getServer().getPluginManager().loadPlugin(file);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to load " + ChatColor.WHITE + file.getName() + ChatColor.RED + ":");
            sender.sendMessage(ChatColor.RED + e.getClass().getSimpleName() + ": " + ChatColor.WHITE + e.getMessage());
            e.printStackTrace();
            return;
        }

        plugin.getServer().getPluginManager().enablePlugin(pl);

        sender.sendMessage(ChatColor.GREEN + "Successfully reloaded " + ChatColor.WHITE + pl.getName() + ChatColor.GREEN + " from " + ChatColor.WHITE + file.getName());
    }

    @SuppressWarnings("unchecked")
    private void _unload(PythonPlugin pl) throws Exception {
        PluginManager man = plugin.getServer().getPluginManager();
        if (!(man instanceof SimplePluginManager)) {
            throw new ClassCastException("cannot unload plugins from a non-SimplePluginManager");
        }

        SimplePluginManager pm = (SimplePluginManager) man;

        pm.disablePlugin(pl);
        HandlerList.unregisterAll(pl);
        for (Permission perm : pl.getDescription().getPermissions()) {
            pm.removePermission(perm);
        }

        SimpleCommandMap cmdMap = (SimpleCommandMap) Reflection.getPrivateValue(pm, "commandMap");
        Map<String, Command> knownCommands = (Map<String, Command>) Reflection.getPrivateValue(cmdMap, "knownCommands");
        if (pl.getDescription().getCommands() != null) {
            for (String command : pl.getDescription().getCommands().keySet()) {
                Command cmd = cmdMap.getCommand(command);
                for (String alias : cmd.getAliases()) {
                    knownCommands.remove(alias);
                }
                for (String key : new ArrayList<String>(knownCommands.keySet())) {
                    if (knownCommands.get(key) == cmd) {
                        knownCommands.remove(key);
                    }
                }
                cmd.unregister(cmdMap);
            }
        }

        List<Plugin> pluginList = (List<Plugin>) Reflection.getPrivateValue(pm, "plugins");
        pluginList.remove(pl);
        Map<String, Plugin> lookupNames = (Map<String, Plugin>) Reflection.getPrivateValue(pm, "lookupNames");
        lookupNames.remove(pl.getName());
    }

    private void reload(CommandSender sender) {
        for (Plugin pl : plugin.getServer().getPluginManager().getPlugins()) {
            if (pl instanceof PythonPlugin) {
                refresh(sender, pl.getName());
            }
        }
    }

}
