import com.buschmais.jqassistant.core.test.plugin.AbstractPluginIT;
import com.buschmais.xo.api.Query;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;


class AntlrScannerPluginTest extends AbstractPluginIT {

    @Test
    void scan() {
        File file = new File("src/test/resources/logging/Logging.g4");

        Map<String, Object> properties = Map.of(
                "jqassistant.plugin.antlr.createNodesContainingEmptyText", true,
                "\"jqassistant.plugin.antlr.grammars\"[0].grammar", "Logging.g4",
                "\"jqassistant.plugin.antlr.grammars\"[0].grammarRoot", "log",
                "\"jqassistant.plugin.antlr.grammars\"[0].fileEnding", ".logging"
        );
        getScanner(properties).scan(file, "/Logging.g4", null);

        store.beginTransaction();
        Query.Result<Query.Result.CompositeRowObject> result = store.executeQuery("MATCH (n:Antlr) RETURN n");
        result.iterator().forEachRemaining(rowObject -> System.out.println(rowObject.toString()));

        Query.Result<Query.Result.CompositeRowObject> result1 = store.executeQuery("MATCH (n:Antlr)-[:HAS_CHILD]->(m:Antlr) RETURN n, m");
        result1.iterator().forEachRemaining(rowObject -> System.out.println(rowObject.toString()));

        Query.Result<Query.Result.CompositeRowObject> compositeRowObjects = store.executeQuery("MATCH (n:Antlr) RETURN count(n)");
        compositeRowObjects.iterator().forEachRemaining(rowObject -> System.out.println(rowObject.toString()));

        store.executeQuery("Match (n) Detach Delete n");

        store.commitTransaction();
    }
}