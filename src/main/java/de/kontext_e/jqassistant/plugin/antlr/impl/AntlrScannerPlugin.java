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
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, AntlrDescriptor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrScannerPlugin.class);

    private static final String CREATE_NODES_CONTAINING_EMPTY_TEXT = "jqassistant.plugin.antlr.createNodesContainingEmptyText";
    private static final String DELETE_PARSER_AND_LEXER_AFTER_SCAN = "jqassistant.plugin.antlr.deleteParserAndLexerAfterScan";
    private static final String GRAMMAR_PROPERTY = "\"jqassistant.plugin.antlr.grammars\"";

    private boolean createEmptyNodes;
    private boolean deleteParserAndLexerAfterScan;
    private Map<String, Map<String, String>> grammarConfigurations = new HashMap<>();

    private Store store;
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
        store = scanner.getContext().getStore();

        antlrTool = new AntlrTool(grammarFile, grammarConfigurations.get(grammarFile.getName()));
        String lexerAndParserLocation = antlrTool.generateLexerAndParser();

        List<File> filesToBeParsed = getFilesToBeParsed(grammarFile);
        for (File fileToBeParsed : filesToBeParsed) {
            List<ParseTree> parseTrees = loadParserAndParseFile(lexerAndParserLocation, fileToBeParsed);
            for (ParseTree parseTree : parseTrees) {
                saveParseTreeToNeo4J(null, parseTree);
            }
        }

        if (deleteParserAndLexerAfterScan) {
            deleteGeneratedFiles(lexerAndParserLocation);
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

    private void saveParseTreeToNeo4J(AntlrDescriptor parent, ParseTree parseTree) {
        if (parseTree.getText().isBlank() && !createEmptyNodes) return;

        AntlrDescriptor node = createDescriptor(parseTree);
        if (parent != null) {
            parent.getChildren().add(node);
        }

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            ParseTree child = parseTree.getChild(i);
            saveParseTreeToNeo4J(node, child);
        }
    }

    private AntlrDescriptor createDescriptor(ParseTree parseTree) {
        AntlrDescriptor descriptor = store.create(AntlrDescriptor.class);
        descriptor.setText(parseTree.getText());
        addCustomLabelToDescriptor(descriptor, parseTree);

        return descriptor;
    }

    private void addCustomLabelToDescriptor(AntlrDescriptor descriptor, ParseTree parseTree) {
        String className = parseTree.getClass().getName();
        Matcher matcher = Pattern.compile("\\$(.*?)Context").matcher(className);
        String nodeLabel = matcher.find() ? matcher.group(1) : "TerminalNode";

        //Cypher does not allow for parameterization of labels, which is why string formatting is used
        String query = String.format("MATCH (n) WHERE id(n) = %s SET n:%s", descriptor.getId(), nodeLabel);
        store.executeQuery(query).close();
    }

    private static void deleteGeneratedFiles(String lexerAndParserLocation) {
        try (var dirStream = Files.walk(Paths.get(lexerAndParserLocation))) {
            dirStream.map(Path::toFile)
                    .sorted(Comparator.reverseOrder())
                    .forEach(File::delete);
        } catch (IOException e) {
            LOGGER.warn("Could not delete generated files in: {}", lexerAndParserLocation);
        }
    }
}
