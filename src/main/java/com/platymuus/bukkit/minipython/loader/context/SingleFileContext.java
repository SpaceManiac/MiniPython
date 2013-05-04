package com.platymuus.bukkit.minipython.loader.context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * PluginContext
 */
public class SingleFileContext implements PluginContext {

    private final File file;

    public SingleFileContext(File file) {
        this.file = file;
    }

    public InputStream openStream(String filename) throws IOException {
        if (filename.equals("plugin.py")) {
            return new FileInputStream(file);
        } else {
            return null;
        }
    }

    public void close() {
    }

    public boolean isDirectory() {
        return false;
    }
}
