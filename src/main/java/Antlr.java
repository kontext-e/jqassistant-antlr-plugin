import groovy.lang.GroovyClassLoader;
import org.antlr.v4.Tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Antlr {

    GroovyClassLoader groovyClassLoader = new GroovyClassLoader();

    public void run() throws IOException {
        String grammarName = "Logging";
        String lexerParserDirectory = generateParser(grammarName);

        List<Class<?>> classes = loadLexerAndParser(lexerParserDirectory, grammarName);
    }

    private List<Class<?>> loadLexerAndParser(String lexerParserDirectory, String grammarName) throws IOException {
        List<String> javaFilesInDirectory = findJavaFilesInDirectory(lexerParserDirectory);
        List<Class<?>> loadedClasses = new ArrayList<>();

        for (String javaFile : javaFilesInDirectory) {
            File file = new File(javaFile);
            Class<?> parsedClass = groovyClassLoader.parseClass(file);
            loadedClasses.add(parsedClass);
        }

        return loadedClasses;
    }

    private static List<String> findJavaFilesInDirectory(String lexerParserDirectory) {
        List<String> sourceFiles;
        try (Stream<Path> walk = Files.walk(Paths.get(lexerParserDirectory))){
            sourceFiles = walk.filter(p -> !Files.isDirectory(p))
                    .map(p -> p.toString().toLowerCase())
                    .filter(p -> p.endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sourceFiles;
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

    private static void getSyntaxTree() {
        String test = "2018-May-05 14:20:18 INFO some error occurred\n" +
                "2018-May-05 14:20:19 INFO yet another error\n" +
                "2018-May-05 14:20:20 INFO some method started\n" +
                "2018-May-05 14:20:21 DEBUG another method started\n" +
                "2018-May-05 14:20:21 DEBUG entering awesome method\n" +
                "2018-May-05 14:20:24 ERROR Bad thing happened";
    }

}
