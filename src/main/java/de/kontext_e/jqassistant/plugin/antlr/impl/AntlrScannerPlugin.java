package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import de.kontext_e.jqassistant.plugin.antlr.api.config.GrammarConfiguration;
import de.kontext_e.jqassistant.plugin.antlr.api.config.PluginConfig;
import de.kontext_e.jqassistant.plugin.antlr.api.model.GrammarFileDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.ScannedFileDescriptor;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static de.kontext_e.jqassistant.plugin.antlr.impl.Utils.*;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, GrammarFileDescriptor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrScannerPlugin.class);

    private static final String PLUGIN_CONFIG_PREFIX = "jqassistant.plugin.antlr.configLocation";

    private boolean createEmptyNodes;
    private boolean deleteParserAndLexerAfterScan;
    private final Map<String, GrammarConfiguration> grammarConfigurations = new HashMap<>();

    private AntlrTool antlrTool;
    private Store store;
    private ParseTreeSaver parseTreeSaver;

    @Override
    protected void configure(){
        String configLocation = getProperty(PLUGIN_CONFIG_PREFIX, String.class);
        try {
            URL url = Paths.get(configLocation).toUri().toURL();
            YamlConfigSource yamlConfigSource = new YamlConfigSource(url, 300);
            SmallRyeConfig config = new SmallRyeConfigBuilder()
                    .withMapping(PluginConfig.class)
                    .withSources(yamlConfigSource)
                    .build();

            PluginConfig pluginConfig = config.getConfigMapping(PluginConfig.class);
            createEmptyNodes = pluginConfig.createEmptyNodes();
            deleteParserAndLexerAfterScan = pluginConfig.deleteLexerAndParserAfterScan();

            for (GrammarConfiguration grammarConfiguration : pluginConfig.grammars()){
                String fileExtension = grammarConfiguration.fileExtension()
                        .orElse(getFileExtension(new File(grammarConfiguration.grammarFile())));
                grammarConfigurations.put(fileExtension, grammarConfiguration);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.configure();
    }

    @Override
    public boolean accepts(FileResource fileResource, String s, Scope scope) throws IOException {
        String fileExtension = getFileExtension(fileResource.getFile());
        return grammarConfigurations.containsKey(fileExtension);
    }

    @Override
    public GrammarFileDescriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        File file = fileResource.getFile();

        GrammarConfiguration grammarConfiguration = grammarConfigurations.get(getFileExtension(file));

        store = scanner.getContext().getStore();
        parseTreeSaver = new ParseTreeSaver(store, createEmptyNodes);
        antlrTool = new AntlrTool(grammarConfiguration);

        String lexerAndParserLocation = antlrTool.getLexerAndParser();
        ScannedFileDescriptor scannedFile = parseFilesAndStoreTrees(file, lexerAndParserLocation);
        addGrammarRootNameToScannedFiles(scannedFile, grammarConfiguration.grammarRoot().orElse(getGrammarRoot(grammarConfiguration.grammarFile())));

        if (deleteParserAndLexerAfterScan) {
            deleteGeneratedFiles(lexerAndParserLocation);
        }

        FileDescriptor fileDescriptor = scanner.getContext().getCurrentDescriptor();
        GrammarFileDescriptor antlrGrammarDescriptor = store.addDescriptorType(fileDescriptor, GrammarFileDescriptor.class);
        antlrGrammarDescriptor.getScannedFiles().add(scannedFile);
        return antlrGrammarDescriptor;
    }

    private void addGrammarRootNameToScannedFiles(ScannedFileDescriptor scannedFile, String grammarRoot) {
        String queryTemplate = "MATCH (n) WHERE id(n) = %s SET n:%s";
        String query = String.format(queryTemplate, scannedFile.getId(), capitalizeFirstLetter(grammarRoot));
        store.executeQuery(query).close();
    }

    private ScannedFileDescriptor parseFilesAndStoreTrees(File fileToBeParsed, String lexerAndParserLocation) {
        List<ParseTree> parseTrees = loadParserAndParseFile(lexerAndParserLocation, fileToBeParsed);
        ScannedFileDescriptor scannedFileDescriptor = store.create(ScannedFileDescriptor.class);
        parseTreeSaver.saveParseTreesToNeo4J(parseTrees, scannedFileDescriptor);
        return scannedFileDescriptor;
    }

    private List<ParseTree> loadParserAndParseFile(String lexerAndParserLocation, File parsedFile) {
        try {
            return antlrTool.loadParserAndParseFile(lexerAndParserLocation, parsedFile);
        } catch (IOException e) {
            LOGGER.error("There has been an error reading the File to be parsed: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.error("Method to get parse tree root not found in parser. Does the configured grammar root match the actual grammar root? {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("There has been an error while loading and executing the parser and lexer: {}", (Object[]) e.getStackTrace());
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    void deleteGeneratedFiles(String lexerAndParserLocation) {
        try (var dirStream = Files.walk(Paths.get(lexerAndParserLocation))) {
            dirStream.map(Path::toFile)
                    .sorted(Comparator.reverseOrder())
                    .forEach(File::delete);
        } catch (IOException e) {
            LOGGER.warn("Could not delete generated files in: {}", lexerAndParserLocation);
        }
    }
}
