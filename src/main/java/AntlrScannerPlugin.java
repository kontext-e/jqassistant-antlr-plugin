import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class AntlrScannerPlugin {

    private AntlrAnalyzer antlrAnalyzer;

    public void run() throws IOException {
        String grammarName = "Logging";
        File grammarFile = new File("src/main/antlr4/Logging.g4");
        File parsedFile = new File("src/main/resources/output.logging");

        antlrAnalyzer = new AntlrAnalyzer(grammarName, "log", grammarFile);
        String lexerAndParserLocation = antlrAnalyzer.generateLexerAndParser();

        URL lexerAndParserURL = new File(lexerAndParserLocation).toURI().toURL();
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{lexerAndParserURL});
        List<ParseTree> parseTrees = loadParserAndParseFile(classLoader, parsedFile);

        parseTrees.forEach(this::iterateOverParseTree);
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

}
