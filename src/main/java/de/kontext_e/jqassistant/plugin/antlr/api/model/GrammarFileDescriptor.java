package de.kontext_e.jqassistant.plugin.antlr.api.model;

import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.xo.neo4j.api.annotation.Relation;
import jdk.jfr.Label;

import java.util.List;

@Label("Grammar")
@SuppressWarnings("unused")
public interface GrammarFileDescriptor extends AntlrDescriptor, FileDescriptor {

    @Relation("SCANNED_FILE")
    List<ScannedFileDescriptor> getScannedFiles();
    void setScannedFiles(List<ScannedFileDescriptor> scannedFiles);

}
