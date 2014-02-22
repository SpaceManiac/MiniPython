package com.platymuus.bukkit.minipython.loader;

import com.platymuus.bukkit.minipython.loader.context.DirectoryContext;
import com.platymuus.bukkit.minipython.loader.context.PluginContext;
import com.platymuus.bukkit.minipython.loader.context.SingleFileContext;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.*;
import org.python.core.*;
import org.python.util.PythonInterpreter;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
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
     *
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

        return getDescription(getContext(file), file);
    }

    public Plugin loadPlugin(File file) throws InvalidPluginException, UnknownDependencyException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!file.exists()) {
            throw new InvalidPluginException(new FileNotFoundException(file.getPath() + " does not exist"));
        }

        // Part 1: set up the plugin's description
        PluginContext context = getContext(file);
        PluginDescriptionFile desc;
        try {
            desc = getDescription(getContext(file), file);
        } catch (InvalidDescriptionException ex) {
            throw new InvalidPluginException("Error in description for " + file.getPath(), ex);
        }

        // Part 2: find data folder
        File dataFolder = new File(file.getParentFile(), desc.getName());
        if (dataFolder.getAbsolutePath().equals(file.getAbsolutePath())) {
            throw new InvalidPluginException("Data folder " + dataFolder.getName() + " is the same as its plugin's file");
        } else if (dataFolder.exists() && !dataFolder.isDirectory()) {
            throw new InvalidPluginException("Data folder " + dataFolder.getName() + " is not a directory");
        }

        // Part 2: check dependencies eventually?
        // ...

        // Part 3: add the plugin to the Python path if needed
        PySystemState state = Py.getSystemState(); //new PySystemState();
        PyList path = state.path;
        PyString pathEntry = new PyString(file.getAbsolutePath());
        if (context.isDirectory() && !path.__contains__(pathEntry)) {
            path.append(pathEntry);
        }

        // Part 4: determine the main file
        String mainFile = desc.getMain();
        InputStream mainStream;
        try {
            mainStream = context.openStream(mainFile);

            // if we didn't find anything, try some other common ones
            if (mainStream == null) {
                mainFile = "plugin.py";
                mainStream = context.openStream(mainFile);
            }

            if (mainStream == null) {
                mainFile = "main.py";
                mainStream = context.openStream(mainFile);
            }
        } catch (IOException ex) {
            throw new InvalidPluginException(ex);
        }

        if (mainStream == null) {
            throw new InvalidPluginException("Failed to find " + desc.getMain() + " or any fallbacks in " + file.getName());
        }

        // Part 5: get the interpreter all set up
        PythonInterpreter interp = new PythonInterpreter(); //null, state);

        try {
            prepareInterpreter(interp);
            interp.execfile(mainStream, mainFile);
            mainStream.close();
        } catch (Exception ex) {
            throw new InvalidPluginException("Error running Python for " + file.getName(), ex);
        }

        // Part 6: extract the plugin object
        PythonPlugin plugin;
        PyObject pyClass = interp.get("Plugin");

        if (pyClass == null) {
            plugin = new PythonPlugin();
        } else {
            try {
                plugin = (PythonPlugin) pyClass.__call__().__tojava__(PythonPlugin.class);
            } catch (Exception ex) {
                throw new InvalidPluginException("Could not initialize class for " + file.getName(), ex);
            }
        }

        // Part 7: wrap things up
        interp.set("pyplugin", plugin);
        plugin.initialize(this, server, file, dataFolder, desc, context, interp);

        return plugin;
    }

    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener, Plugin plugin) {
        boolean useTimings = plugin.getServer().getPluginManager().useTimings();
        Map<Class<? extends Event>, Set<RegisteredListener>> result = new HashMap<Class<? extends Event>, Set<RegisteredListener>>();

        // java2py lets us loop over our Python elements
        PyObject self = Py.java2py(this);
        PyObject memberList = self.__dir__();

        for (PyObject memberName : memberList.asIterable()) {
            PyObject func = self.__getattr__(memberName.toString());
            if (!func.isCallable()) continue;

            PyObject list;
            try {
                list = func.__getattr__("bukkit_eventhandler");
            } catch (PyException ex) {
                // it doesn't have that attribute, so skip town
                continue;
            }

            // hopefully we're not being cheated and lied to and this is a list of tuples
            for (PyObject tuple : list.asIterable()) {
                String type = tuple.__getitem__(0).toString();
                EventPriority priority = (EventPriority) tuple.__getitem__(1).__tojava__(EventPriority.class);
                boolean ignoreCancelled = tuple.__getitem__(2).__nonzero__();

                Class<? extends Event> eventClass = resolveType(type, plugin);
                if (eventClass == null) continue;

                EventExecutor executor = new FuncExecutor(func);

                Set<RegisteredListener> eventSet = result.get(eventClass);
                if (eventSet == null) {
                    eventSet = new HashSet<RegisteredListener>();
                    result.put(eventClass, eventSet);
                }

                if (useTimings) {
                    eventSet.add(new TimedRegisteredListener(listener, executor, priority, plugin, ignoreCancelled));
                } else {
                    eventSet.add(new RegisteredListener(listener, executor, priority, plugin, ignoreCancelled));
                }
            }
        }

        return result;
    }

    private Class<? extends Event> resolveType(String type, Plugin plugin) {
        try {
            return Class.forName(type).asSubclass(Event.class);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName("org.bukkit.event." + type).asSubclass(Event.class);
            } catch (ClassNotFoundException e2) {
                plugin.getLogger().severe("Failed to register listener: no such class " + type);
                return null;
            }
        } catch (ClassCastException e) {
            plugin.getLogger().severe("Failed to register listener: class " + type + " is not an Event");
            return null;
        }
    }

    private class FuncExecutor implements EventExecutor {
        private final PyObject func;

        public FuncExecutor(PyObject func) {
            this.func = func;
        }

        public void execute(Listener listener, Event event) throws EventException {
            try {
                func.__call__(Py.java2py(event));
            } catch (Exception ex) {
                throw new EventException(ex);
            }
        }
    }

    public void enablePlugin(Plugin plugin) {
        PythonPlugin pyPlugin = (PythonPlugin) plugin;
        if (pyPlugin.isEnabled()) return;

        plugin.getLogger().info("Enabling " + plugin.getDescription().getFullName());

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

        plugin.getLogger().info("Disabling " + plugin.getDescription().getFullName());

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

    private PluginDescriptionFile getDescription(PluginContext context, File fallback) throws InvalidDescriptionException {
        try {
            InputStream stream = context.openStream("plugin.yml");
            if (stream != null) {
                PluginDescriptionFile desc = new PluginDescriptionFile(stream);
                stream.close();
                return desc;
            }
        } catch (IOException e) {
            // ignore for now
        }

        // we can't use the PluginDescriptionFile constructor because it leaves lots of fields null,
        // so we have to fake it with some auto-generated yaml
        String text = "name: \"" + fallback.getName().replace(".py", "") + "\"\n" +
                "version: 0.0\n" +
                "main: plugin.py\n";
        return new PluginDescriptionFile(new StringReader(text));
    }

    private void prepareInterpreter(PythonInterpreter interp) throws IOException {
        loadScript(interp, "imports.py");
        loadScript(interp, "decorators.py");
    }

    private void loadScript(PythonInterpreter interp, String script) throws IOException {
        URL url = getClass().getClassLoader().getResource("scripts/" + script);
        if (url == null) throw new IOException("Failed to find script " + script);

        URLConnection connection = url.openConnection();
        connection.setUseCaches(false);
        InputStream inputStream = url.openStream();
        interp.execfile(inputStream, "builtin/" + script);
        inputStream.close();
    }

}
