package de.kontext_e.jqassistant.plugin.antlr;

import com.buschmais.jqassistant.core.scanner.api.Scanner;
import com.buschmais.jqassistant.core.scanner.api.Scope;
import com.buschmais.jqassistant.core.store.api.model.Descriptor;
import com.buschmais.jqassistant.plugin.common.api.scanner.AbstractScannerPlugin;
import com.buschmais.jqassistant.plugin.common.api.scanner.filesystem.FileResource;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class AntlrScannerPlugin extends AbstractScannerPlugin<FileResource, Descriptor> {

    private AntlrAnalyzer antlrAnalyzer;

    @Override
    public Descriptor scan(FileResource fileResource, String path, Scope scope, Scanner scanner) throws IOException {
        String grammarName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
        File grammarFile = new File(path);
        File parsedFile = new File("src/test/resources/logging/output.logging");

        antlrAnalyzer = new AntlrAnalyzer(grammarName, "log", grammarFile);
        String lexerAndParserLocation = antlrAnalyzer.generateLexerAndParser();

        URL lexerAndParserURL = new File(lexerAndParserLocation).toURI().toURL();
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{lexerAndParserURL});
        List<ParseTree> parseTrees = loadParserAndParseFile(classLoader, parsedFile);

        parseTrees.forEach(this::iterateOverParseTree);

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

    private void iterateOverParseTree(ParseTree parseTree) {
        //Save to db
        System.out.println(parseTree.getClass().getName() + ": " + parseTree.getText());

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            ParseTree child = parseTree.getChild(i);
            iterateOverParseTree(child);
        }
    }

    @Override
    public boolean accepts(FileResource fileResource, String s, Scope scope) throws IOException {
        return s.endsWith(".g4");
    }
}
