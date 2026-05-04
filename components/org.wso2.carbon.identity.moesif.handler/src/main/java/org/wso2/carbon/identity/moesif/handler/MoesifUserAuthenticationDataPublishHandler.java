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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.base.IdentityRuntimeException;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.login.AnalyticsLoginDataPublishHandlerV110;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.login.AnalyticsLoginDataPublisherUtils;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.login.model.AuthenticationData;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;

import java.util.Optional;

import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_USER_AUTHENTICATION;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TENANT_DOMAIN_NAMES;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_AUTHENTICATION_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_AUTHENTICATION_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_AUTHENTICATION_STREAM_NAME;

/**
 * Event handler that publishes authentication login events to Moesif via the HTTP output event adapter.
 */
public class MoesifUserAuthenticationDataPublishHandler extends AnalyticsLoginDataPublishHandlerV110 {

    private static final Log LOG = LogFactory.getLog(MoesifUserAuthenticationDataPublishHandler.class);

    @Override
    public String getName() {

        return USER_AUTHENTICATION_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        if (!isEnabled(event)) {
            return;
        }

        if (IdentityEventConstants.EventName.AUTHENTICATION_STEP_SUCCESS.name().equals(event.getEventName()) ||
                IdentityEventConstants.EventName.AUTHENTICATION_STEP_FAILURE.name().equals(event.getEventName())) {
            AuthenticationData authenticationData =
                    AnalyticsLoginDataPublisherUtils.buildAuthnDataForAuthnStepV110(event, true);
            publishToMoesif(authenticationData, event);
        } else if (IdentityEventConstants.EventName.AUTHENTICATION_SUCCESS.name().equals(event.getEventName()) ||
                IdentityEventConstants.EventName.AUTHENTICATION_FAILURE.name().equals(event.getEventName())) {
            AuthenticationData authenticationData =
                    AnalyticsLoginDataPublisherUtils.buildAuthnDataForAuthenticationV110(event, true);
            publishToMoesif(authenticationData, event);
        } else {
            LOG.error("Event " + event.getEventName() + " cannot be handled");
        }
    }

    @SuppressWarnings("unchecked")
    private void publishToMoesif(AuthenticationData authenticationData, Event event) {

        try {
            Object[] payloadData = populatePayloadData(authenticationData);
            Optional<String> userAgent = MoesifHandlerUtils.extractUserAgent(event);

            String[] publishingDomains = (String[]) authenticationData.getParameter(TENANT_DOMAIN_NAMES);
            if (publishingDomains != null) {
                for (String publishingDomain : publishingDomains) {
                    try {
                        FrameworkUtils.startTenantFlow(publishingDomain);
                        String orgUuid = MoesifHandlerDataHolder.getInstance().getOrganizationManager()
                                .resolveOrganizationId(publishingDomain);
                        Object[] metadataArray = MoesifHandlerUtils.getMetaDataArray(orgUuid,
                                ACTION_NAME_USER_AUTHENTICATION, authenticationData.getUserId(),
                                userAgent.orElse(NOT_AVAILABLE));

                        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                                new org.wso2.carbon.databridge.commons.Event(
                                        USER_AUTHENTICATION_STREAM_NAME, System.currentTimeMillis(),
                                        metadataArray, null, payloadData);
                        MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Published Moesif login event for domain: " + publishingDomain);
                        }
                    } catch (OrganizationManagementException e) {
                        throw new RuntimeException(e);
                    } finally {
                        FrameworkUtils.endTenantFlow();
                    }
                }
            }
        } catch (IdentityRuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Error while publishing Moesif authentication data", e);
            }
        }
    }

    private boolean isEnabled(Event event) throws IdentityEventException {

        if (this.configs.getModuleProperties() != null) {
            String handlerEnabled = this.configs.getModuleProperties()
                    .getProperty(USER_AUTHENTICATION_PUBLISHER_ENABLED);
            if (Boolean.parseBoolean(handlerEnabled)) {
                String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
                return MoesifHandlerUtils.isMoesifEnabledForPrimaryTenant(tenantDomain);
            }
        }
        return false;
    }
}
