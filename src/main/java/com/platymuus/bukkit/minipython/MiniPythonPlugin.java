package com.platymuus.bukkit.minipython;

import com.platymuus.bukkit.minipython.loader.PythonLoader;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.python.core.Py;
import org.python.core.PySystemState;
import org.python.core.ThreadState;
import org.python.google.common.base.internal.Finalizer;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * The hook plugin which installs the plugin loader
 */
public class MiniPythonPlugin extends JavaPlugin {

    public static boolean mashUpJython = false;

    public void onEnable() {
        getCommand("minipython").setExecutor(new MiniCommands(this));

        mashUpJython = false; //getServer().getName().equals("CraftBukkit");
        if (mashUpJython) {
            System.out.println("My class loader is: 0x" + Integer.toHexString(getClassLoader().hashCode()));
            System.out.println("PySystemState is: 0x" + Integer.toHexString(PySystemState.class.hashCode()));

            // If no scheduled tasks have occurred at all, CraftScheduler will have
            // a dead task that points back to the old plugin object, so run some empty
            // scheduled task to free up this reference
            new BukkitRunnable() {
                public void run() {
                }
            }.runTask(this);

            // Annoyingly, this cannot be fixed during *unload*. If this plugin is unloaded
            // and no other plugin then schedules a task, this reference remains hanging.
            System.gc();
        }
    }

    public void onDisable() {
        // mash up Jython into a fine pulp and get it out of permgen
        if (mashUpJython) {
            // one source of references to PySystemState
            Py.getSystemState().cleanup();
            Py.setSystemState(null);
            Py.defaultSystemState = null;

            // another reference, the ThreadLocal - doesn't get cleaned up *ever*
            Object threadStateMapping = Reflection.getPrivateValue(Py.class, null, "threadStateMapping");
            Object cachedThreadState = Reflection.getPrivateValue(threadStateMapping, "cachedThreadState");
            ThreadLocal<ThreadState> local = (ThreadLocal<ThreadState>) cachedThreadState;
            //System.out.println("ThreadLocal was: 0x" + Integer.toHexString(local.get().hashCode()));
            local.set(null);

            // Take out the Python/Google finalizer thread
            // It is supposed to die on its own when its class loader is reclaimed, but it appears
            // as though it is keeping the class loader alive itself
            ThreadGroup group = Thread.currentThread().getThreadGroup();
            Thread[] threads = new Thread[group.activeCount() + 3];
            group.enumerate(threads, true);

            boolean killed = false;
            for (Thread th : threads) {
                if (th == null) continue;

                // "instanceof Finalizer" cannot be used because the thread's class uses a different
                // class loader than the one we are using here - so the toString is checked instead
                if (th.toString().contains(Finalizer.class.getName())) {
                    killed = true;
                    th.stop();
                    break;
                }
            }
            if (!killed) {
                getLogger().warning("Failed to kill Jython finalizer thread - there may be PermGen leaks");
            }

            // Remove our PluginClassLoader from java.lang.Proxy.loaderToCache - the map is supposed to be weak
            // but this apparently isn't good enough
            Map<ClassLoader, Map<List<String>, Object>> loaderToCache = (Map<ClassLoader, Map<List<String>, Object>>)
                    Reflection.getPrivateValue(Proxy.class, null, "loaderToCache");
            Object o = loaderToCache.remove(getClassLoader());

            int i = 0;
            Map<Class<?>, Void> proxyClasses = (Map<Class<?>, Void>) Reflection.getPrivateValue(Proxy.class, null, "proxyClasses");
            Iterator<Class<?>> iter = proxyClasses.keySet().iterator();
            while (iter.hasNext()) {
                if (iter.next().getClassLoader() == getClassLoader()) {
                    iter.remove();
                    ++i;
                }
            }

            System.out.println("Removed " + i + " classes and loaderToCache[" + (o != null) + "]");
        }
    }

    public void onLoad() {
        PluginManager pm = getServer().getPluginManager();

        // Make sure we haven't already been added
        try {
            Map<Pattern, PluginLoader> map = (Map<Pattern, PluginLoader>) Reflection.getPrivateValue(pm, "fileAssociations");
            if (map != null && map.containsKey(PythonLoader.PATTERNS[0])) {
                getLogger().info("MiniPython was already loaded, aborting");
                return;
            }
        } catch (Exception ex) {
            getLogger().severe("Failed to ensure MiniPython isn't already loaded");
            return;
        }

        // Add to Bukkit
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
                        getLogger().info("Loading " + file.getName());
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

