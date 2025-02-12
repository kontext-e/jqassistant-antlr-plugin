package de.kontext_e.jqassistant.plugin.antlr.impl;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ClasspathResolver {
    public String getPluginClassPath() {
        ClassLoader classLoader = getClass().getClassLoader();
        return getClassPathOf(classLoader)
                .stream()
                .map(this::normalizeURL)
                .collect(Collectors.joining(";"));
    }

    private List<URL> getClassPathOf(ClassLoader classLoader) {
        if(classLoader instanceof URLClassLoader) {
            var ucl = ((URLClassLoader) classLoader);
            return Arrays.asList(ucl.getURLs());
        }
        return Collections.emptyList();
    }

    private String normalizeURL(URL url) {
        String path = url.getPath();
        path = path.replace('/', File.separatorChar);
        path = path.replace('\\', File.separatorChar);
        path = path.substring(1);
        return path;
    }
}
