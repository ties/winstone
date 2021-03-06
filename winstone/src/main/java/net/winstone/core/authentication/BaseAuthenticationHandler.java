/*
 * Copyright 2003-2006 Rick Knowles <winstone-devel at lists sourceforge net>
 * Distributed under the terms of either:
 * - the common development and distribution license (CDDL), v1.0; or
 * - the GNU Lesser General Public License, v2.1 or later
 */
package net.winstone.core.authentication;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.winstone.core.WebAppConfiguration;

import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

/**
 * Base class for managers of authentication within Winstone. This class also
 * acts as a factory, loading the appropriate subclass for the requested auth
 * type.
 * 
 * @author mailto: <a href="rick_knowles@hotmail.com">Rick Knowles</a>
 * @version $Id: BaseAuthenticationHandler.java,v 1.6 2006/02/28 07:32:47
 *          rickknowles Exp $
 */
public abstract class BaseAuthenticationHandler implements AuthenticationHandler {

	private static org.slf4j.Logger logger = LoggerFactory.getLogger(BaseAuthenticationHandler.class);
	private static final transient String ELEM_REALM_NAME = "realm-name";
	protected final SecurityConstraint constraints[];
	protected final AuthenticationRealm realm;
	protected String realmName;

	/**
	 * Factory method - this parses the web.xml nodes and builds the correct
	 * subclass for handling that auth type.
	 */
	@SuppressWarnings("rawtypes")
	protected BaseAuthenticationHandler(final Node loginConfigNode, final List constraintNodes, final Set rolesAllowed, final AuthenticationRealm realm) {
		this.realm = realm;

		for (int m = 0; m < loginConfigNode.getChildNodes().getLength(); m++) {
			final Node loginElm = loginConfigNode.getChildNodes().item(m);
			if (loginElm.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			} else if (loginElm.getNodeName().equals(BaseAuthenticationHandler.ELEM_REALM_NAME)) {
				realmName = WebAppConfiguration.getTextFromNode(loginElm);
			}
		}

		// Build security constraints
		constraints = new SecurityConstraint[constraintNodes.size()];
		for (int n = 0; n < constraints.length; n++) {
			constraints[n] = new SecurityConstraint((Node) constraintNodes.get(n), rolesAllowed, n);
		}
	}

	/**
	 * Evaluates any authentication constraints, intercepting if auth is
	 * required. The relevant authentication handler subclass's logic is used to
	 * actually authenticate.
	 * 
	 * @return A boolean indicating whether to continue after this request
	 */
	@Override
	public boolean processAuthentication(final ServletRequest inRequest, final ServletResponse inResponse, final String pathRequested) throws IOException, ServletException {
		BaseAuthenticationHandler.logger.debug("Starting authentication check");

		final HttpServletRequest request = (HttpServletRequest) inRequest;
		final HttpServletResponse response = (HttpServletResponse) inResponse;

		// Give previous attempts a chance to be validated
		if (!validatePossibleAuthenticationResponse(request, response, pathRequested)) {
			return Boolean.FALSE;
		} else {
			return doRoleCheck(request, response, pathRequested);
		}
	}

	protected boolean doRoleCheck(final HttpServletRequest request, final HttpServletResponse response, final String pathRequested) throws IOException, ServletException {
		// Loop through constraints
		boolean foundApplicable = Boolean.FALSE;
		for (int n = 0; (n < constraints.length) && !foundApplicable; n++) {
			BaseAuthenticationHandler.logger.debug("Evaluating security constraint: {}", constraints[n].getName());

			// Find one that applies, then
			if (constraints[n].isApplicable(pathRequested, request.getMethod())) {
				BaseAuthenticationHandler.logger.debug("Found applicable security constraint: {}", constraints[n].getName());
				foundApplicable = Boolean.TRUE;

				if (constraints[n].needsSSL() && !request.isSecure()) {
					final String msg = "Security constraint requires SSL (failed): " + constraints[n].getName();
					BaseAuthenticationHandler.logger.debug(msg);
					response.sendError(HttpServletResponse.SC_FORBIDDEN, msg);
					return Boolean.FALSE;
				} else if (!constraints[n].isAllowed(request)) {
					// Logger.log(Logger.FULL_DEBUG,
					// "Not allowed - requesting auth");
					requestAuthentication(request, response, pathRequested);
					return Boolean.FALSE;
				} else {
					// Logger.log(Logger.FULL_DEBUG,
					// "Allowed - authorization accepted");
					// Ensure that secured resources are not cached
					setNoCache(response);
				}
			}
		}

		// If we made it this far without a check being run, there must be none
		// applicable
		BaseAuthenticationHandler.logger.debug("Passed authentication check");
		return Boolean.TRUE;
	}

	protected void setNoCache(final HttpServletResponse response) {
		response.setHeader("Pragma", "No-cache");
		response.setHeader("Cache-Control", "No-cache");
		response.setDateHeader("Expires", 1);
	}

	/**
	 * The actual auth request implementation.
	 */
	protected abstract void requestAuthentication(final HttpServletRequest request, final HttpServletResponse response, final String pathRequested) throws IOException, ServletException;

	/**
	 * Handling the (possible) response
	 */
	protected abstract boolean validatePossibleAuthenticationResponse(final HttpServletRequest request, final HttpServletResponse response, final String pathRequested) throws ServletException, IOException;
}
