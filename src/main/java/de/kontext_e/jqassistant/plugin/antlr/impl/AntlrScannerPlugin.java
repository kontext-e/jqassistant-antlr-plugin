package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import de.kontext_e.jqassistant.plugin.antlr.api.model.AntlrDescriptor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, AntlrDescriptor> {

    public static final String CREATE_NODES_CONTAINING_EMPTY_TEXT = "jqassistant.plugin.antlr.createNodesContainingEmptyText";
    public static final String GRAMMAR_PROPERTY = "\"jqassistant.plugin.antlr.grammars\"";
    private boolean createEmptyNodes;
    private Map<String, Map<String, String>> grammarConfigurations = new HashMap<>();

    private Store store;
    private AntlrAnalyzer antlrAnalyzer;

    @Override
    protected void configure() {
        createEmptyNodes = getBooleanProperty(CREATE_NODES_CONTAINING_EMPTY_TEXT, true);
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
            String fileEnding = (String) properties.get(GRAMMAR_PROPERTY + "[" + i + "].fileEnding");

            if (grammarFile == null) break;

            String grammarName = grammarFile.substring(grammarFile.lastIndexOf(File.separator) + 1, grammarFile.lastIndexOf('.'));
            Map<String, String> grammarConfiguration = Map.of(
                    "grammarName", grammarName,
                    "grammarRoot", grammarRoot,
                    "fileEnding", fileEnding
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
        FileDescriptor fileDescriptor = scanner.getContext().getCurrentDescriptor();
        AntlrDescriptor antlrDescriptor = store.addDescriptorType(fileDescriptor, AntlrDescriptor.class);

        antlrAnalyzer = new AntlrAnalyzer(grammarFile, grammarConfigurations.get(grammarFile.getName()));
        String lexerAndParserLocation = antlrAnalyzer.generateLexerAndParser();

        URL lexerAndParserURL = new File(lexerAndParserLocation).toURI().toURL();
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{lexerAndParserURL});

        List<File> filesToBeParsed = getFilesToBeParsed(grammarFile);
        for (File fileToBeParsed : filesToBeParsed) {
            List<ParseTree> parseTrees = loadParserAndParseFile(classLoader, fileToBeParsed);
            for (ParseTree parseTree : parseTrees) {
                saveParseTreeToNeo4J(null, parseTree);
            }
        }

        try (var dirStream = Files.walk(Paths.get(lexerAndParserLocation))) {
            dirStream.map(Path::toFile)
                     .sorted(Comparator.reverseOrder())
                     .forEach(File::delete);
        }

        return antlrDescriptor;
    }

    private List<File> getFilesToBeParsed(File grammarFile) {
        ArrayList<File> files = new ArrayList<>();

        String grammarFileAbsolutePath = grammarFile.getAbsolutePath();
        String grammarName = grammarFileAbsolutePath.substring(grammarFileAbsolutePath.lastIndexOf(File.separator) + 1, grammarFileAbsolutePath.lastIndexOf('.'));
        String fileEnding = '.' + grammarName.toLowerCase();

        File parent = grammarFile.getParentFile();
        try (Stream<Path> stream = Files.walk(parent.toPath())){
            stream.filter(path -> path.toString().endsWith(fileEnding)).forEach(path -> files.add(path.toFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return files;
    }

    private List<ParseTree> loadParserAndParseFile(URLClassLoader classLoader, File parsedFile) {
        List<ParseTree> parseTrees = new ArrayList<>();

        try (classLoader){
            return antlrAnalyzer.loadParserAndParseFile(classLoader, parsedFile);
        } catch (IOException e) {
            System.out.println("There has been an error reading the File to be parsed: " + e.getMessage());
            return parseTrees;
        } catch (Exception e) {
            System.out.println("There has been an error while loading and executing the parser and lexer: " + e.getMessage());
            return parseTrees;
        }
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
}
