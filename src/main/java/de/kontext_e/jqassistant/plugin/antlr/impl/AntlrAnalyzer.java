package de.kontext_e.jqassistant.plugin.antlr.impl;

import org.antlr.v4.Tool;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.tree.ParseTree;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AntlrAnalyzer {

    private final String grammarName;
    private final String grammarRoot;
    private final File grammarFile;

    public AntlrAnalyzer(File grammarFile) {
        this.grammarFile = grammarFile;
        String path = grammarFile.getAbsolutePath();
        this.grammarName = path.substring(path.lastIndexOf(File.separator) + 1, path.lastIndexOf('.'));
        this.grammarRoot = grammarName.substring(0, 1).toLowerCase() + grammarName.substring(1);
    }

    public String generateLexerAndParser() {
        String lexerAndParserLocation = generateParser(grammarFile);
        compileJavaFiles(findJavaFilesInDirectory(lexerAndParserLocation));
        return lexerAndParserLocation;
    }

    private static String generateParser(File grammarFile) {
        String outputDirectory = grammarFile.getParentFile().getAbsolutePath() + File.separator + ".antlrPlugin";
        new File(outputDirectory).mkdirs();

        List<String> arguments = new ArrayList<>();
        arguments.add(grammarFile.getAbsolutePath());
        arguments.add("-o");
        arguments.add(outputDirectory);

        Tool tool = new Tool(arguments.toArray(new String[0]));
        tool.processGrammarsOnCommandLine();

        return outputDirectory;
    }

    private static List<String> findJavaFilesInDirectory(String lexerParserDirectory) {
        List<String> sourceFiles;
        try (Stream<Path> walk = Files.walk(Paths.get(lexerParserDirectory))) {
            sourceFiles = walk.filter(p -> !Files.isDirectory(p))
                    .map(Path::toString)
                    .filter(p -> p.endsWith(".java"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sourceFiles;
    }

    private void compileJavaFiles(List<String> javaFiles) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(javaFiles);

        compiler.getTask(new StringWriter(), fileManager, null, null, null, compilationUnits).call();
    }

    @SuppressWarnings("unchecked")
    public List<ParseTree> loadParserAndParseFile(ClassLoader classLoader, File file) throws ClassNotFoundException, IOException,
            InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {

        Class<?> parserClass = Class.forName(grammarName + "Parser", true, classLoader);
        Class<?> lexerClass = Class.forName(grammarName + "Lexer", true, classLoader);

        Object lexerObject = lexerClass.getDeclaredConstructors()[0].newInstance(CharStreams.fromPath(file.toPath()));
        CommonTokenStream tokenStream = new CommonTokenStream((TokenSource) lexerObject);
        Object parserInstance = parserClass.getDeclaredConstructors()[0].newInstance(tokenStream);

        Method method = parserClass.getMethod(grammarRoot);
        Object returnValue = method.invoke(parserInstance);

        //TODO Verify that this works
        String rootContextClassName = grammarRoot.substring(0, 1).toUpperCase() + grammarRoot.substring(1);
        Class<?> contextClass = Class.forName(grammarName + "Parser$" + rootContextClassName + "Context", true, classLoader);
        Field children = contextClass.getSuperclass().getDeclaredField("children");

        Object parseTrees = children.get(returnValue);
        return (List<ParseTree>) parseTrees;
    }
}
