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
import java.util.Arrays;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.UserIdNotFoundException;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.session.AnalyticsSessionDataPublishHandler;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.session.SessionDataPublisherConstants;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.session.SessionDataPublisherUtil;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.session.model.SessionData;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;

import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_USER_SESSION;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_SESSION_PUBLISHER_ENABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_SESSION_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_SESSION_STREAM_NAME;


/**
 * Event handler that publishes session lifecycle events (create, update, terminate) to Moesif
 * via the HTTP output event adapter.
 *
 * <p>Payload is built using {@link SessionDataPublisherUtil} — the same payload structure that the
 * upstream analytics session publisher uses — ensuring both destinations receive identical data.
 * The tenant flow is started with the root/parent organisation tenant domain so that the Moesif
 * event publisher deployed there is selected.</p>
 */
public class MoesifSessionDataPublisherHandler extends AnalyticsSessionDataPublishHandler {

    private static final Log LOG = LogFactory.getLog(MoesifSessionDataPublisherHandler.class);

    @Override
    public String getName() {

        return USER_SESSION_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) {

        if (!isEnabled()) {
            return;
        }

        String eventName = event.getEventName();
        int actionId;
        if (IdentityEventConstants.EventName.SESSION_CREATE.name().equals(eventName)) {
            actionId = SessionDataPublisherConstants.SESSION_CREATION_STATUS;
        } else if (IdentityEventConstants.EventName.SESSION_TERMINATE.name().equals(eventName)) {
            actionId = SessionDataPublisherConstants.SESSION_TERMINATION_STATUS;
        } else if (IdentityEventConstants.EventName.SESSION_UPDATE.name().equals(eventName)) {
            actionId = SessionDataPublisherConstants.SESSION_UPDATE_STATUS;
        } else {
            LOG.error(String.format("Event %s cannot be handled by %s", eventName, getName()));
            return;
        }

        SessionData sessionData = SessionDataPublisherUtil.buildSessionData(event);
        AuthenticationContext authenticationContext = (AuthenticationContext) event.getEventProperties().get("context");
        SessionDataPublisherUtil.updateTimeStamps(sessionData, actionId);

        Object[] payloadData;
        if (SessionDataPublisherUtil.isPublishingSessionCountEnabled()) {
            payloadData = SessionDataPublisherUtil.buildSessionPayloadWithSessionCount(sessionData, actionId, true);
        } else {
            Object[] basePayload = SessionDataPublisherUtil.buildSessionPayload(sessionData, actionId, true);
            payloadData = Arrays.copyOf(basePayload, basePayload.length + 1);
            payloadData[basePayload.length] = NOT_AVAILABLE;
        }

        publishToMoesif(event, sessionData, authenticationContext, payloadData);
    }

    /**
     * Publishes the pre-built session payload to Moesif.
     * Starts a tenant flow with the root/parent organisation tenant domain — unlike the standard
     * analytics publisher which starts from the super-tenant.
     */
    private void publishToMoesif(Event event, SessionData sessionData,
                                 AuthenticationContext authenticationContext, Object[] payloadData) {

        String tenantDomain = sessionData.getTenantDomain();
        if (StringUtils.isBlank(tenantDomain)) {
            LOG.warn("Tenant domain is blank; skipping Moesif session event.");
            return;
        }

        // Resolve the root/parent org tenant domain for the tenant flow.
        String rootTenantDomain = tenantDomain;
        if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
            try {
                rootTenantDomain =
                        OrganizationManagementUtil.getRootOrgTenantDomainBySubOrgTenantDomain(tenantDomain);
            } catch (OrganizationManagementException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "Could not resolve root tenant domain for '%s'; using original.", tenantDomain), e);
                }
            }
        }

        // Resolve the Moesif company/org UUID from the root tenant domain.
        String orgUuid = NOT_AVAILABLE;
        try {
            String resolved = MoesifHandlerDataHolder.getInstance()
                    .getOrganizationManager()
                    .resolveOrganizationId(rootTenantDomain);
            if (StringUtils.isNotBlank(resolved)) {
                orgUuid = resolved;
            }
        } catch (OrganizationManagementException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format(
                        "Could not resolve organisation ID for tenant '%s'; using NOT_AVAILABLE.", rootTenantDomain), e);
            }
        }

        String userId;
        try {
            AuthenticatedUser user = authenticationContext.getSubject();
            if (user == null) {
                user = authenticationContext.getLastAuthenticatedUser();
            }
            if (user != null) {
                userId = StringUtils.defaultIfBlank(user.getUserId(), MoesifCommonConstants.NOT_AVAILABLE);
            } else {
                userId = NOT_AVAILABLE;
            }
        } catch (UserIdNotFoundException e) {
            userId = NOT_AVAILABLE;
        }
        String userAgent = StringUtils.defaultIfBlank(sessionData.getUserAgent(), NOT_AVAILABLE);

        String ipAddress = sessionData.getRemoteIP() != null ? sessionData.getRemoteIP() : NOT_AVAILABLE;

        Object[] metaData = MoesifHandlerUtils.getMetaDataArray(orgUuid, ACTION_NAME_USER_SESSION, userId, userAgent,
                ipAddress);

        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                new org.wso2.carbon.databridge.commons.Event(
                        USER_SESSION_STREAM_NAME, System.currentTimeMillis(), metaData, null, payloadData);

        try {
            FrameworkUtils.startTenantFlow(rootTenantDomain);
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
        } finally {
            FrameworkUtils.endTenantFlow();
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Published Moesif session event for tenant: %s", tenantDomain));
        }
    }

    private boolean isEnabled() {

        if (this.configs.getModuleProperties() != null) {
            String handlerEnabled = this.configs.getModuleProperties()
                    .getProperty(USER_SESSION_PUBLISHER_ENABLE);
            if (Boolean.parseBoolean(handlerEnabled)) {
                String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
                return MoesifHandlerUtils.isHandlerEnabledForPrimaryTenant(tenantDomain,
                        MoesifCommonConstants.MOESIF_SESSION_PUBLISHER_ENABLED_PROPERTY);
            }
        }
        return false;
    }
}
