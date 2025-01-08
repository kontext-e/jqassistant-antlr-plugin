package de.kontext_e.jqassistant.plugin.antlr.impl;

import com.buschmais.jqassistant.core.store.api.Store;
import de.kontext_e.jqassistant.plugin.antlr.api.model.AntlrDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.NodeDescriptor;
import de.kontext_e.jqassistant.plugin.antlr.api.model.ScannedFileDescriptor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseTreeSaver {

    private final Store store;
    private final boolean createEmptyNodes;
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseTreeSaver.class);

    public ParseTreeSaver(Store store, boolean createEmptyNodes) {
        this.store = store;
        this.createEmptyNodes = createEmptyNodes;
    }

    void saveParseTreesToNeo4J(List<ParseTree> parseTrees, ScannedFileDescriptor rootNode) {
        for (ParseTree parseTree : parseTrees) {
            saveParseTreeToNeo4J(rootNode, parseTree);
        }
    }

    private void saveParseTreeToNeo4J(AntlrDescriptor parent, ParseTree parseTree) {
        if (parseTree.getText().isBlank() && createEmptyNodes) return;

        NodeDescriptor node = createDescriptor(parseTree);
        parent.getChildren().add(node);

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            ParseTree child = parseTree.getChild(i);
            saveParseTreeToNeo4J(node, child);
        }
    }

    private NodeDescriptor createDescriptor(ParseTree parseTree) {
        NodeDescriptor descriptor = store.create(NodeDescriptor.class);
        descriptor.setText(parseTree.getText());
        addCustomLabelToDescriptor(descriptor, parseTree);

        return descriptor;
    }

    private void addCustomLabelToDescriptor(AntlrDescriptor descriptor, ParseTree parseTree) {
        String className = parseTree.getClass().getName();
        Matcher matcher = Pattern.compile("\\$(.*?)Context").matcher(className);
        String nodeLabel = matcher.find() ? matcher.group(1) : "TerminalNode";

        //Cypher does not allow for parameterization of labels, which is why string formatting is used
        String query = String.format("MATCH (n) WHERE id(n) = %s SET n:%s", descriptor.getId(), nodeLabel);
        store.executeQuery(query).close();
    }
}
