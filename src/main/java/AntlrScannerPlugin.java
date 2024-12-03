import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class AntlrScannerPlugin {

    public void run() throws IOException, NoSuchFieldException, ClassNotFoundException, InvocationTargetException,
            InstantiationException, IllegalAccessException, NoSuchMethodException {

        String grammarName = "Logging";
        File grammarFile = new File("src/main/antlr4/Logging.g4");

        AntlrAnalyzer antlrAnalyzer = new AntlrAnalyzer(grammarName, "log", grammarFile);
        String lexerAndParserLocation = antlrAnalyzer.generateLexerAndParser();

        URL lexerAndParserURL = new File(lexerAndParserLocation).toURI().toURL();
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{lexerAndParserURL});

        File file = new File("src/main/resources/output.logging");
        List<ParseTree> parseTrees = antlrAnalyzer.getParseTreeOfFile(file, classLoader);

        parseTrees.forEach(this::iterateOverParseTree);
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
