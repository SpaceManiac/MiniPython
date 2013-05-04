package com.platymuus.bukkit.minipython.loader;

import com.avaje.ebean.EbeanServer;
import com.platymuus.bukkit.minipython.loader.context.PluginContext;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginLogger;

import java.io.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A loaded Python plugin.
 */
public class PythonPlugin implements Plugin {

    // Core attributes
    private boolean initialized = false;
    private PluginDescriptionFile desc;
    private PythonLoader loader;
    private Server server;
    private File file;
    private File dataFolder;
    private Logger logger;
    private boolean isEnabled;
    private boolean naggable;

    // Configuration
    private File configFile;
    private FileConfiguration config;

    // Internals
    private PluginContext context;

    // Core attribute getters

    public final Server getServer() {
        return server;
    }

    public final File getDataFolder() {
        return dataFolder;
    }

    public final PluginDescriptionFile getDescription() {
        return desc;
    }

    public final PluginLoader getPluginLoader() {
        return loader;
    }

    public final boolean isEnabled() {
        return isEnabled;
    }

    public final Logger getLogger() {
        if (logger == null) {
            logger = new PluginLogger(this);
        }
        return logger;
    }

    public final String getName() {
        return getDescription().getName();
    }

    public String toString() {
        return getDescription().getFullName();
    }

    // Configuration and resource management

    public InputStream getResource(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename may not be null");
        }

        try {
            return context.openStream(filename);
        }
        catch (IOException ex) {
            // Ignore any errors and return null
            return null;
        }
    }

    public void saveResource(String resourcePath, boolean replace) {
        // Copied wholesale from JavaPlugin

        if (resourcePath == null || resourcePath.equals("")) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) {
            throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in " + file);
        }

        File outFile = new File(dataFolder, resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(dataFolder, resourcePath.substring(0, lastIndex >= 0 ? lastIndex : 0));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                OutputStream out = new FileOutputStream(outFile);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();
            } else {
                logger.log(Level.WARNING, "Could not save " + outFile.getName() + " to " + outFile + " because " + outFile.getName() + " already exists.");
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, ex);
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defConfigStream = getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);

            config.setDefaults(defConfig);
        }
    }

    public void saveConfig() {
        try {
            getConfig().save(configFile);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
    }

    // Miscellaneous features

    public EbeanServer getDatabase() {
        // Maybe I'll care about this eventually
        return null;
    }

    public final boolean isNaggable() {
        return naggable;
    }

    public final void setNaggable(boolean canNag) {
        naggable = canNag;
    }

    // Overridable hooks

    public void onLoad() {
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        getServer().getLogger().severe("Plugin " + desc.getFullName() + " does not contain any generators that may be used in the default world!");
        return null;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null;
    }

    // Internals

    final void initialize(PythonLoader loader, Server server, File file, File dataFolder, PluginDescriptionFile desc, PluginContext context) {
        if (initialized) return;
        initialized = true;

        this.loader = loader;
        this.server = server;
        this.file = file;
        this.dataFolder = dataFolder;
        this.desc = desc;
        this.context = context;

        configFile = new File(dataFolder, "config.yml");
    }

    final void setEnabled(final boolean enabled) {
        if (isEnabled != enabled) {
            isEnabled = enabled;

            if (isEnabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

}
