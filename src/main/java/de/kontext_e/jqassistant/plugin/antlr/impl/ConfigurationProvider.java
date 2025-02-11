package de.kontext_e.jqassistant.plugin.antlr.impl;

import de.kontext_e.jqassistant.plugin.antlr.api.config.GrammarConfiguration;
import de.kontext_e.jqassistant.plugin.antlr.api.config.PluginConfig;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static de.kontext_e.jqassistant.plugin.antlr.impl.Utils.getFileExtension;

public class ConfigurationProvider {

    private boolean deleteParserAndLexerAfterScan;
    private boolean createEmptyNodes;
    private final Map<String, GrammarConfiguration> grammarConfigurations = new HashMap<>();

    ConfigurationProvider() {}

    public void loadConfigurationFrom(File configFile) throws IOException {
        SmallRyeConfig config = loadYamlFile(configFile);
        PluginConfig pluginConfig = config.getConfigMapping(PluginConfig.class);

        createEmptyNodes = pluginConfig.createEmptyNodes();
        deleteParserAndLexerAfterScan = pluginConfig.deleteLexerAndParserAfterScan();

        for (GrammarConfiguration grammarConfiguration : pluginConfig.grammars()){
            String fileExtension = grammarConfiguration.getFileExtension();
            grammarConfigurations.put(fileExtension, grammarConfiguration);
        }

    }

    private static SmallRyeConfig loadYamlFile(File configFile) throws IOException {
        URL url = Paths.get(configFile.getPath()).toUri().toURL();
        YamlConfigSource yamlConfigSource = new YamlConfigSource(url, 300);
        return new SmallRyeConfigBuilder()
                .withMapping(PluginConfig.class)
                .withSources(yamlConfigSource)
                .build();
    }

    public boolean getDeleteParserAndLexerAfterScan() {
        return deleteParserAndLexerAfterScan;
    }

    public boolean getCreateEmptyNodes() {
        return createEmptyNodes;
    }

    public GrammarConfiguration getGrammarConfigurationFor(String fileExtension) {
        return grammarConfigurations.get(fileExtension);
    }

    public boolean isConfiguredFileExtension(String fileExtension) {
        return grammarConfigurations.containsKey(fileExtension);
    }
}
