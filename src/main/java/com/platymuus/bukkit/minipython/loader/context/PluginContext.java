package com.platymuus.bukkit.minipython.loader.context;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * The general interface for plugin containers.
 */
public interface PluginContext {

    public InputStream openStream(String filename) throws IOException;

    public void close();

    public boolean isDirectory();

}
