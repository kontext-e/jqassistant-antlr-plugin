package de.kontext_e.jqassistant.plugin.antlr.api.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

@ConfigMapping(prefix = "jqassistant.plugin.antlr", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface PluginConfig {

    List<GrammarConfiguration> grammars();

    @WithDefault("false")
    boolean createEmptyNodes();

    @WithDefault("false")
    boolean deleteLexerAndParserAfterScan();

}
