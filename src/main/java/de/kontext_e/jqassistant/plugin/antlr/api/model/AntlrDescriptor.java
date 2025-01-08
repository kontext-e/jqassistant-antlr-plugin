package de.kontext_e.jqassistant.plugin.antlr.api.model;

import com.buschmais.jqassistant.core.store.api.model.Descriptor;
import com.buschmais.xo.neo4j.api.annotation.Label;
import com.buschmais.xo.neo4j.api.annotation.Relation;

import java.util.List;

@Label("Antlr")
@SuppressWarnings("unused")
public interface AntlrDescriptor extends Descriptor {

    @Relation("HAS_CHILD")
    List<NodeDescriptor> getChildren();
    void setChildren(List<NodeDescriptor> children);
}
