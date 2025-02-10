package de.kontext_e.jqassistant.plugin.antlr.impl;

import de.kontext_e.jqassistant.plugin.antlr.api.config.GrammarConfiguration;
import org.antlr.v4.Tool;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.kontext_e.jqassistant.plugin.antlr.impl.Utils.capitalizeFirstLetter;
import static de.kontext_e.jqassistant.plugin.antlr.impl.Utils.getGrammarName;

public class AntlrTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntlrTool.class);

    private final String grammarName;
    private final String grammarRoot;
    private final File grammarFile;

    public AntlrTool(GrammarConfiguration grammarConfiguration) {
        this.grammarFile = new File(grammarConfiguration.grammarFile());
        this.grammarName = grammarConfiguration.grammarName().orElse(getGrammarName(grammarConfiguration.grammarFile()));
        this.grammarRoot = grammarConfiguration.grammarRoot().orElse(grammarName.toLowerCase());
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
        String pluginClassPath = new BasicClasspathResolver().getPluginClassPath();
        // The following line and the null is unfortunately necessary to make the tests run again, as during the tests,
        // this class is not loaded with an url Classloader but instead with an AppClassLoader
        List<String> options = pluginClassPath.isEmpty() ? null : Arrays.asList("-classpath", pluginClassPath);
        StringWriter compilerOutput = new StringWriter();

        compiler.getTask(compilerOutput, fileManager, null, options, null, compilationUnits).call();

        if (!compilerOutput.toString().isEmpty()) {
            LOGGER.error("Compilation failed: {}", compilerOutput);
        }
    }


    @SuppressWarnings("unchecked")
    public List<ParseTree> loadParserAndParseFile(String lexerAndParserLocation, File file) throws ClassNotFoundException, IOException,
            InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException {

        URL lexerAndParserURL = new File(lexerAndParserLocation).toURI().toURL();
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{lexerAndParserURL}, getClass().getClassLoader());

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
