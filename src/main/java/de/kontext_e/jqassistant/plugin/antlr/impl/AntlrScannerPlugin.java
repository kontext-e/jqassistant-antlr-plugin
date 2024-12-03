package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.Store;
import com.buschmais.jqassistant.core.store.api.model.Descriptor;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, Descriptor> {

    private AntlrAnalyzer antlrAnalyzer;
    private Store store;

    @Override
    public Descriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        String grammarName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
        File grammarFile = new File(path);
        File parsedFile = new File("src/test/resources/logging/output.logging");

        store = scanner.getContext().getStore();

        antlrAnalyzer = new AntlrAnalyzer(grammarName, "log", grammarFile);
        String lexerAndParserLocation = antlrAnalyzer.generateLexerAndParser();

        URL lexerAndParserURL = new File(lexerAndParserLocation).toURI().toURL();
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{lexerAndParserURL});
        List<ParseTree> parseTrees = loadParserAndParseFile(classLoader, parsedFile);

        for (ParseTree parseTree : parseTrees) {
            iterateOverParseTree(null, parseTree);
        }

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
        AntlrDescriptor node = createDescriptor(parseTree);
        if (parent != null) {
            parent.getChildren().add(node);
        }
        System.out.println(parseTree.getClass().getName() + ": " + parseTree.getText());

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            ParseTree child = parseTree.getChild(i);
            iterateOverParseTree(node, child);
        }
    }

    private AntlrDescriptor createDescriptor(ParseTree parseTree) {
        AntlrDescriptor descriptor = store.create(AntlrDescriptor.class);
        descriptor.setText(parseTree.getText());
        Object descriptorId = descriptor.getId();

        String label =parseTree.getClass().getName();

        Matcher matcher = Pattern.compile("\\$(.*?)Context").matcher(label);
        if (matcher.find()) {
            String nodeLabel = matcher.group(1);
            //TODO Query Parameter using neo4j not String.format
            String query = String.format("MATCH (n) WHERE id(n) = %s SET n:%s", descriptorId, nodeLabel);
            store.executeQuery(query).close();
        }

        return descriptor;
    }

    @Override
    public boolean accepts(FileResource fileResource, String s, Scope scope) throws IOException {
        return s.endsWith(".g4");
    }
}
