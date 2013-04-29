package com.platymuus.bukkit.minipython.loader;

import com.avaje.ebean.EbeanServer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

/**
 * A loaded Python plugin.
 */
public class PythonPlugin implements Plugin {

    private File dataFolder;

    public File getDataFolder() {
        return dataFolder;
    }

    public PluginDescriptionFile getDescription() {
        return null;
    }

    public FileConfiguration getConfig() {
        return null;
    }

    public InputStream getResource(String filename) {
        return null;
    }

    public void saveConfig() {
    }

    public void saveDefaultConfig() {
    }

    public void saveResource(String resourcePath, boolean replace) {
    }

    public void reloadConfig() {
    }

    public PluginLoader getPluginLoader() {
        return null;
    }

    public Server getServer() {
        return null;
    }

    public boolean isEnabled() {
        return false;
    }

    public void onDisable() {
    }

    public void onLoad() {
    }

    public void onEnable() {
    }

    public boolean isNaggable() {
        return false;
    }

    public void setNaggable(boolean canNag) {
    }

    public EbeanServer getDatabase() {
        return null;
    }

    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return null;
    }

    public Logger getLogger() {
        return null;
    }

    public String getName() {
        return null;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null;
    }

}
