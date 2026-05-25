/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.moesif.handler;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.UserIdNotFoundException;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;

import java.time.Instant;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_ORG_SWITCH;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ORG_SWITCH_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ORG_SWITCH_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ORG_SWITCH_STREAM_NAME;

/**
 * Event handler that publishes organization-switch token grant events to Moesif.
 *
 * <p>Triggered when a user switches organization via the {@code OrganizationSwitchGrant}
 * grant type and publishes user-residency/login-org data together with service-provider
 * information to the Moesif Actions API.
 */
public class MoesifOrgSwitchDataPublishHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(MoesifOrgSwitchDataPublishHandler.class);
    private static final String POST_ORGANIZATION_SWITCH_EVENT = "POST_ORGANIZATION_SWITCH_EVENT";
    private static final String EVENT_PROP_AUTHENTICATED_USER = "AUTHENTICATED_USER";
    private static final String EVENT_PROP_APPLICATION_NAME = "APPLICATION_NAME";
    private static final String EVENT_PROP_APPLICATION_TENANT_DOMAIN = "APPLICATION_TENANT_DOMAIN";
    private static final String EVENT_PROP_TENANT_DOMAIN = "TENANT_DOMAIN";

    @Override
    public String getName() {

        return ORG_SWITCH_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        if (!isEnabled()) {
            return;
        }

        if (!POST_ORGANIZATION_SWITCH_EVENT.equals(event.getEventName())) {
            return;
        }

        Map<String, Object> eventProperties = event.getEventProperties();

        AuthenticatedUser authenticatedUser = (AuthenticatedUser) eventProperties.get(EVENT_PROP_AUTHENTICATED_USER);
        String applicationName = (String) eventProperties.get(EVENT_PROP_APPLICATION_NAME);
        String applicationTenantDomain = (String) eventProperties.get(EVENT_PROP_APPLICATION_TENANT_DOMAIN);
        String tenantDomain = (String) eventProperties.get(EVENT_PROP_TENANT_DOMAIN);
        String errorCode = (String) eventProperties.get(IdentityEventConstants.EventProperty.ERROR_CODE);

        if (authenticatedUser == null) {
            LOG.warn("AuthenticatedUser is null in OAuthTokenReqMessageContext; skipping org-switch event.");
            return;
        }

        // User's home organisation (where the user account resides).
        String userResidentOrgId = StringUtils.defaultIfBlank(
                authenticatedUser.getUserResidentOrganization(), NOT_AVAILABLE);

        // Organisation the user switched to (login org).
        String accessingOrgId = StringUtils.defaultIfBlank(
                authenticatedUser.getAccessingOrganization(), NOT_AVAILABLE);

        // Resolve the company UUID for Moesif from the current tenant domain.
        String companyId = NOT_AVAILABLE;
        String rootTenantDomain = tenantDomain;
        try {
            String resolvedOrgId = OrganizationManagementConstants.SUPER_ORG_ID;
            if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                rootTenantDomain =
                        OrganizationManagementUtil.getRootOrgTenantDomainBySubOrgTenantDomain(tenantDomain);
                resolvedOrgId = MoesifHandlerDataHolder.getInstance()
                        .getOrganizationManager()
                        .resolveOrganizationId(rootTenantDomain);
            }
            if (StringUtils.isNotBlank(resolvedOrgId)) {
                companyId = resolvedOrgId;
            }
        } catch (OrganizationManagementException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not resolve organisation ID for tenant '" + tenantDomain
                        + "'; using NOT_AVAILABLE as company ID.", e);
            }
        }

        // User identifier from the authenticated user.
        String userId = NOT_AVAILABLE;
        try {
            String resolvedUserId = authenticatedUser.getUserId();
            if (StringUtils.isNotBlank(resolvedUserId)) {
                userId = resolvedUserId;
            }
        } catch (UserIdNotFoundException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not resolve user ID; using NOT_AVAILABLE.", e);
            }
        }

        Object[] metaData = MoesifHandlerUtils.getMetaDataArray(
                companyId, ACTION_NAME_ORG_SWITCH, userId, NOT_AVAILABLE, NOT_AVAILABLE);

        Object[] payloadData = buildPayload(
                userResidentOrgId, accessingOrgId, applicationName, applicationTenantDomain, tenantDomain, errorCode);

        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                new org.wso2.carbon.databridge.commons.Event(
                        ORG_SWITCH_STREAM_NAME, System.currentTimeMillis(),
                        metaData, null, payloadData);

        try {
            FrameworkUtils.startTenantFlow(rootTenantDomain);
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
        } finally {
            FrameworkUtils.endTenantFlow();
        }


        if (LOG.isDebugEnabled()) {
            LOG.debug("Published Moesif org-switch event for tenant: " + tenantDomain);
        }
    }

    private Object[] buildPayload(String userResidentOrgId, String accessingOrgId,
                                  String spName, String applicationTenantDomain, String tenantDomain, String errorCode) {

        String publishingTime = Instant.now().toString();

        Object[] payload = new Object[7];
        payload[0] = StringUtils.defaultIfBlank(userResidentOrgId, NOT_AVAILABLE);
        payload[1] = StringUtils.defaultIfBlank(accessingOrgId, NOT_AVAILABLE);
        payload[2] = StringUtils.defaultIfBlank(spName, NOT_AVAILABLE);
        payload[3] = StringUtils.defaultIfBlank(applicationTenantDomain, NOT_AVAILABLE);
        payload[4] = StringUtils.defaultIfBlank(tenantDomain, NOT_AVAILABLE);
        payload[5] = StringUtils.defaultIfBlank(errorCode, NOT_AVAILABLE);
        payload[6] = publishingTime;

        return payload;
    }

    private boolean isEnabled() {

        if (this.configs.getModuleProperties() != null) {
            String handlerEnabled = this.configs.getModuleProperties()
                    .getProperty(ORG_SWITCH_PUBLISHER_ENABLED);
            if (Boolean.parseBoolean(handlerEnabled)) {
                String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
                return MoesifHandlerUtils.isHandlerEnabledForPrimaryTenant(tenantDomain,
                        MoesifCommonConstants.MOESIF_ORG_SWITCH_PUBLISHER_ENABLED_PROPERTY);
            }
        }
        return false;
    }
}
