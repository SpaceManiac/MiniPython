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

    public boolean isDirectory() {
        return true;
    }

    public InputStream openStream(String filename) throws IOException {
        File f = new File(file, filename);
        if (!f.exists()) return null;
        return new FileInputStream(f);
    }

    public PluginDescriptionFile getDescription() throws InvalidDescriptionException {
        try (InputStream stream = openStream("plugin.yml")) {
            if (stream != null) {
                return new PluginDescriptionFile(stream);
            }
            throw new InvalidDescriptionException("Could not find plugin.yml");
        } catch (IOException e) {
            throw new InvalidDescriptionException(e);
        }
    }

    public void close() {
    }
}
