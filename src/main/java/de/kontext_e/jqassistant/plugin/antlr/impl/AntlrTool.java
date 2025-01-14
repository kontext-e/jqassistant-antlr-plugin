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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.kontext_e.jqassistant.plugin.antlr.impl.Utils.capitalizeFirstLetter;

public class AntlrTool {

    private final String grammarName;
    private final String grammarRoot;
    private final File grammarFile;

    public AntlrTool(Map<String, String> grammarConfiguration) {
        this.grammarFile = new File(grammarConfiguration.get("grammarFile"));
        this.grammarName = grammarConfiguration.get("grammarName");
        this.grammarRoot = grammarConfiguration.get("grammarRoot");
    }

    public String getLexerAndParser() throws IOException {
        String lexerAndParserLocation = grammarFile.getParentFile().getAbsolutePath() + File.separator + ".antlrPlugin" + grammarName;
        if (!javaFilesFoundInDirectory(lexerAndParserLocation)) {
            generateLexerAndParser(grammarFile, lexerAndParserLocation);
        }
        if (!classFilesFoundInDirectory(lexerAndParserLocation)) {
            compileJavaFiles(findJavaFilesInDirectory(lexerAndParserLocation));
        }
        return lexerAndParserLocation;
    }

    private static boolean classFilesFoundInDirectory(String lexerAndParserLocation) {
        File[] files = new File(lexerAndParserLocation).listFiles();
        if (files == null) return false;
        return Arrays.stream(files).anyMatch(file -> file.getName().endsWith(".class"));
    }

    private static boolean javaFilesFoundInDirectory(String lexerAndParserLocation) {
        File[] outputDirectory = new File(lexerAndParserLocation).listFiles();
        if (outputDirectory == null) return false;
        return Arrays.stream(outputDirectory).anyMatch(file -> file.getName().endsWith(".java"));
    }

    private void generateLexerAndParser(File grammarFile, String outputPath) throws IOException {
        File outputDirectory = new File(outputPath);

        boolean createdOutputDirectory = outputDirectory.mkdirs();
        if (!createdOutputDirectory && !outputDirectory.exists()) { throw new IOException("Error creating output directory: " + outputDirectory); }

        List<String> arguments = new ArrayList<>();
        arguments.add(grammarFile.getAbsolutePath());
        arguments.add("-o");
        arguments.add(outputPath);

        Tool tool = new Tool(arguments.toArray(new String[0]));
        tool.processGrammarsOnCommandLine();
    }

    private static List<String> findJavaFilesInDirectory(String lexerParserDirectory) throws IOException {
        List<String> sourceFiles;
        try (Stream<Path> walk = Files.walk(Paths.get(lexerParserDirectory))) {
            sourceFiles = walk.filter(p -> !Files.isDirectory(p))
                    .map(Path::toString)
                    .filter(p -> p.endsWith(".java"))
                    .collect(Collectors.toList());
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
    public List<ParseTree> loadParserAndParseFile(String lexerAndParserLocation, File file) throws ClassNotFoundException, IOException,
            InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {

        URL lexerAndParserURL = new File(lexerAndParserLocation).toURI().toURL();
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{lexerAndParserURL});

        Class<?> parserClass = Class.forName(grammarName + "Parser", true, classLoader);
        Class<?> lexerClass = Class.forName(grammarName + "Lexer", true, classLoader);

        Object lexerObject = lexerClass.getDeclaredConstructors()[0].newInstance(CharStreams.fromPath(file.toPath()));
        CommonTokenStream tokenStream = new CommonTokenStream((TokenSource) lexerObject);
        Object parserInstance = parserClass.getDeclaredConstructors()[0].newInstance(tokenStream);

        Method method = parserClass.getMethod(grammarRoot);
        Object returnValue = method.invoke(parserInstance);

        String rootContextClassName = capitalizeFirstLetter(grammarRoot);
        Class<?> contextClass = Class.forName(grammarName + "Parser$" + rootContextClassName + "Context", true, classLoader);
        Field children = contextClass.getSuperclass().getDeclaredField("children");

        classLoader.close();
        Object parseTrees = children.get(returnValue);
        return (List<ParseTree>) parseTrees;
    }
}
