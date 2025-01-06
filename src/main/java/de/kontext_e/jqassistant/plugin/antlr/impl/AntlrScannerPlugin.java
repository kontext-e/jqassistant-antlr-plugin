package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import de.kontext_e.jqassistant.plugin.antlr.api.model.AntlrDescriptor;
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

    private static final String CREATE_NODES_CONTAINING_EMPTY_TEXT = "jqassistant.plugin.antlr.createNodesContainingEmptyText";
    private static final String DELETE_PARSER_AND_LEXER_AFTER_SCAN = "jqassistant.plugin.antlr.deleteParserAndLexerAfterScan";
    private static final String GRAMMAR_PROPERTY = "\"jqassistant.plugin.antlr.grammars\"";

    private boolean createEmptyNodes;
    private boolean deleteParserAndLexerAfterScan;
    private Map<String, Map<String, String>> grammarConfigurations = new HashMap<>();

    private AntlrTool antlrTool;

    @Override
    protected void configure() {
        createEmptyNodes = getBooleanProperty(CREATE_NODES_CONTAINING_EMPTY_TEXT, true);
        deleteParserAndLexerAfterScan = getBooleanProperty(DELETE_PARSER_AND_LEXER_AFTER_SCAN, false);
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

            String grammarName = grammarFile.substring(grammarFile.lastIndexOf(File.separator) + 1, grammarFile.lastIndexOf('.'));
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

    @Override
    public boolean accepts(FileResource fileResource, String s, Scope scope) {
        return s.endsWith(".g4");
    }

    @Override
    public AntlrDescriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        File grammarFile = fileResource.getFile();
        Store store = scanner.getContext().getStore();

        antlrTool = new AntlrTool(grammarFile, grammarConfigurations.get(grammarFile.getName()));
        String lexerAndParserLocation = antlrTool.generateLexerAndParser();

        ParseTreeSaver parseTreeSaver = new ParseTreeSaver(store, createEmptyNodes);
        List<File> filesToBeParsed = getFilesToBeParsed(grammarFile);
        for (File fileToBeParsed : filesToBeParsed) {
            List<ParseTree> parseTrees = loadParserAndParseFile(lexerAndParserLocation, fileToBeParsed);
            parseTreeSaver.saveParseTreesToNeo4J(parseTrees);
        }

        if (deleteParserAndLexerAfterScan) {
            parseTreeSaver.deleteGeneratedFiles(lexerAndParserLocation);
        }

        FileDescriptor fileDescriptor = scanner.getContext().getCurrentDescriptor();
        return store.addDescriptorType(fileDescriptor, AntlrDescriptor.class);
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
