import com.buschmais.jqassistant.core.test.plugin.AbstractPluginIT;
import org.junit.jupiter.api.Test;

import java.io.File;


class AntlrScannerPluginTest extends AbstractPluginIT {

    @Test
    void scan() {
        File file = new File("src/test/resources/logging/Logging.g4");

        getScanner().scan(file, file.getAbsolutePath(), null);
    }
}