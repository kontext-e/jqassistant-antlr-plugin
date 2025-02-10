package de.kontext_e.jqassistant.plugin.antlr.impl;

import java.io.File;

public class Utils {

    static String getGrammarName(String grammarFile) {
        grammarFile = grammarFile.replace('/', File.separatorChar);
        grammarFile = grammarFile.replace('\\', File.separatorChar);
        return grammarFile.substring(grammarFile.lastIndexOf(File.separator) + 1, grammarFile.lastIndexOf('.'));
    }

    static String capitalizeFirstLetter(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    static String getFileExtension(File file) {
        return file.getName().substring(file.getName().lastIndexOf('.'));
    }

    static String getGrammarRoot(String grammarFile) {
        return getGrammarName(grammarFile).toLowerCase();
    }
}
