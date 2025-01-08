# jQAssistant Plugin for Antlr grammars

This plugin uses antlr grammars to read and parse other files and stores their syntax trees into the jQAssistant Database.

## How to install

## Configure
You can configure how each grammar is treated:

```yaml
  scan:
    properties:
      jqassistant.plugin.antlr.grammars:
        - grammar: 'Logging.g4'
          grammarRoot: 'log'
          fileExtension: '.logging'
```

The configuration above tells the scanner that when it encounters the grammar file `Logging.g4` it should scan files with the file extension `.logging`. The plugin looks for those files in all subdirectories of the directory that contains the grammar file. The grammarRoot property tells the plugin, that the root element of the grammar is the `log` so it knows how to build the syntax tree.

If this configuration is omitted for a grammar, the grammar root defaults to the grammar name (in this example `Logging`) but as lowercase. The file extension defaults to the lowercased grammar name as well.
Analogously the grammarRoot and or the fileExtensionProperty can be omitted independently of each other resulting in the inferred values as described above

Additionally there are three properties to further configure the behavior of the plugin:

````yaml
scan:
  properties:
    jqassistant.plugin.antlr.createNodesContainingEmptyText = false
    jqassistant.plugin.antlr.deleteParserAndLexerAfterScan = false
    jqassistant.plugin.antlr.readOnlyConfiguredGrammars = false
````

### CreateNodesContainingEmptyText
This property allows you to prevent the creation of nodes that would only contain whitespace. The default is set to false, so no whitespace nodes are being created.

### DeleteParserAndLexerAfterScan
This plugin generates the Parser and Lexer during scanning and reuses them throughout the scan, to increase performance. To keep performance at a relatively high level, the lexer and parser are by default not being deleted after each scan. This however is only beneficial if there is no frequent change to the grammar, as the plugin can not automatically detect if the grammar has changed and thus if the lexer and parser need to be regenerated.

### ReadOnlyConfiguredGrammars
This plugin supports configuration of the grammars that are to be scanned as described above. 
If this property is set to true, only the configured grammars are being scanned, allowing for a granular selection of grammars. To avoid tedious configuration of each grammar, the grammarRoot and file Extension property can be omitted if their values can easily be inferred by the plugin.

If this property is set to false, all discovered grammars are being scanned and their configuration parameters are inferred as described above. This allows for less setup to use the plugin. If this is set to false, the configured grammars are still being taken into account and their configuration takes precedent.

## How it works