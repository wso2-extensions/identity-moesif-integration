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
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.login.AnalyticsLoginDataPublishHandlerV110;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.login.AnalyticsLoginDataPublisherUtils;
import org.wso2.carbon.identity.data.publisher.authentication.analytics.login.model.AuthenticationData;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;

import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;

import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.AUTHENTICATOR;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.IDENTIFIER_EXECUTOR;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TENANT_DOMAIN_NAMES;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_AUTHENTICATION_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_AUTHENTICATION_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_AUTHENTICATION_STREAM_NAME;
import static org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils.extractUserAgent;


/**
 * Event handler that publishes authentication login events to Moesif via the HTTP output event adapter.
 * The payload is pre-formatted to the Moesif action API format.
 */
public class MoesifUserAuthenticationDataPublishHandler extends AnalyticsLoginDataPublishHandlerV110 {

    private static final Log LOG = LogFactory.getLog(MoesifUserAuthenticationDataPublishHandler.class);

    @Override
    public String getName() {

        return USER_AUTHENTICATION_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) {

        MoesifHandlerUtils.PublishDecision decision = resolvePublishDecision();
        if (!decision.shouldPublish()) {
            return;
        }
        boolean analyticsEnabled = decision.isAnalyticsEnabled();

        if (IdentityEventConstants.EventName.AUTHENTICATION_STEP_SUCCESS.name().equals(event.getEventName()) ||
                IdentityEventConstants.EventName.AUTHENTICATION_STEP_FAILURE.name().equals(event.getEventName())) {
            if (isIdentifierFirstStep(event)) {
                // The user is not resolved at the identifier first step, hence such steps are not published.
                return;
            }
            AuthenticationData authenticationData =
                    AnalyticsLoginDataPublisherUtils.buildAuthnDataForAuthnStepV110(event, true);
            publishToMoesif(authenticationData, event, analyticsEnabled);
        } else if (IdentityEventConstants.EventName.AUTHENTICATION_SUCCESS.name().equals(event.getEventName()) ||
                IdentityEventConstants.EventName.AUTHENTICATION_FAILURE.name().equals(event.getEventName())) {
            AuthenticationData authenticationData = AnalyticsLoginDataPublisherUtils
                    .buildAuthnDataForAuthenticationV110(event, true);
            publishToMoesif(authenticationData, event, analyticsEnabled);
        } else {
            LOG.error("Event " + event.getEventName() + " cannot be handled");
        }
    }

    /**
     * Checks whether the given authentication step event was fired by the identifier first authenticator.
     * Such steps only resolve the user identifier, hence they carry no authenticated user to publish.
     *
     * @param event Authentication step event.
     * @return true if the step belongs to the identifier first authenticator, false otherwise.
     */
    private boolean isIdentifierFirstStep(Event event) {

        Map<String, Object> eventProperties = event.getEventProperties();
        if (eventProperties == null) {
            return false;
        }
        Object params = eventProperties.get(IdentityEventConstants.EventProperty.PARAMS);
        if (!(params instanceof Map)) {
            return false;
        }
        return IDENTIFIER_EXECUTOR.equals(((Map<?, ?>) params).get(AUTHENTICATOR));
    }

    @SuppressWarnings("unchecked")
    private void publishToMoesif(AuthenticationData authenticationData, Event event, boolean analyticsEnabled) {

        try {
            Object[] payloadData = populatePayloadData(authenticationData, true);
            Optional<String> userAgent = extractUserAgent(event);
            // Authentication events carry the remote IP on AuthenticationData itself; fall back to
            // IdentityUtil.getClientIpAddress on the inbound HTTP request when the AuthenticationData
            // field isn't populated (e.g. step events fired outside a request scope).
            String ipAddress = authenticationData.getRemoteIp() != null ? authenticationData.getRemoteIp() :
                    MoesifCommonConstants.NOT_AVAILABLE;

            String[] publishingDomains = (String[]) authenticationData.getParameter(TENANT_DOMAIN_NAMES);
            if (publishingDomains != null) {
                for (String publishingDomain : publishingDomains) {
                    try {
                        FrameworkUtils.startTenantFlow(publishingDomain);
                        String orgUuid = MoesifHandlerDataHolder.getInstance().getOrganizationManager()
                                .resolveOrganizationId(publishingDomain);
                        Object[] metadataArray = MoesifHandlerUtils.getMetaDataArray(orgUuid,
                                MoesifHandlerConstants.ACTION_NAME_USER_AUTHENTICATION, authenticationData.getUserId(),
                                userAgent.orElse(MoesifCommonConstants.NOT_AVAILABLE), ipAddress, analyticsEnabled);

                        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                                new org.wso2.carbon.databridge.commons.Event(
                                        USER_AUTHENTICATION_STREAM_NAME, System.currentTimeMillis(), metadataArray, null, payloadData);
                        MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Published Moesif login event for domain: " + publishingDomain);
                        }
                    } catch (OrganizationManagementException e) {
                        LOG.error("Error while resolving organization ID for tenant domain: " + publishingDomain, e);
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

    private MoesifHandlerUtils.PublishDecision resolvePublishDecision() {

        if (this.configs.getModuleProperties() == null
                || !Boolean.parseBoolean(this.configs.getModuleProperties().getProperty(USER_AUTHENTICATION_PUBLISHER_ENABLED))) {
            return MoesifHandlerUtils.doNotPublish();
        }
        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        return MoesifHandlerUtils.resolvePublishDecision(tenantDomain,
                MoesifCommonConstants.MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY);
    }
}
