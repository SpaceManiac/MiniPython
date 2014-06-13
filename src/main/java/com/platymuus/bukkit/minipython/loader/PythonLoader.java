package com.platymuus.bukkit.minipython.loader;

import com.platymuus.bukkit.minipython.MiniPythonPlugin;
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

        return getContext(file).getDescription();
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
            desc = context.getDescription();
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

        // Part 3: initialize state and add entries to the path
        PySystemState state = new PySystemState();
        // a) the plugin directory or zip, if needed
        if (context.isDirectory()) {
            addToPath(state, file);
        }
        // b) the scripts/ directory in the loader
        addToPath(state, new File(MiniPythonPlugin.plugin.getFile(), "scripts"));

        // Part 4: get the interpreter all set up
        PythonInterpreter interp = new PythonInterpreter(null, state);

        try {
            // prepare interpreter
            interp.set("_plugin_name", new PyString(desc.getName()));
            loadScript(interp, "_setup.py");
        } catch (Exception ex) {
            throw new InvalidPluginException("Error preparing interpreter for " + desc.getFullName(), ex);
        }

        // Part 5: determine and run the main file
        String[] mainParts = desc.getMain().split(":");
        PyObject moduleDict;
        String mainName = null;
        try {
            if (context.isDirectory()) {
                // determine plugin value name
                String module = mainParts[0];
                if (mainParts.length > 1) {
                    mainName = mainParts[1];
                }

                // import the main module
                interp.exec("import " + module + " as _main");
                moduleDict = interp.get("_main").getDict();
            } else {
                // determine plugin value name
                if (!mainParts[0].equals("__auto__")) {
                    mainName = mainParts[0];
                }

                // execute the file standalone
                try (InputStream in = new FileInputStream(file)) {
                    interp.execfile(in, file.getName());
                    moduleDict = interp.getLocals();
                }
            }
        } catch (Exception ex) {
            throw new InvalidPluginException("Error running Python for " + desc.getFullName(), ex);
        }

        // Part 6: extract the plugin object
        // first, look up the main name in the module if we can do that
        PyObject item;
        if (mainName == null) {
            // have to guess
            // try 'Main', 'Plugin', and 'quick'
            mainName = "Main";
            item = moduleDict.__finditem__("Main");
            if (item == null) {
                mainName = "Plugin";
                item = moduleDict.__finditem__("Plugin");
            }
            if (item == null) {
                mainName = "quick";
                item = moduleDict.__finditem__("quick");
            }
            if (item == null) {
                throw new InvalidPluginException("No main class for " + desc.getFullName() + ": automatic lookup failed, please specify explicitly");
            }
        } else {
            // name was actually specified
            item = moduleDict.__finditem__(mainName);
            if (item == null) {
                throw new InvalidPluginException("No main class for " + desc.getFullName() + ": name \"" + mainName + "\" not in module");
            }
        }

        // we now have the object
        // it is either the plugin itself, the class, or a 'quick' module
        // item is never null at this point
        Object converted = item.__tojava__(PythonPlugin.class);
        if (converted == Py.NoConversion) {
            // see if it has _make_plugin from 'quick' or is callable (like a class)
            PyObject make_func = item.__findattr__("_make_plugin");
            try {
                if (make_func != null) {
                    item = make_func.__call__();
                } else if (item.isCallable()) {
                    item = item.__call__();
                }
            } catch (Exception ex) {
                throw new InvalidPluginException("Error initializing " + desc.getFullName() + " main \"" + mainName + "\"", ex);
            }
            converted = item.__tojava__(PythonPlugin.class);
        }
        if (converted == Py.NoConversion) {
            throw new InvalidPluginException("Bad main class for " + desc.getFullName() + ": \"" + mainName + "\" could not be resolved to a plugin");
        }

        PythonPlugin plugin = (PythonPlugin) converted;

        // Part 7: wrap things up
        plugin.initialize(this, server, file, dataFolder, desc, context, interp);

        return plugin;
    }

    private void addToPath(PySystemState state, File file) {
        PyString entry = new PyString(file.getAbsolutePath());
        if (!state.path.contains(entry)) {
            state.path.append(entry);
        }
    }

    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Listener listener, Plugin plugin) {
        boolean useTimings = plugin.getServer().getPluginManager().useTimings();
        Map<Class<? extends Event>, Set<RegisteredListener>> result = new HashMap<Class<? extends Event>, Set<RegisteredListener>>();

        // java2py lets us loop over our Python elements
        PyObject self = Py.java2py(listener);
        PyObject memberList = self.__dir__();

        for (PyObject memberName : memberList.asIterable()) {
            PyObject func = self.__getattr__(memberName.toString());
            if (!func.isCallable()) continue;

            PyObject list = func.__findattr__("bukkit_eventhandler");
            if (list == null) {
                // it doesn't have that attribute, so skip town
                continue;
            }

            // hopefully we're not being cheated and lied to and this is a list of tuples
            for (PyObject tuple : list.asIterable()) {
                String type = tuple.__getitem__(0).toString();
                EventPriority priority = (EventPriority) tuple.__getitem__(1).__tojava__(EventPriority.class);
                boolean ignoreCancelled = tuple.__getitem__(2).__nonzero__();

                Class<? extends Event> eventClass = resolveType(type, plugin);
                if (eventClass == null) {
                    plugin.getLogger().severe("Could not register listener: unknown event type \"" + type + "\"");
                    continue;
                }

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
        } catch (PyException ex) {
            server.getLogger().log(Level.SEVERE, "Error in Python code while enabling " + plugin.getDescription().getFullName());
            ex.printStackTrace();
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
        } catch (PyException ex) {
            server.getLogger().log(Level.SEVERE, "Error in Python code while enabling " + plugin.getDescription().getFullName());
            ex.printStackTrace();
        } catch (Throwable ex) {
            server.getLogger().log(Level.SEVERE, "Error occurred while disabling " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
        }
    }

    // Internal helper methods

    private PluginContext getContext(File file) {
        // we've externally ensured file both is not null and exists
        if (file.isDirectory()) {
            return new DirectoryContext(file);
        } else {
            return new SingleFileContext(file);
        }
    }

    private void loadScript(PythonInterpreter interp, String script) throws IOException {
        URL url = PythonLoader.class.getClassLoader().getResource("scripts/" + script);
        if (url == null) throw new IOException("Failed to find script " + script);

        URLConnection connection = url.openConnection();
        connection.setUseCaches(false);
        InputStream inputStream = url.openStream();
        interp.execfile(inputStream, "builtin/" + script);
        inputStream.close();
    }

}
