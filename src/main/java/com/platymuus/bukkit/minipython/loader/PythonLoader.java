package com.platymuus.bukkit.minipython.loader;

import com.platymuus.bukkit.minipython.loader.PythonPlugin;
import com.platymuus.bukkit.minipython.loader.context.DirectoryContext;
import com.platymuus.bukkit.minipython.loader.context.PluginContext;
import com.platymuus.bukkit.minipython.loader.context.SingleFileContext;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
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

    // Bukkit interface

    public Pattern[] getPluginFileFilters() {
        return PATTERNS;
    }

    public PluginDescriptionFile getPluginDescription(File file) throws InvalidDescriptionException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!file.exists()) {
            throw new InvalidDescriptionException(new FileNotFoundException(file.getPath() + " does not exist"));
        }

        PluginContext context = getContext(file);

        try {
            InputStream stream = context.openStream("plugin.yml");
            if (stream == null) {
                throw new InvalidDescriptionException("Plugin " + file.getName() + " contains no description");
            }
            PluginDescriptionFile desc = new PluginDescriptionFile(stream);
            stream.close();
            return desc;
        } catch (IOException e) {
            throw new InvalidDescriptionException("Plugin " + file.getName() + " contains no description");
        }
    }

    public Plugin loadPlugin(File file) throws InvalidPluginException, UnknownDependencyException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!file.exists()) {
            throw new InvalidPluginException(new FileNotFoundException(file.getPath() + " does not exist"));
        }

        PluginContext context = getContext(file);
        PluginDescriptionFile desc;

        try {
            InputStream stream = context.openStream("plugin.yml");
            if (stream != null) {
                desc = new PluginDescriptionFile(stream);
                stream.close();
            }
        }
        catch (IOException ex) {
            // just doesn't exist, we're good
        }
        catch (InvalidDescriptionException ex) {
            throw new InvalidPluginException("Error in description for " + file.getPath(), ex);
        }

        // TODO
        return new PythonPlugin();
    }

    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener, Plugin plugin) {
        return null;
    }

    public void enablePlugin(Plugin plugin) {
        PythonPlugin pyPlugin = (PythonPlugin) plugin;
        if (pyPlugin.isEnabled()) return;

        try {
            pyPlugin.setEnabled(true);
        } catch (Throwable ex) {
            server.getLogger().log(Level.SEVERE, "Error occurred while enabling " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
        }

        server.getPluginManager().callEvent(new PluginEnableEvent(plugin));
    }

    public void disablePlugin(Plugin plugin) {
        PythonPlugin pyPlugin = (PythonPlugin) plugin;
        if (!pyPlugin.isEnabled()) return;

        server.getPluginManager().callEvent(new PluginDisableEvent(plugin));

        try {
            pyPlugin.setEnabled(false);
        } catch (Throwable ex) {
            server.getLogger().log(Level.SEVERE, "Error occurred while disabling " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
        }
    }

    // Internal helper methods

    private PluginContext getContext(File file) {
        // we've externally ensured file both is not null and exists

        String path = file.getName().toLowerCase();
        if (file.isDirectory()) {
            return new DirectoryContext(file);
        } else {
            return new SingleFileContext(file);
        }
    }

}
