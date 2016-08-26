package es.keensoft.alfresco.importer;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.alfresco.repo.importer.Importer;
import org.alfresco.repo.importer.ImporterComponent;
import org.alfresco.repo.importer.Parser;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.view.ImportPackageHandler;
import org.alfresco.service.cmr.view.ImporterBinding;
import org.alfresco.service.cmr.view.ImporterProgress;
import org.alfresco.service.cmr.view.Location;

/**
 * This importerComponent does not include previous logic provided for ImportStrategy,
 * it works only with that new CreateNewNodePreservingExistingImporterStrategy
 * 
 * NOTE: As Alfresco implementation does not provide extension points, so reflection is used
 * to avoid copying every original resource
 */
public class EnhancedStrategyImporterComponent extends ImporterComponent {
	
	private CreateNewNodePreservingExistingImporterStrategy importStrategy;

    @Override
	public void parserImport(NodeRef nodeRef, Location location, Reader viewReader, ImportPackageHandler streamHandler, ImporterBinding binding, ImporterProgress progress) {
        
    	Importer nodeImporter = getNodeImporter(nodeRef, location, binding, streamHandler, progress);
    	importStrategy.setNodeImporter(nodeImporter);
    	setImportStrategy(nodeImporter);
    	
        try {
            nodeImporter.start();
            getViewParser().parse(viewReader, nodeImporter);
            nodeImporter.end();
        } catch(RuntimeException e) {
            nodeImporter.error(e);
            throw e;
        }
    }
    
    private Importer getNodeImporter(NodeRef nodeRef, Location location, ImporterBinding binding, ImportPackageHandler streamHandler, ImporterProgress progress) {
    	
    	try {
    	
	    	Class<?> c = Class.forName("org.alfresco.repo.importer.ImporterComponent$NodeImporter");
	    	Constructor<?> constructor = c.getDeclaredConstructor(ImporterComponent.class, NodeRef.class, Location.class, ImporterBinding.class, ImportPackageHandler.class, ImporterProgress.class);
	    	constructor.setAccessible(true);
	    	return (Importer) constructor.newInstance(this, nodeRef, location, binding, streamHandler, progress);
	    	
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }
    
    private void setImportStrategy(Importer nodeImporter) {
    	try {
	    	Class<?> c = Class.forName("org.alfresco.repo.importer.ImporterComponent$NodeImporter");
	    	Field f = c.getDeclaredField("importStrategy");
	    	f.setAccessible(true);
	    	f.set(nodeImporter, importStrategy);
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }
    
    private Parser getViewParser() {
    	try {
	    	Field f = ImporterComponent.class.getDeclaredField("viewParser");
	    	f.setAccessible(true);
	    	return (Parser) f.get(this);
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }

	public void setImportStrategy(CreateNewNodePreservingExistingImporterStrategy importStrategy) {
		this.importStrategy = importStrategy;
	}
    
}
