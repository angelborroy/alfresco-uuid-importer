# Alfresco uuid-importer
--------------------------

## Caution##

This module modifies Alfresco importing behaviour when executing `Import` action for an ACP package. This addon does not provide any different import tool from Alfresco out-of-the-box.

## Background##

Alfresco provides by default different importer strategies:

1 - CreateNewNodeImporterStrategy:

Import strategy where imported nodes are always created regardless of whether a node of the same UUID already exists in the repository

2 - RemoveExistingNodeImporterStrategy

Importer strategy where an existing node (one with the same UUID) as a node being imported is first removed.  The imported node is placed in the location specified at import time.

3 - ReplaceExistingNodeImporterStrategy

Importer strategy where an existing node (one with the same UUID) as a node being imported is first removed.  The imported node is placed under the parent of the removed node.

4 - ThrowOnCollisionNodeImporterStrategy

Import strategy where an error is thrown when importing a node that has the same UUID of an existing node in the repository.

5 - UpdateExistingNodeImporterStrategy

Import strategy where imported nodes are updated if a node with the same UUID already exists in the repository.



**CreateNewNodePreservingExistingImporterStrategy** is a variant of Strategy 1. 

Documentation for Strategy 1 stays that the node will be created regardless of whether a node of the same UUID already exists, but this is not true by implementation. If a node in the repository exists with the same UUID coming from ACP, an exception is thrown and the import process stops.

By using this new ImporterStrategy, if a node in target repository exists having the same UUID provided in the ACP a new UUID is generated and this replacing is logged on a file. If that UUID did not exist in the repository, the node is created by using it.

This log file is called `import.log` by default and includes lines like the following:

````
00a71931-4edc-43af-886e-9c202dc0984c > 3ee615e6-cac6-49d1-a431-f0b4b0544adb
````

This format means: `Original UUID > New UUID`

**Alfresco UUID Importer**

**License**
The plugin is licensed under the [LGPL v3.0](http://www.gnu.org/licenses/lgpl-3.0.html). 

**State**
Current addon release is 1.0-SNAPSHOT

**Compatibility**
The current version has been developed using Alfresco 5.1 and Alfresco SDK 2.2.0, although it should run in Alfresco 5.0.d and Alfresco 5.0.c

***SOME original Alfresco resources have been overwritten***

Building the artifacts
----------------------
You can build the artifacts from source code using maven
```$ mvn clean package```
