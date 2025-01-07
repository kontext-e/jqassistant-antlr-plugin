package de.kontext_e.jqassistant.plugin.antlr.api.model;

import com.buschmais.jqassistant.plugin.common.api.model.FileDescriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;

@SuppressWarnings("unused")
@Label("ScannedFile")
public interface ScannedFileDescriptor extends AntlrDescriptor, FileDescriptor {
}
