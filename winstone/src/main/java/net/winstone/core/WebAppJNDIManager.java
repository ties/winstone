/*
 * Copyright 2003-2006 Rick Knowles <winstone-devel at lists sourceforge net>
 * Distributed under the terms of either:
 * - the common development and distribution license (CDDL), v1.0; or
 * - the GNU Lesser General Public License, v2.1 or later
 */
package net.winstone.core;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

import org.w3c.dom.Node;

import net.winstone.core.WebAppConfiguration;

/**
 * Implements a simple web.xml + command line arguments style jndi manager
 * 
 * @author <a href="mailto:rick_knowles@hotmail.com">Rick Knowles</a>
 * @version $Id: WebAppJNDIManager.java,v 1.9 2006/02/28 07:32:48 rickknowles Exp $
 */
public class WebAppJNDIManager {

    protected static org.slf4j.Logger logger = LoggerFactory.getLogger(WebAppJNDIManager.class);
    private final static transient String ELEM_ENV_ENTRY = "env-entry";
    private final static transient String ELEM_ENV_ENTRY_NAME = "env-entry-name";
    private final static transient String ELEM_ENV_ENTRY_TYPE = "env-entry-type";
    private final static transient String ELEM_ENV_ENTRY_VALUE = "env-entry-value";

    /**
     * Gets the relevant list of objects from the args, validating against the
     * web.xml nodes supplied. All node addresses are assumed to be relative to
     * the java:/comp/env context
     */
    public WebAppJNDIManager(List<Node> webXMLNodes, ClassLoader loader) {


        // If the webXML nodes are not null, validate that all the entries we
        // wanted have been created
        if (webXMLNodes != null) {
            for (Iterator<Node> i = webXMLNodes.iterator(); i.hasNext();) {
                Node node = (Node) i.next();

                // Extract the env-entry nodes and create the objects
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                } else if (node.getNodeName().equals(ELEM_ENV_ENTRY)) {
                    String name = null;
                    String type = null;
                    String value = null;
                    for (int m = 0; m < node.getChildNodes().getLength(); m++) {
                        Node envNode = node.getChildNodes().item(m);
                        if (envNode.getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        } else if (envNode.getNodeName().equals(ELEM_ENV_ENTRY_NAME)) {
                            name = WebAppConfiguration.getTextFromNode(envNode);
                        } else if (envNode.getNodeName().equals(ELEM_ENV_ENTRY_TYPE)) {
                            type = WebAppConfiguration.getTextFromNode(envNode);
                        } else if (envNode.getNodeName().equals(ELEM_ENV_ENTRY_VALUE)) {
                            value = WebAppConfiguration.getTextFromNode(envNode);
                        }
                    }
                    if ((name != null) && (type != null) && (value != null)) {
                        logger.debug("Creating object {} from web.xml env-entry description", name);
                        createObject(name, type, value, loader);

                    }
                }
            }
        }
    }

    private void createObject(String name, String type, String value, ClassLoader loader) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}