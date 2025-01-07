package de.kontext_e.jqassistant.plugin.antlr.api.model;

import com.buschmais.xo.neo4j.api.annotation.Label;

@Label("Node")
@SuppressWarnings("unused")
public interface NodeDescriptor extends AntlrDescriptor {

    String getText();
    void setText(String text);

}
