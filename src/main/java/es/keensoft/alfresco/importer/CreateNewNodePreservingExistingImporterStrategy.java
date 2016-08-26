package es.keensoft.alfresco.importer;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.importer.ImportNode;
import org.alfresco.repo.importer.Importer;
import org.alfresco.repo.importer.ImporterComponent.NodeImporterStrategy;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.dictionary.TypeDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.cmr.security.AccessPermission;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.OwnableService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.view.ImporterException;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;

/** Alfresco provides by default different importer strategies:
 * 1 - CreateNewNodeImporterStrategy:
 *     Import strategy where imported nodes are always created regardless of whether a
 *     node of the same UUID already exists in the repository
 * 2 - RemoveExistingNodeImporterStrategy
 *     Importer strategy where an existing node (one with the same UUID) as a node being
 *     imported is first removed.  The imported node is placed in the location specified
 *     at import time.
 * 3 - ReplaceExistingNodeImporterStrategy
 *     Importer strategy where an existing node (one with the same UUID) as a node being
 *     imported is first removed.  The imported node is placed under the parent of the removed
 *     node.
 * 4 - ThrowOnCollisionNodeImporterStrategy
 *     Import strategy where an error is thrown when importing a node that has the same UUID
 *     of an existing node in the repository.
 * 5 - UpdateExistingNodeImporterStrategy
 *     Import strategy where imported nodes are updated if a node with the same UUID
 *     already exists in the repository.
 *     
 * CreateNewNodePreservingExistingImporterStrategy is a variant of Strategy 1. 
 * 
 * Documentation for Strategy 1 stays that the node will be created regardless of whether 
 * a node of the same UUID already exists, but this is not true by implementation. 
 * If a node in the repository exists with the same UUID coming from ACP, 
 * an exception is thrown and the import process stops.
 * 
 * By using this new ImporterStrategy, if a node in target repository exists having the 
 * same UUID provided in the ACP a new UUID is generated and this replacing is logged 
 * on a file. If that UUID did not exist in the repository, the node is created
 * by using it.
 * 
 * This log file is called "import.log" by default and includes lines like the following:
 * 
 * 00a71931-4edc-43af-886e-9c202dc0984c > 3ee615e6-cac6-49d1-a431-f0b4b0544adb
 * 
 * This format means: Original UUID > New UUID
 * 
 * NOTE: As Alfresco implementation does not provide extension points, so reflection is used
 * to avoid copying every original resource
 */  
public class CreateNewNodePreservingExistingImporterStrategy implements NodeImporterStrategy {
	
    private static final Log logger = LogFactory.getLog(CreateNewNodePreservingExistingImporterStrategy.class);
    private static final Logger importLogger = Logger.getLogger("importLogger");
    
	private NodeService nodeService;
    private RuleService ruleService;
    private PermissionService permissionService;
    private AuthorityService authorityService;
    private OwnableService ownableService;
    
    private BehaviourFilter behaviourFilter;
    
    private Importer nodeImporter;
    
	@Override
	public NodeRef importNode(ImportNode node) {
		
        TypeDefinition nodeType = node.getTypeDefinition();
        NodeRef parentRef = node.getParentContext().getParentRef();
        QName assocType = getAssocType(node);
        QName childQName = getChildName(node);
        if (childQName == null)
        {
            throw new ImporterException("Cannot determine child name of node (type: " + nodeType.getName() + ")");
        }

        // Disable the import-wide behaviours (because the node doesn't exist, yet)
        Set<QName> nodeTypeAndAspects = getNodeTypeAndAspects(node);
        for (QName typeOrAspect: nodeTypeAndAspects)
        {
            behaviourFilter.disableBehaviour(typeOrAspect);
        }
        
        // Build initial map of properties
        Map<QName, Serializable> initialProperties = bindProperties(node);
        
        // Detect existing nodes
        String uuid = node.getUUID();
        boolean logChange = false;
        if (uuid != null && uuid.length() > 0)
        {
            NodeRef existingNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, uuid);
            if (nodeService.exists(existingNodeRef)) {
            	// New UUID
            	logChange = true;
            } else {
            	// Use UUID from ACP
                initialProperties.put(ContentModel.PROP_NODE_UUID, node.getUUID());
            }
        }
                
        // Create Node
        ChildAssociationRef assocRef = nodeService.createNode(parentRef, assocType, childQName, nodeType.getName(), initialProperties);
        NodeRef nodeRef = assocRef.getChildRef();
        
        // Log UUID created
        if (logChange) {
        	logger.debug("Node created with UUID change from " + node.getUUID() + " to " + nodeRef.getId());
        	importLogger.info(node.getUUID() + " > " + nodeRef.getId());
        } else {
        	logger.debug("Node created with UUID " + node.getUUID());
        }

        // Note: non-admin authorities take ownership of new nodes
        // from Thor
        if (!(AuthenticationUtil.isRunAsUserTheSystemUser() || authorityService.hasAdminAuthority())) {
            ownableService.takeOwnership(nodeRef);
        }

        // apply permissions
        List<AccessPermission> permissions = null;
        AccessStatus writePermission = permissionService.hasPermission(nodeRef, PermissionService.CHANGE_PERMISSIONS);

        // from Thor
        if (AuthenticationUtil.isRunAsUserTheSystemUser() || writePermission.equals(AccessStatus.ALLOWED))
        {
            permissions = bindPermissions(node.getAccessControlEntries());
            
            for (AccessPermission permission : permissions)
            {
                permissionService.setPermission(nodeRef, permission.getAuthority(), permission.getPermission(), permission.getAccessStatus().equals(AccessStatus.ALLOWED));
            }
            // note: apply inheritance after setting permissions as this may affect whether you can apply permissions
            boolean inheritPermissions = node.getInheritPermissions();
            if (!inheritPermissions)
            {
                permissionService.setInheritParentPermissions(nodeRef, false);
            }
        }
        
        // Re-enable the import-wide behaviours
        // Disable behaviour for the node until the complete node (and its children have been imported)
        for (QName typeOrAspect: nodeTypeAndAspects)
        {
            behaviourFilter.enableBehaviour(typeOrAspect);
        }
        behaviourFilter.disableBehaviour(nodeRef);
        // TODO: Replace this with appropriate rule/action import handling
        ruleService.disableRules(nodeRef);

        // Report creation
        reportNodeCreated(assocRef);

        // return newly created node reference
        return nodeRef;
    }
	
	private QName getAssocType(ImportNode node) {
		try {
	    	Class<?> c = Class.forName("org.alfresco.repo.importer.ImporterComponent$NodeImporter");
			Method method = c.getDeclaredMethod("getAssocType", ImportNode.class);
			method.setAccessible(true);
			return (QName) method.invoke(nodeImporter, node);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}	
	
	private QName getChildName(ImportNode node) {
		try {
	    	Class<?> c = Class.forName("org.alfresco.repo.importer.ImporterComponent$NodeImporter");
			Method method = c.getDeclaredMethod("getChildName", ImportNode.class);
			method.setAccessible(true);
			return (QName) method.invoke(nodeImporter, node);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	private Set<QName> getNodeTypeAndAspects(ImportNode node) {
		try {
	    	Class<?> c = Class.forName("org.alfresco.repo.importer.ImporterComponent$NodeImporter");
			Method method = c.getDeclaredMethod("getNodeTypeAndAspects", ImportNode.class);
			method.setAccessible(true);
			return (Set<QName>) method.invoke(nodeImporter, node);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public Map<QName, Serializable> bindProperties(ImportNode node) {
		try {
	    	Class<?> c = Class.forName("org.alfresco.repo.importer.ImporterComponent$NodeImporter");
			Method method = c.getDeclaredMethod("bindProperties", ImportNode.class);
			method.setAccessible(true);
			return (Map<QName, Serializable>) method.invoke(nodeImporter, node);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	private List<AccessPermission> bindPermissions(List<AccessPermission> permissions) {
		try {
	    	Class<?> c = Class.forName("org.alfresco.repo.importer.ImporterComponent$NodeImporter");
			Method method = c.getDeclaredMethod("bindPermissions", List.class);
			method.setAccessible(true);
			return (List<AccessPermission>) method.invoke(nodeImporter, permissions);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void reportNodeCreated(ChildAssociationRef childAssocRef) {
		try {
	    	Class<?> c = Class.forName("org.alfresco.repo.importer.ImporterComponent$NodeImporter");
			Method method = c.getDeclaredMethod("reportNodeCreated", ChildAssociationRef.class);
			method.setAccessible(true);
			method.invoke(nodeImporter, childAssocRef);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	public void setPermissionService(PermissionService permissionService) {
		this.permissionService = permissionService;
	}

	public void setRuleService(RuleService ruleService) {
		this.ruleService = ruleService;
	}

	public void setAuthorityService(AuthorityService authorityService) {
		this.authorityService = authorityService;
	}

	public void setOwnableService(OwnableService ownableService) {
		this.ownableService = ownableService;
	}

	public void setBehaviourFilter(BehaviourFilter behaviourFilter) {
		this.behaviourFilter = behaviourFilter;
	}

	public void setNodeImporter(Importer nodeImporter) {
		this.nodeImporter = nodeImporter;
	}
	
}