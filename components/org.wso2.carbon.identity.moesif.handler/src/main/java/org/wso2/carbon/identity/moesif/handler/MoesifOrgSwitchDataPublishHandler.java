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
import org.wso2.carbon.identity.data.publisher.authentication.moesif.internal.MoesifDataPublishDataHolder;
import org.wso2.carbon.identity.data.publisher.authentication.moesif.util.MoesifDataPublishUtils;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.oauth.dao.OAuthAppDO;
import org.wso2.carbon.identity.oauth2.token.OAuthTokenReqMessageContext;
import org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.wso2.carbon.identity.data.publisher.authentication.moesif.MoesifDataPublishConstants.*;


/**
 * Event handler that publishes organization-switch token grant events to Moesif.
 *
 * <p>Triggered when a user switches organization via the {@code OrganizationSwitchGrant}
 * grant type and publishes user-residency/login-org data together with service-provider
 * information to the Moesif Actions API.
 */
public class MoesifOrgSwitchDataPublishHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(MoesifOrgSwitchDataPublishHandler.class);
    private static final String OAUTH_TOKEN_REQ_MESSAGE_CONTEXT = "OAUTH_TOKEN_REQ_MESSAGE_CONTEXT";
    private static final String POST_ORGANIZATION_SWITCH_EVENT = "POST_ORGANIZATION_SWITCH_EVENT";
    private static final String OAUTH_APP_DO_PROPERTY = "OAuthAppDO";

    @Override
    public String getName() {

        return ORG_SWITCH_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        if (!isEnabled(event)) {
            return;
        }

        if (!POST_ORGANIZATION_SWITCH_EVENT.equals(event.getEventName())) {
            return;
        }

        Map<String, Object> eventProperties = event.getEventProperties();

        OAuthTokenReqMessageContext tokenReqCtx =
                (OAuthTokenReqMessageContext) eventProperties.get(OAUTH_TOKEN_REQ_MESSAGE_CONTEXT);
        if (tokenReqCtx == null) {
            LOG.warn("OAuthTokenReqMessageContext is missing in " + POST_ORGANIZATION_SWITCH_EVENT + " event.");
            return;
        }

        String errorCode = (String) eventProperties.get(IdentityEventConstants.EventProperty.ERROR_CODE);

        AuthenticatedUser authorizedUser = tokenReqCtx.getAuthorizedUser();
        if (authorizedUser == null) {
            LOG.warn("AuthenticatedUser is null in OAuthTokenReqMessageContext; skipping org-switch event.");
            return;
        }

        // User's home organisation (where the user account resides).
        String userResidentOrgId = StringUtils.defaultIfBlank(
                authorizedUser.getUserResidentOrganization(), NOT_AVAILABLE);

        // Organisation the user switched to (login org).
        String accessingOrgId = StringUtils.defaultIfBlank(
                authorizedUser.getAccessingOrganization(), NOT_AVAILABLE);

        // Service-provider name carried in the token request context.
        String spName = NOT_AVAILABLE;
        OAuthAppDO oAuthAppDO = (OAuthAppDO) tokenReqCtx.getProperty(OAUTH_APP_DO_PROPERTY);
        if (oAuthAppDO != null && StringUtils.isNotBlank(oAuthAppDO.getApplicationName())) {
            spName = oAuthAppDO.getApplicationName();
        }

        // Tenant domain of the current thread (the accessing organisation).
        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();

        // Resolve the company UUID for Moesif from the current tenant domain.
        String companyId = NOT_AVAILABLE;
        try {
            String resolvedOrgId = OrganizationManagementConstants.SUPER_ORG_ID;
            if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                String rootTenantDomain =
                        OrganizationManagementUtil.getRootOrgTenantDomainBySubOrgTenantDomain(tenantDomain);
                resolvedOrgId = MoesifDataPublishDataHolder.getInstance()
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
            String resolvedUserId = authorizedUser.getUserId();
            if (StringUtils.isNotBlank(resolvedUserId)) {
                userId = resolvedUserId;
            }
        } catch (UserIdNotFoundException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not resolve user ID; using NOT_AVAILABLE.", e);
            }
        }

        Object[] metaData = MoesifDataPublishUtils.getMetaDataArray(
                companyId, ACTION_NAME_ORG_SWITCH, userId, NOT_AVAILABLE);

        Object[] payloadData = buildPayload(
                userResidentOrgId, accessingOrgId, spName, tenantDomain, errorCode);

        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                new org.wso2.carbon.databridge.commons.Event(
                        ORG_SWITCH_STREAM_NAME, System.currentTimeMillis(),
                        metaData, null, payloadData);

        MoesifDataPublishDataHolder.getInstance().getPublisherService().publish(databridgeEvent);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Published Moesif org-switch event for tenant: " + tenantDomain);
        }
    }

    private Object[] buildPayload(String userResidentOrgId, String accessingOrgId,
                                  String spName, String tenantDomain, String errorCode) {

        String publishingTime = Instant.now().toString();

        Object[] payload = new Object[6];
        payload[0] = StringUtils.defaultIfBlank(userResidentOrgId, NOT_AVAILABLE);
        payload[1] = StringUtils.defaultIfBlank(accessingOrgId, NOT_AVAILABLE);
        payload[2] = StringUtils.defaultIfBlank(spName, NOT_AVAILABLE);
        payload[3] = StringUtils.defaultIfBlank(tenantDomain, NOT_AVAILABLE);
        payload[4] = StringUtils.defaultIfBlank(errorCode, NOT_AVAILABLE);
        payload[5] = publishingTime;

        return payload;
    }

    private boolean isEnabled(Event event) throws IdentityEventException {

        if (this.configs.getModuleProperties() != null) {
            String handlerEnabled = this.configs.getModuleProperties()
                    .getProperty(ORG_SWITCH_PUBLISHER_ENABLED);
            if (Boolean.parseBoolean(handlerEnabled)) {
                String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
                return MoesifDataPublishUtils.isMoesifEnabledForPrimaryTenant(tenantDomain);
            }
        }
        return false;
    }
}
