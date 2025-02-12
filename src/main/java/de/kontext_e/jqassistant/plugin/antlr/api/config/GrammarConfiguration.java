package de.kontext_e.jqassistant.plugin.antlr.api.config;

import de.kontext_e.jqassistant.plugin.antlr.impl.Utils;
import io.smallrye.config.ConfigMapping;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.smallrye.config.ConfigMapping.NamingStrategy.VERBATIM;

@ConfigMapping(namingStrategy = VERBATIM)
public interface GrammarConfiguration {

    String grammarFile();

    Optional<String> grammarRoot();

    default String getGrammarRoot() {
        return grammarRoot().orElse(Utils.getGrammarRoot(grammarFile()));
    }

    Optional<String> grammarName();

    default String getGrammarName(){
        return grammarName().orElse(Utils.getGrammarName(grammarFile()));
    }

    Optional<String> fileExtension();

    default String getFileExtension() {
        return fileExtension().orElse(Utils.getFileExtension(new File(grammarFile())));
    }

    Optional<List<String>> excludedFileLocations();

    default List<String> getExcludedFileLocations() {
        return excludedFileLocations().orElse(new ArrayList<>());
    }
}
