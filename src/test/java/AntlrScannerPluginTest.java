import com.buschmais.jqassistant.core.scanner.api.DefaultScope;
import com.buschmais.jqassistant.core.test.plugin.AbstractPluginIT;
import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.AntlrDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.GrammarFileDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.NodeDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.ScannedFileDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AntlrScannerPluginTest extends AbstractPluginIT {

    File file = new File("src/test/resources/logging/output.logging");
    Map<String, Object> properties = Map.of(
            "jqassistant.plugin.antlr.readOnlyConfiguredGrammars", "true",
            "\"jqassistant.plugin.antlr.grammars\"[0].grammarFile", "src/test/resources/logging/Logging.g4",
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

        var grammarFile = grammarFiles.get(0);
        var scannedFiles = grammarFile.getScannedFiles();
        assertThat(scannedFiles).hasSize(1);

        var scannedFileDescriptor = scannedFiles.get(0);
        assertThat(scannedFileDescriptor.getChildren().size()).isGreaterThan(0);
    }

    @Test
    void testCustomNodeLabels(){
        String query = "MATCH (n:Entry:Antlr) RETURN n";
        TestResult result = query(query);
        List<AntlrDescriptor> logEntries = result.getColumn("n");
        assertThat(logEntries).hasSize(6);
    }

    @Test
    void testCustomFileLabel(){
        String query = "MATCH (n:Log:Antlr) RETURN n";
        TestResult result = query(query);
        List<ScannedFileDescriptor> logEntries = result.getColumn("n");
        assertThat(logEntries).hasSize(1);
    }

    @Test
    void testLogEntryContent(){
        String query = "MATCH (n:Entry:Antlr) WHERE n.text CONTAINS '2018-May-05 14:20:18 INFO some error occurred' RETURN n";
        TestResult result = query(query);
        List<NodeDescriptor> logEntries = result.getColumn("n");
        assertThat(logEntries).hasSize(1);

        var logEntry = logEntries.get(0);
        var children = logEntry.getChildren();
        assertThat(children.size()).isEqualTo(3);
        assertThat(children.stream().map(NodeDescriptor::getText)).contains(
                "2018-May-05 14:20:18",
                "INFO",
                "some error occurred"
        );
    }
}