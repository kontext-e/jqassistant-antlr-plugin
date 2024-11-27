import groovy.lang.GroovyClassLoader;
import org.antlr.v4.Tool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {

    GroovyClassLoader loader = new GroovyClassLoader();

    public static void main(String[] args) {

        String grammarName = "Logging";
        String lexerParserDirectory = generateParser(grammarName);
    }


    private static String generateParser(String grammarName) {
        String grammarFileName = String.format("src/main/antlr4/%s.g4", grammarName);
        String outputDirectory = "src/main/antlr4/" + grammarName;

        List<String> arguments = new ArrayList<>();
        arguments.add(new File(grammarFileName).getAbsolutePath());
        arguments.add("-o");
        arguments.add(outputDirectory);

        Tool tool = new Tool(arguments.toArray(new String[0]));
        tool.processGrammarsOnCommandLine();

        return outputDirectory;
    }


}
