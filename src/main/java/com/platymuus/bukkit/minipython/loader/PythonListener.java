package com.platymuus.bukkit.minipython.loader;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.TimedRegisteredListener;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Java-side implementation of the event listener system.
 */
public class PythonListener implements Listener {

    public final Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(Plugin plugin) {
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
                    eventSet.add(new TimedRegisteredListener(this, executor, priority, plugin, ignoreCancelled));
                } else {
                    eventSet.add(new RegisteredListener(this, executor, priority, plugin, ignoreCancelled));
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
}
