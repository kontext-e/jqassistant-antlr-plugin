import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.core.test.plugin.AbstractPluginIT;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.impl.AntlrScannerPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
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

    @Test
    void testDoCreateEmptyNodes(){
        var file = new File("src/test/resources/logging/output.logging");
        var fileDescriptor = store.create(FileDescriptor.class);
        Map<String, Object> properties = Map.of(
                "jqassistant.plugin.antlr.configLocation", "src/test/resources/configFiles/doCreateEmptyNodes.yaml"
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
                "jqassistant.plugin.antlr.configLocation", "src/test/resources/configFiles/doNotCreateEmptyNodes.yaml"
        );

        getScanner(properties).scan(file, fileDescriptor, file.getAbsolutePath(), DefaultScope.NONE);

        var result = query("MATCH (n:Antlr:Node) WHERE trim(n.text)='' RETURN n");
        assertThrows(IllegalArgumentException.class, () -> result.getColumn("n"));
    }

    @Test
    void testDeletionOfGeneratedFiles(@TempDir Path tempDir) throws IOException {
        var tempFileToBeScanned = prepareDotTempDir(tempDir);
        Path deleteLexerAndParser = tempDir.resolve("deleteLexerAndParser.yaml");

        var fileDescriptor = store.create(FileDescriptor.class);
        Map<String, Object> properties = Map.of(
                "jqassistant.plugin.antlr.configLocation", deleteLexerAndParser.toString()
        );

        getScanner(properties).scan(tempFileToBeScanned, fileDescriptor, tempFileToBeScanned.getAbsolutePath(), DefaultScope.NONE);

        assertThat(tempDir.toFile().listFiles()).hasSize(3);
    }

    private static File prepareDotTempDir(Path tempDir) throws IOException {
        var lexerAndParserDirectory = new File("src/test/resources/dot/");
        var tempFileToBeScanned = tempDir.resolve("cluster.dot").toFile();
        var tempGrammarFile = tempDir.resolve("Dot.g4");
        var configFile = tempDir.resolve("deleteLexerAndParser.yaml").toFile();
        var configFileContent =
                "jqassistant:\n" +
                "  plugin:\n" +
                "    antlr:\n" +
                "      deleteLexerAndParserAfterScan: \"true\"\n" +
                "      grammars:\n" +
                "        - grammarFile: "+ tempGrammarFile +"\n" +
                "          grammarName: \"DOT\"\n" +
                "          grammarRoot: \"graph\"\n" +
                "          fileExtension: \".dot\"";
        Files.write(configFile.toPath(), configFileContent.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
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
        return tempFileToBeScanned;
    }

    @Test
    void testPartialConfiguration(){
        var file = new File("src/test/resources/equation/wierd.equation");
        var fileDescriptor = store.create(FileDescriptor.class);
        Map<String, Object> properties = Map.of(
                "jqassistant.plugin.antlr.configLocation", "src/test/resources/configFiles/partialConfiguration.yaml"
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
                "jqassistant.plugin.antlr.configLocation", "src/test/resources/configFiles/incorrectConfiguration.yaml"
        );

        getScanner(properties).scan(file, fileDescriptor, file.getAbsolutePath(), DefaultScope.NONE);

        var query = "MATCH (n:Antlr:Node) RETURN n";
        var result = query(query);
        //Assert that scan has successfully completed but no file was scanned --> log message must have been printed
        assertThrows(IllegalArgumentException.class, () -> result.getColumn("n"));
    }

}
