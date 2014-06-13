package com.platymuus.bukkit.minipython.loader.context;

import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.*;

/**
 * PluginContext
 */
public class SingleFileContext implements PluginContext {

    private final File file;

    public SingleFileContext(File file) {
        this.file = file;
    }

    public boolean isDirectory() {
        return false;
    }

    public InputStream openStream(String filename) throws IOException {
        return null;
    }

    public PluginDescriptionFile getDescription() throws InvalidDescriptionException {
        StringBuilder buffer = new StringBuilder();
        boolean inYaml = false;

        // keep track of the required keys
        boolean foundName = false, foundVersion = false, foundMain = false;

        // any number of commented lines (starting with #)
        // a line "# ---"
        // lines starting with "# " which will become the yaml
        // a line "# ---"
        // the rest of the file

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    // EOF reached
                    break;
                } else if (line.trim().isEmpty()) {
                    // empty lines allowed
                    continue;
                } else if (!line.startsWith("#")) {
                    // end of comments reached
                    break;
                }
                if (line.equals("# ---")) {
                    if (!inYaml) {
                        // first dashes start yaml
                        inYaml = true;
                    } else {
                        // second dashes end yaml
                        break;
                    }
                } else if (line.startsWith("# ") && inYaml) {
                    // add line to yaml buffer
                    buffer.append(line.substring(2)).append('\n');

                    if (line.startsWith("# name:")) {
                        foundName = true;
                    } else if (line.startsWith("# version:")) {
                        foundVersion = true;
                    } else if (line.startsWith("# main:")) {
                        foundMain = true;
                    }
                }
                // other comments are ignored
            }
        } catch (IOException e) {
            throw new InvalidDescriptionException(e);
        }

        // add missing name, version, or main if needed
        if (!foundName) {
            buffer.append("name: ").append(file.getName().replace(".py", "")).append('\n');
        }
        if (!foundVersion) {
            buffer.append("version: 0.0\n");
        }
        if (!foundMain) {
            buffer.append("main: __auto__\n");
        }

        // buffer is now complete, throw it PDF's way
        return new PluginDescriptionFile(new StringReader(buffer.toString()));
    }

    public void close() {
    }
}
