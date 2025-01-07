import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.core.test.plugin.AbstractPluginIT;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.AntlrDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.GrammarFileDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.ScannedFileDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AntlrScannerPluginTest extends AbstractPluginIT {

    File file = new File("src/test/resources/logging/Logging.g4");
    Map<String, Object> properties = Map.of(
            "\"jqassistant.plugin.antlr.grammars\"[0].grammar", "Logging.g4",
            "\"jqassistant.plugin.antlr.grammars\"[0].grammarRoot", "log",
            "\"jqassistant.plugin.antlr.grammars\"[0].fileExtension", ".logging"
    );

    @BeforeEach
    void setUp() {
        store.beginTransaction();
        FileDescriptor fileDescriptor = store.create(FileDescriptor.class);
        getScanner(properties).scan(file, fileDescriptor, file.getAbsolutePath(), DefaultScope.NONE);
    }

    @AfterEach
    void tearDown() {
        store.commitTransaction();
        store.beginTransaction();
        query("Match (n) Detach Delete n");
        store.commitTransaction();
    }

    @Test
    void testRelationsBetweenFilesAndTree() {
        String query = "MATCH (n:Grammar:Antlr:File) RETURN n";
        TestResult result = query(query);
        List<GrammarFileDescriptor> grammarFiles = result.getColumn("n");
        assertThat(grammarFiles).hasSize(1);

        var grammarFileDescriptor = grammarFiles.get(0);
        var scannedFiles = grammarFileDescriptor.getScannedFiles();
        assertThat(scannedFiles).hasSize(1);

        var scannedFileDescriptor = scannedFiles.get(0);
        assertThat(scannedFileDescriptor.getChildren().size()).isGreaterThan(0);
    }
}