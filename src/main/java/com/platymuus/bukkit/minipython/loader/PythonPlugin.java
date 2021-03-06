package com.platymuus.bukkit.minipython.loader;

import com.avaje.ebean.EbeanServer;
import com.platymuus.bukkit.minipython.MiniPythonPlugin;
import com.platymuus.bukkit.minipython.loader.context.PluginContext;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.*;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A loaded Python plugin.
 */
public class PythonPlugin extends PluginBase {

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
    PluginContext context;
    PythonInterpreter interpreter;

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

    public final File getFile() {
        return file;
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
        } catch (IOException ex) {
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
            // todo: update this method for UTF-8 awareness changes
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

    /**
     * Gets the command with the given name, specific to this plugin. Commands
     * need to be registered in the {@link PluginDescriptionFile#getCommands()
     * PluginDescriptionFile} to exist at runtime.
     *
     * @param name name or alias of the command
     * @return the plugin command if found, otherwise null
     */
    public PluginCommand getCommand(String name) {
        // From JavaPlugin
        String alias = name.toLowerCase();
        PluginCommand command = getServer().getPluginCommand(alias);

        if (command == null || command.getPlugin() != this) {
            command = getServer().getPluginCommand(desc.getName().toLowerCase() + ":" + alias);
        }

        if (command != null && command.getPlugin() == this) {
            return command;
        } else {
            return null;
        }
    }

    // Internals

    final void initialize(PythonLoader loader, Server server, File file, File dataFolder, PluginDescriptionFile desc, PluginContext context, PythonInterpreter interp) {
        if (initialized) return;
        initialized = true;

        this.loader = loader;
        this.server = server;
        this.file = file;
        this.dataFolder = dataFolder;
        this.desc = desc;
        this.context = context;
        this.interpreter = interp;

        configFile = new File(dataFolder, "config.yml");
    }

    final void setEnabled(final boolean enabled) {
        if (isEnabled != enabled) {
            isEnabled = enabled;

            if (isEnabled) {
                onEnable();
            } else {
                onDisable();

                // delete all the locals
                if (MiniPythonPlugin.mashUpJython) {
                    PyObject obj = interpreter.getLocals();
                    List<PyObject> list = new ArrayList<>();
                    for (PyObject key : obj.asIterable()) {
                        list.add(key);
                    }
                    for (PyObject key : list) {
                        obj.__delitem__(key);
                    }
                    interpreter.setLocals(Py.newStringMap());
                }
            }
        }
    }
}
