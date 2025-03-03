package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import de.kontext_e.jqassistant.plugin.antlr.api.config.GrammarConfiguration;
import de.kontext_e.jqassistant.plugin.antlr.api.model.GrammarFileDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.ScannedFileDescriptor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static de.kontext_e.jqassistant.plugin.antlr.impl.Utils.*;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, GrammarFileDescriptor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrScannerPlugin.class);

    private static final String PLUGIN_CONFIG_PREFIX = "jqassistant.plugin.antlr.configLocation";

    private ConfigurationProvider configurationProvider;
    private AntlrTool antlrTool;
    private Store store;
    private ParseTreeSaver parseTreeSaver;
    private File configFile;

    @Override
    protected void configure(){
        String configLocation = getProperty(PLUGIN_CONFIG_PREFIX, String.class);
        configFile = new File(configLocation);
        configurationProvider = new ConfigurationProvider();
        try {
            configurationProvider.loadConfigurationFrom(configFile);
        } catch (IOException e) {
            LOGGER.error("Could not read configuration file: {}", configFile, e);
        }
        super.configure();
    }

    @Override
    public boolean accepts(FileResource fileResource, String s, Scope scope) throws IOException {
        String fileExtension = getFileExtension(fileResource.getFile());
        Path path = fileResource.getFile().toPath();

        boolean hasConfiguredFileExtension = configurationProvider.isConfiguredFileExtension(fileExtension);
        if (!hasConfiguredFileExtension) return false;

        GrammarConfiguration config = configurationProvider.getGrammarConfigurationFor(fileExtension);

        Path configFilePath = configFile.getParentFile().toPath().toAbsolutePath();
        boolean isIncluded = config.getIncludedFileLocations().stream()
                .map(Paths::get)
                .map(configFilePath::resolve)
                .map(Path::normalize)
                .anyMatch(location -> isSubPath(path, location));
        if (isIncluded) return true;

        return config.getExcludedFileLocations().stream()
                .map(Paths::get)
                .map(configFilePath::resolve)
                .map(Path::normalize).
                noneMatch(location -> isSubPath(path, location));
    }

    private static boolean isSubPath(Path child, Path parent) {
        try {
            Path parentPath = parent.toRealPath();
            Path childPath = child.toRealPath();
            return childPath.startsWith(parentPath);
        } catch (IOException e) {
            LOGGER.error("Could not parse path: {}, try using absolute paths", child, e);
            return false;
        }
    }

    @Override
    public GrammarFileDescriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        File file = fileResource.getFile();
        GrammarConfiguration grammarConfiguration = configurationProvider.getGrammarConfigurationFor(getFileExtension(file));

        store = scanner.getContext().getStore();
        parseTreeSaver = new ParseTreeSaver(store, configurationProvider.getCreateEmptyNodes());
        antlrTool = new AntlrTool(grammarConfiguration, configFile);

        String lexerAndParserLocation = antlrTool.getLexerAndParser();
        ScannedFileDescriptor scannedFile = parseFilesAndStoreTrees(file, lexerAndParserLocation);
        String grammarRoot = grammarConfiguration.getGrammarRoot();
        addGrammarRootNameToScannedFiles(scannedFile, grammarRoot);

        if (configurationProvider.getDeleteParserAndLexerAfterScan()) {
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
