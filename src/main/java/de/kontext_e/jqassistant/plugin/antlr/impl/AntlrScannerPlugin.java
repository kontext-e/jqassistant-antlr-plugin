package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import de.kontext_e.jqassistant.plugin.antlr.api.model.AntlrDescriptor;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, AntlrDescriptor> {

    public static final String CREATE_NODES_CONTAINING_EMPTY_TEXT = "jqassistant.plugin.antlr.createNodesContainingEmptyText";

    private AntlrAnalyzer antlrAnalyzer;

    private Store store;
    private boolean createEmptyNodes;

    @Override
    protected void configure() {
        String property = getProperty(CREATE_NODES_CONTAINING_EMPTY_TEXT, String.class);
        createEmptyNodes = Set.of("true", "yes", "on").contains(property.toLowerCase());
        super.configure();
    }

    @Override
    public boolean accepts(FileResource fileResource, String s, Scope scope) throws IOException {
        return s.endsWith(".g4");
    }

    @Override
    public AntlrDescriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        File grammarFile = fileResource.getFile();

        store = scanner.getContext().getStore();

        antlrAnalyzer = new AntlrAnalyzer(grammarFile);
        String lexerAndParserLocation = antlrAnalyzer.generateLexerAndParser();

        URL lexerAndParserURL = new File(lexerAndParserLocation).toURI().toURL();
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{lexerAndParserURL});
        List<ParseTree> parseTrees = loadParserAndParseFile(classLoader, parsedFile);

        for (ParseTree parseTree : parseTrees) {
            iterateOverParseTree(null, parseTree);
        }

        //TODO
        return null;
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

    private void iterateOverParseTree(AntlrDescriptor parent, ParseTree parseTree) {
        if (parseTree.getText().isBlank() && !createEmptyNodes) return;

        AntlrDescriptor node = createDescriptor(parseTree);
        if (parent != null) {
            parent.getChildren().add(node);
        }

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            ParseTree child = parseTree.getChild(i);
            iterateOverParseTree(node, child);
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
