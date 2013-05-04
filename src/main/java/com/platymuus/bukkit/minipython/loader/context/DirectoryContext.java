package com.platymuus.bukkit.minipython.loader.context;

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
        File f = new File(file, filename);
        if (!f.exists()) return null;
        return new FileInputStream(f);
    }

    public void close() {
    }

    public boolean isDirectory() {
        return true;
    }
}
