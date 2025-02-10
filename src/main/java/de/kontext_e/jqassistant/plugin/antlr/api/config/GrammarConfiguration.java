package de.kontext_e.jqassistant.plugin.antlr.api.config;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface GrammarConfiguration {

    String grammarFile();

    Optional<String> grammarRoot();

    Optional<String> grammarName();

    Optional<String> fileExtension();

    //TODO
    //List<String> fileLocations();
    //List<String> excludedFileLocations();

}
