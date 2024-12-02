import org.antlr.v4.Tool;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Antlr {

    public void run() throws IOException {
        String grammarName = "Logging";
        String lexerParserDirectory = generateParser(grammarName);

        List<Class<?>> classes = loadLexerAndParser(lexerParserDirectory, grammarName);
    }

    //TODO rename variables to reflect, that they are absolute Paths
    private List<Class<?>> loadLexerAndParser(String lexerParserDirectory, String grammarName) throws IOException {
        List<String> javaFilesInDirectory = findFilesWithSuffixInDirectory(lexerParserDirectory, ".java");
        compileJavaFiles(javaFilesInDirectory);
        loadClassesToClasspath(lexerParserDirectory);


        return null;
    }

    private void loadClassesToClasspath(String lexerParserDirectory) {
        try (URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] {new File(lexerParserDirectory).toURI().toURL()})){
            List<String> classFiles = findFilesWithSuffixInDirectory(lexerParserDirectory, ".class");

            Class<?> lexer = Class.forName("LoggingLexer", true, classLoader);
            Object instance = lexer.getDeclaredConstructors()[0].newInstance((Object) null);
            System.out.println(instance.getClass().getName());
        } catch (ClassNotFoundException | IOException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void compileJavaFiles(List<String> javaFiles) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(javaFiles);

        StringWriter stringWriter = new StringWriter();

        compiler.getTask(stringWriter, fileManager, null, null, null, compilationUnits).call();
    }

    private static List<String> findFilesWithSuffixInDirectory(String lexerParserDirectory, String suffix) {
        List<String> sourceFiles;
        try (Stream<Path> walk = Files.walk(Paths.get(lexerParserDirectory))){
            sourceFiles = walk.filter(p -> !Files.isDirectory(p))
                    .map(Path::toString)
                    .filter(p -> p.endsWith(suffix))
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
