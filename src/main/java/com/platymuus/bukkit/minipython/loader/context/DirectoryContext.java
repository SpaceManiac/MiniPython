package com.platymuus.bukkit.minipython.loader.context;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * PluginContext representing a directory.
 */
public class DirectoryContext implements PluginContext {

    private final File file;

    public DirectoryContext(File file) {
        this.file = file;
    }

    public InputStream openStream(String filename) throws IOException {
        return new FileInputStream(new File(file, filename));
    }

    public void close() {
    }

    public boolean isDirectory() {
        return true;
    }
}
