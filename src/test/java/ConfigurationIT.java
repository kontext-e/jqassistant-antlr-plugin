import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.core.test.plugin.AbstractPluginIT;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigurationIT extends AbstractPluginIT {


    @BeforeEach
    void setUp() {
        store.beginTransaction();
    }

    @AfterEach
    void tearDown() {
        store.commitTransaction();
        store.beginTransaction();
        query("Match (n) Detach Delete n");
        store.commitTransaction();
    }


    private static File prepareCalculatorTempDir(File tempDir) throws IOException {
        var grammarfile = new File("src/test/resources/calculator/equation.g4");
        var file = new File("src/test/resources/calculator/weird.equation");

        var tempGrammarFile = tempDir.toPath().resolve("equation.g4").toFile();
        var tempFile = tempDir.toPath().resolve("weird.equation").toFile();

        Files.copy(grammarfile.toPath(), tempGrammarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return tempGrammarFile;
    }

    @Test
    void testDoCreateEmptyNodes(){
        var file = new File("src/test/resources/logging/output.logging");
        var fileDescriptor = store.create(FileDescriptor.class);
        Map<String, Object> properties = Map.of(
                "jqassistant.plugin.antlr.createNodesContainingEmptyText", "true",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammar", "src/test/resources/logging/Logging.g4",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammarRoot", "log",
                "\"jqassistant.plugin.antlr.grammars\"[0].fileExtension", ".logging"
        );

        getScanner(properties).scan(file, fileDescriptor, file.getAbsolutePath(), DefaultScope.NONE);

        var query = "MATCH (n:Antlr:Node) WHERE trim(n.text)='' RETURN n";
        var result = query(query);
        var nodes = result.getColumn("n");
        assertThat(nodes).hasSizeGreaterThan(0);
    }

    @Test
    void testDoNotCreateEmptyNodes(){
        var file = new File("src/test/resources/logging/output.logging");
        var fileDescriptor = store.create(FileDescriptor.class);
        Map<String, Object> properties = Map.of(
                "\"jqassistant.plugin.antlr.grammars\"[0].grammar", "src/test/resources/logging/Logging.g4",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammarRoot", "log",
                "\"jqassistant.plugin.antlr.grammars\"[0].fileExtension", ".logging"
        );

        getScanner(properties).scan(file, fileDescriptor, file.getAbsolutePath(), DefaultScope.NONE);

        var result = query("MATCH (n:Antlr:Node) WHERE trim(n.text)='' RETURN n");
        assertThrows(IllegalArgumentException.class, () -> result.getColumn("n"));
    }

    @Test
    void testDeletionOfGeneratedFiles(@TempDir Path tempDir) throws IOException {
        var tempGrammarFile = prepareDotTempDir(tempDir);

        var fileDescriptor = store.create(FileDescriptor.class);
        Map<String, Object> properties = Map.of(
                "jqassistant.plugin.antlr.deleteParserAndLexerAfterScan", "true",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammar", tempDir.resolve("DOT.g4"),
                "\"jqassistant.plugin.antlr.grammars\"[0].grammarRoot", "graph",
                "\"jqassistant.plugin.antlr.grammars\"[0].fileExtension", ".dot"
        );

        getScanner(properties).scan(tempGrammarFile, fileDescriptor, tempGrammarFile.getAbsolutePath(), DefaultScope.NONE);

        assertThat(tempDir.toFile().listFiles()).hasSize(2);
    }

    private static File prepareDotTempDir(Path tempDir) throws IOException {
        var lexerAndParserDirectory = new File("src/test/resources/dot/");
        var tempGrammarFile = tempDir.resolve("cluster.dot").toFile();

        // Copy the directory recursively
        Stream<Path> fileWalker = Files.walk(lexerAndParserDirectory.toPath());
        fileWalker.forEach(sourcePath -> {
            try {
                Path targetPath = tempDir.resolve(lexerAndParserDirectory.toPath().relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        fileWalker.close();
        return tempGrammarFile;
    }

    @Test
    void testPartialConfiguration(){
        var file = new File("src/test/resources/dot/cluster.dot");
        var fileDescriptor = store.create(FileDescriptor.class);
        Map<String, Object> properties = Map.of(
                "jqassistant.plugin.antlr.readOnlyConfiguredGrammars", "true",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammar", "src/test/resources/dot/DOT.g4",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammarRoot", "graph"
        );

        getScanner(properties).scan(file, fileDescriptor, file.getAbsolutePath(), DefaultScope.NONE);

        var query = "MATCH (n:Antlr:ScannedFile) RETURN n";
        var result = query(query);
        var nodes = result.getColumn("n");
        assertThat(nodes).hasSize(1);
    }


    @Test
    void testIncorrectConfiguration(){
        var file = new File("src/test/resources/dot/cluster.dot");
        var fileDescriptor = store.create(FileDescriptor.class);
        Map<String, Object> properties = Map.of(
                "jqassistant.plugin.antlr.readOnlyConfiguredGrammars", "true",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammar", "src/test/resources/dot/DOT.g4",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammarRoot", "dot"
        );

        getScanner(properties).scan(file, fileDescriptor, file.getAbsolutePath(), DefaultScope.NONE);

        var query = "MATCH (n:Antlr:Node) RETURN n";
        var result = query(query);
        //Assert that scan has successfully completed but no file was scanned --> log message must have been printed
        assertThrows(IllegalArgumentException.class, () -> result.getColumn("n"));
    }

}
