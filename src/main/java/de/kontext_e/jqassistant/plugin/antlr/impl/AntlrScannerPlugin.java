package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
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

import static de.kontext_e.jqassistant.plugin.antlr.impl.Utils.capitalizeFirstLetter;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, GrammarFileDescriptor> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrScannerPlugin.class);

    private static final String PLUGIN_PROPERTY_PREFIX = "jqassistant.plugin.antlr.";
    private static final String CREATE_NODES_CONTAINING_EMPTY_TEXT = PLUGIN_PROPERTY_PREFIX + "createNodesContainingEmptyText";
    private static final String DELETE_LEXER_AND_PARSER_AFTER_SCAN = PLUGIN_PROPERTY_PREFIX + "deleteLexerAndParserAfterScan";
    private static final String GRAMMAR_PROPERTY = "\"" + PLUGIN_PROPERTY_PREFIX + "grammars\"";

    private boolean createEmptyNodes;
    private boolean deleteParserAndLexerAfterScan;
    private Map<String, Map<String, String>> grammarConfigurations = new HashMap<>();

    private AntlrTool antlrTool;
    private Store store;
    private ParseTreeSaver parseTreeSaver;

    @Override
    protected void configure() {
        createEmptyNodes = getBooleanProperty(CREATE_NODES_CONTAINING_EMPTY_TEXT, false);
        deleteParserAndLexerAfterScan = getBooleanProperty(DELETE_LEXER_AND_PARSER_AFTER_SCAN, false);
        grammarConfigurations = getGrammarConfigurations();
        super.configure();
    }

    private HashMap<String, Map<String, String>> getGrammarConfigurations() {

        HashMap<String, Map<String, String>> grammarConfigurations = new HashMap<>();
        Map<String, Object> properties = getProperties();

        int i = 0;
        while (true){
            String fileExtension = (String) properties.get(GRAMMAR_PROPERTY + "[" + i + "].fileExtension");
            String grammarFileName = (String) properties.get(GRAMMAR_PROPERTY + "[" + i + "].grammarFile");
            String grammarRoot = (String) properties.get(GRAMMAR_PROPERTY + "[" + i + "].grammarRoot");

            if (fileExtension == null && grammarFileName == null) break;
            if (fileExtension == null || grammarFileName == null) {
                LOGGER.error(
                        "Incomplete configuration found at {}. Make sure to specify both the fileExtension and grammarFile!",
                        fileExtension != null ? fileExtension : grammarFileName
                );
                break;
            }

            File grammarFile = new File(grammarFileName);
            if (!grammarFile.isAbsolute()) grammarFile = grammarFile.getAbsoluteFile();

            String grammarName = getGrammarName(grammarFileName);
            if (grammarRoot == null) grammarRoot = grammarName.toLowerCase();

            Map<String, String> grammarConfiguration = Map.of(
                    "grammarName", grammarName,
                    "grammarRoot", grammarRoot,
                    "grammarFile", grammarFile.getAbsolutePath()
            );
            grammarConfigurations.put(fileExtension, grammarConfiguration);

            i++;
        }
        return grammarConfigurations;
    }

    static String getGrammarName(String grammarFile) {
        grammarFile = grammarFile.replace('/', File.separatorChar);
        grammarFile = grammarFile.replace('\\', File.separatorChar);
       return grammarFile.substring(grammarFile.lastIndexOf(File.separator) + 1, grammarFile.lastIndexOf('.'));
    }

    @Override
    public boolean accepts(FileResource fileResource, String s, Scope scope) throws IOException {
        String fileExtension = getFileExtension(fileResource.getFile());
        return grammarConfigurations.containsKey(fileExtension);
    }

    private static String getFileExtension(File file) {
        return file.getName().substring(file.getName().lastIndexOf('.'));
    }

    @Override
    public GrammarFileDescriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        File file = fileResource.getFile();

        Map<String, String> grammarConfiguration = grammarConfigurations.get(getFileExtension(file));

        store = scanner.getContext().getStore();
        parseTreeSaver = new ParseTreeSaver(store, createEmptyNodes);
        antlrTool = new AntlrTool(grammarConfiguration);

        String lexerAndParserLocation = antlrTool.getLexerAndParser();
        ScannedFileDescriptor scannedFile = parseFilesAndStoreTrees(file, lexerAndParserLocation);
        addGrammarRootNameToScannedFiles(scannedFile, grammarConfiguration.get("grammarRoot"));

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
