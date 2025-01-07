package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import de.kontext_e.jqassistant.plugin.antlr.api.model.AntlrDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.GrammarFileDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.ScannedFileDescriptor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, AntlrDescriptor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrScannerPlugin.class);

    private static final String PLUGIN_PROPERTY_PREFIX = "jqassistant.plugin.antlr.";
    private static final String CREATE_NODES_CONTAINING_EMPTY_TEXT = PLUGIN_PROPERTY_PREFIX + "createNodesContainingEmptyText";
    private static final String DELETE_PARSER_AND_LEXER_AFTER_SCAN = PLUGIN_PROPERTY_PREFIX + "deleteParserAndLexerAfterScan";
    private static final String READ_ONLY_CONFIGURED_GRAMMARS = PLUGIN_PROPERTY_PREFIX + "readOnlyConfiguredGrammars";

    private static final String GRAMMAR_PROPERTY = "\"jqassistant.plugin.antlr.grammars\"";

    private boolean createEmptyNodes;
    private boolean deleteParserAndLexerAfterScan;
    private boolean readOnlyConfiguredGrammars;
    private Map<String, Map<String, String>> grammarConfigurations = new HashMap<>();

    private AntlrTool antlrTool;
    private Store store;
    private ParseTreeSaver parseTreeSaver;

    @Override
    protected void configure() {
        createEmptyNodes = getBooleanProperty(CREATE_NODES_CONTAINING_EMPTY_TEXT, true);
        deleteParserAndLexerAfterScan = getBooleanProperty(DELETE_PARSER_AND_LEXER_AFTER_SCAN, false);
        readOnlyConfiguredGrammars = getBooleanProperty(READ_ONLY_CONFIGURED_GRAMMARS, false);
        grammarConfigurations = getGrammarConfigurations();
        super.configure();
    }

    private HashMap<String, Map<String, String>> getGrammarConfigurations() {

        HashMap<String, Map<String, String>> grammarConfigurations = new HashMap<>();
        Map<String, Object> properties = getProperties();

        int i = 0;
        while (true){
            String grammarFile = (String) properties.get(GRAMMAR_PROPERTY + "[" + i + "].grammar");
            String grammarRoot = (String) properties.get(GRAMMAR_PROPERTY + "[" + i + "].grammarRoot");
            String fileExtension = (String) properties.get(GRAMMAR_PROPERTY + "[" + i + "].fileExtension");

            if (grammarFile == null) break;

            String grammarName = getGrammarName(grammarFile);
            if (grammarRoot == null) grammarRoot = grammarName.toLowerCase();
            if (fileExtension == null) fileExtension = '.' + grammarName.toLowerCase();

            Map<String, String> grammarConfiguration = Map.of(
                    "grammarName", grammarName,
                    "grammarRoot", grammarRoot,
                    "fileExtension", fileExtension
            );
            grammarConfigurations.put(grammarFile, grammarConfiguration);

            i++;
        }
        return grammarConfigurations;
    }

    static String getGrammarName(String grammarFile) {
        return grammarFile.substring(grammarFile.lastIndexOf(File.separator) + 1, grammarFile.lastIndexOf('.'));
    }

    @Override
    public boolean accepts(FileResource fileResource, String s, Scope scope) throws IOException {
        if (readOnlyConfiguredGrammars){
            return grammarConfigurations.containsKey(fileResource.getFile().getName());
        } else {
            return s.endsWith(".g4");
        }
    }

    @Override
    public AntlrDescriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        File grammarFile = fileResource.getFile();

        Map<String, String> grammarConfiguration = grammarConfigurations.get(grammarFile.getName());
        if (grammarConfiguration == null) {
            grammarConfiguration = createNewGrammarConfiguration(grammarFile);
        }

        store = scanner.getContext().getStore();
        parseTreeSaver = new ParseTreeSaver(store, createEmptyNodes);
        antlrTool = new AntlrTool(grammarFile, grammarConfiguration);

        String lexerAndParserLocation = antlrTool.generateLexerAndParser();
        List<File> filesToBeParsed = getFilesToBeParsed(grammarFile);
        List<ScannedFileDescriptor> scannedFiles = parseFilesAndSaveTrees(filesToBeParsed, lexerAndParserLocation);

        if (deleteParserAndLexerAfterScan) {
            parseTreeSaver.deleteGeneratedFiles(lexerAndParserLocation);
        }

        FileDescriptor fileDescriptor = scanner.getContext().getCurrentDescriptor();
        GrammarFileDescriptor antlrGrammarDescriptor = store.addDescriptorType(fileDescriptor, GrammarFileDescriptor.class);
        antlrGrammarDescriptor.setScannedFiles(scannedFiles);
        return antlrGrammarDescriptor;
    }

    private Map<String, String> createNewGrammarConfiguration(File grammarFile) {
        Map<String, String> grammarConfiguration = new HashMap<>();
        String grammarName = getGrammarName(grammarFile.getName());
        grammarConfiguration.put("grammarName", grammarName);
        grammarConfiguration.put("grammarRoot", grammarName.toLowerCase());
        grammarConfiguration.put("fileExtension", "." + grammarName.toLowerCase());
        grammarConfigurations.put(grammarFile.getName(), grammarConfiguration);
        return grammarConfiguration;
    }

    private List<File> getFilesToBeParsed(File grammarFile) {
        ArrayList<File> files = new ArrayList<>();

        String grammarFileAbsolutePath = grammarFile.getAbsolutePath();
        String grammarName = grammarFileAbsolutePath.substring(grammarFileAbsolutePath.lastIndexOf(File.separator) + 1);
        String fileExtension = grammarConfigurations.get(grammarName).get("fileExtension");

        File parent = grammarFile.getParentFile();
        try (Stream<Path> stream = Files.walk(parent.toPath())){
            stream.filter(path -> path.toString().endsWith(fileExtension))
                  .forEach(path -> files.add(path.toFile()));
        } catch (IOException e) {
            LOGGER.error("An Error occurred while looking for files to be read with grammar: {}, {}", grammarFile.getName(), e.getMessage());
        }

        return files;
    }

    private List<ScannedFileDescriptor> parseFilesAndSaveTrees(List<File> filesToBeParsed, String lexerAndParserLocation) {
        List<ScannedFileDescriptor> scannedFiles = new ArrayList<>();
        for (File fileToBeParsed : filesToBeParsed) {
            ScannedFileDescriptor scannedFileDescriptor = store.create(ScannedFileDescriptor.class);
            List<ParseTree> parseTrees = loadParserAndParseFile(lexerAndParserLocation, fileToBeParsed);
            parseTreeSaver.saveParseTreesToNeo4J(parseTrees, scannedFileDescriptor);
            scannedFiles.add(scannedFileDescriptor);
        }
        return scannedFiles;
    }

    private List<ParseTree> loadParserAndParseFile(String lexerAndParserLocation, File parsedFile) {
        try {
            return antlrTool.loadParserAndParseFile(lexerAndParserLocation, parsedFile);
        } catch (IOException e) {
            LOGGER.error("There has been an error reading the File to be parsed: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            LOGGER.error("Method to get parse tree root not found in parser. Does the configured grammar root match the actual grammar root? {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("There has been an error while loading and executing the parser and lexer: {}", e.getMessage());
        }
        return new ArrayList<>();
    }
}
