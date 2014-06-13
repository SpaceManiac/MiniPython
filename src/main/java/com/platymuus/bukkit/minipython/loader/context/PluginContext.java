package com.platymuus.bukkit.minipython.loader.context;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * The general interface for plugin containers.
 */
public interface PluginContext {

    public boolean isDirectory();

    public InputStream openStream(String filename) throws IOException;

    public PluginDescriptionFile getDescription() throws InvalidDescriptionException;

    public void close();

}
