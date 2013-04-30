package com.platymuus.bukkit.minipython.loader;

import com.platymuus.bukkit.minipython.loader.PythonPlugin;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.*;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The loader responsible for creating Python plugins.
 */
public class PythonLoader implements PluginLoader {

    /**
     * The list of patterns which correspond to Python plugins.
     */
    public static final Pattern[] PATTERNS = {
            Pattern.compile("\\.py$")
    };

    /**
     * The Bukkit server we're attached to.
     */
    private final Server server;

    /**
     * Construct the plugin loader.
     * @param server The Bukkit server.
     */
    public PythonLoader(Server server) {
        this.server = server;
    }

    public Pattern[] getPluginFileFilters() {
        return PATTERNS;
    }

    public PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
        return null;
    }

    public Plugin loadPlugin(File file) throws InvalidPluginException, UnknownDependencyException {
        return null;
    }

    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener, Plugin plugin) {
        return null;
    }

    public void enablePlugin(Plugin plugin) {
        PythonPlugin pyPlugin = (PythonPlugin) plugin;
        if (pyPlugin.isEnabled()) return;


    }

    public void disablePlugin(Plugin plugin) {
        PythonPlugin pyPlugin = (PythonPlugin) plugin;
        if (!pyPlugin.isEnabled()) return;


    }

}
