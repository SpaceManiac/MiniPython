package com.platymuus.bukkit.minipython;

import com.platymuus.bukkit.minipython.loader.PythonLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * The hook plugin which installs the plugin loader
 */
public class MiniPythonPlugin extends JavaPlugin {

    public void onEnable() {
        getCommand("minipython").setExecutor(new MiniCommands(this));
    }

    public void onDisable() {
    }

    public void onLoad() {
        PluginManager pm = getServer().getPluginManager();

        // Add to Bukkit
        getLogger().info("Loading Python plugins...");
        pm.registerInterface(PythonLoader.class);

        // Must manually load plugins to avoid reloading existing Java plugins
        File[] list = this.getFile().getParentFile().listFiles();
        if (list == null) {
            getLogger().severe("Failed to search for Python plugins");
            return;
        }

        for (File file : list) {
            for (Pattern filter : PythonLoader.PATTERNS) {
                if (filter.matcher(file.getName()).find()) {
                    try {
                        pm.loadPlugin(file);
                    } catch (Exception e) {
                        getLogger().log(Level.SEVERE, "Could not load Python plugin " + file.getName(), e);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}

