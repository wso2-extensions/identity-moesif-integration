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

import org.apache.commons.collections.MapUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;

import java.util.Map;
import java.util.Optional;

import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_USER_REGISTRATION;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.POST_ADD_USER;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.REGISTRATION_STREAM_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_REGISTRATION_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_REGISTRATION_PUBLISHER_NAME;

/**
 * Event handler that publishes user registration events to Moesif via the HTTP output event adapter.
 */
public class MoesifRegistrationDataPublishHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(MoesifRegistrationDataPublishHandler.class);

    private static final String USER_ID_CLAIM_URI = "http://wso2.org/claims/userid";

    @Override
    public String getName() {

        return USER_REGISTRATION_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        MoesifHandlerUtils.PublishDecision decision = resolvePublishDecision();
        if (!decision.shouldPublish()) {
            return;
        }

        if (!POST_ADD_USER.equals(event.getEventName())) {
            return;
        }

        Map<String, Object> eventProperties = event.getEventProperties();
        String tenantDomain = (String) eventProperties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN);

        @SuppressWarnings("unchecked")
        Map<String, String> claims =
                (Map<String, String>) eventProperties.get(IdentityEventConstants.EventProperty.USER_CLAIMS);
        if (MapUtils.isEmpty(claims)) {
            return;
        }

        String userId = claims.get(USER_ID_CLAIM_URI);
        Object[] payload = MoesifHandlerUtils.buildMoesifRegistrationPayload(eventProperties, tenantDomain);
        Optional<String> userAgent = MoesifHandlerUtils.extractUserAgent(event);

        String rootTenantDomain = tenantDomain;
        String parentOrgId = OrganizationManagementConstants.SUPER_ORG_ID;
        try {
            if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                rootTenantDomain =
                        OrganizationManagementUtil.getRootOrgTenantDomainBySubOrgTenantDomain(tenantDomain);
                parentOrgId = MoesifHandlerDataHolder.getInstance()
                        .getOrganizationManager()
                        .resolveOrganizationId(rootTenantDomain);
            }
        } catch (OrganizationManagementException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not resolve organisation ID for tenant '" + tenantDomain
                        + "'; using NOT_AVAILABLE as company ID.", e);
            }
        }
        Object[] metadataArray = MoesifHandlerUtils.getMetaDataArray(parentOrgId,
                ACTION_NAME_USER_REGISTRATION, userId, userAgent.orElse(NOT_AVAILABLE), NOT_AVAILABLE,
                decision.isAnalyticsEnabled());

        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                new org.wso2.carbon.databridge.commons.Event(
                        REGISTRATION_STREAM_NAME, System.currentTimeMillis(),
                        metadataArray, null, payload);
        try {
            FrameworkUtils.startTenantFlow(rootTenantDomain);
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
        } finally {
            FrameworkUtils.endTenantFlow();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Published Moesif registration event for tenant: " + tenantDomain);
        }
    }

    private MoesifHandlerUtils.PublishDecision resolvePublishDecision() {

        if (this.configs.getModuleProperties() == null
                || !Boolean.parseBoolean(this.configs.getModuleProperties().getProperty(USER_REGISTRATION_PUBLISHER_ENABLED))) {
            return MoesifHandlerUtils.doNotPublish();
        }
        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        return MoesifHandlerUtils.resolvePublishDecision(tenantDomain,
                MoesifCommonConstants.MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY);
    }
}
