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
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.base.IdentityRuntimeException;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.flow.mgt.Constants;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;

import java.util.Map;

import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY;
import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_INVITED_USER_REGISTRATION_FLOW;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_PASSWORD_RECOVERY_FLOW;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_USER_REGISTRATION_FLOW;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_STREAM_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.USER_LINK_STREAM_NAME;

/**
 * Event handler that publishes authentication login events to Moesif via the HTTP output event adapter.
 * The payload is pre-formatted to the Moesif action API format.
 */
public class MoesifFlowDataPublishHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(MoesifFlowDataPublishHandler.class);
    private static final String CTX = "ctx_";

    @Override
    public String getName() {

        return FLOW_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) {

        MoesifHandlerUtils.PublishDecision decision = resolvePublishDecision();
        if (!decision.shouldPublish()) {
            return;
        }

        if (IdentityEventConstants.Event.POST_FLOW_EXECUTION_STEP_EVENT.equals(event.getEventName())) {
            handleFlowStepEvent(event, decision.isAnalyticsEnabled());
        } else {
            LOG.error("Event " + event.getEventName() + " cannot be handled");
        }
    }

    /**
     * Handle the POST_FLOW_EXECUTION_STEP_EVENT, which is fired after every step execution in the flow.
     * We publish a Moesif funnel event for each step, with properties resolved from the flow context.
     */
    private void handleFlowStepEvent(Event event, boolean analyticsEnabled) {

        Map<String, Object> properties = event.getEventProperties();

        publishFlowEventForNode(properties, analyticsEnabled);
    }

    /**
     * Build and publish a single funnel event for the given node.
     */
    private void publishFlowEventForNode(Map<String, Object> properties, boolean analyticsEnabled) {

        if (properties == null || properties.isEmpty()) {
            LOG.warn("Event properties are null or empty; skipping Moesif flow step event.");
            return;
        }

        String anonymousId = buildAnonymousId(
                (String) properties.get(IdentityEventConstants.EventProperty.CONTEXT_ID));
        String userId = (String) properties.get(IdentityEventConstants.EventProperty.USER_ID);
        String flowType = (String) properties.get(IdentityEventConstants.EventProperty.FLOW_TYPE);
        String tenantDomain = (String) properties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN);
        if (StringUtils.isBlank(tenantDomain)) {
            LOG.warn("Tenant domain is blank; skipping Moesif flow step event.");
            return;
        }
        String actionName = resolveActionName(flowType);
        if (actionName == null) {
            return;
        }
        try {
            String orgId = MoesifHandlerDataHolder.getInstance().getOrganizationManager()
                    .resolveOrganizationId(tenantDomain);
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
                    LOG.debug("Could not resolve organization ID for tenant '" + tenantDomain
                            + "'; using NOT_AVAILABLE as company ID.", e);
                }
            }
            Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(properties, orgId, parentOrgId);

            try {
                FrameworkUtils.startTenantFlow(rootTenantDomain);
                publishFlowStepToMoesif(payload, parentOrgId, actionName, anonymousId, analyticsEnabled);
                if (isFlowComplete(properties) && StringUtils.isNotBlank(userId)) {
                    publishUserLinkToMoesif(userId, anonymousId, parentOrgId, analyticsEnabled);
                }
            } finally {
                FrameworkUtils.endTenantFlow();
            }
        } catch (OrganizationManagementException e) {
            LOG.error("Error while resolving organization information for tenant: " + tenantDomain, e);
        }
    }

    /**
     * Resolve the Moesif action name for the given flow type. Returns {@code null} when the flow
     * type is missing, unknown, or not one of the flow types published to Moesif, so the caller
     * can skip the event.
     */
    private String resolveActionName(String flowType) {

        if (StringUtils.isBlank(flowType)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Flow type is blank; skipping Moesif flow step event.");
            }
            return null;
        }
        try {
            switch (Constants.FlowTypes.valueOf(flowType)) {
                case Constants.FlowTypes.REGISTRATION:
                    return ACTION_NAME_USER_REGISTRATION_FLOW;
                case Constants.FlowTypes.INVITED_USER_REGISTRATION:
                    return ACTION_NAME_INVITED_USER_REGISTRATION_FLOW;
                case Constants.FlowTypes.PASSWORD_RECOVERY:
                    return ACTION_NAME_PASSWORD_RECOVERY_FLOW;
                default:
                    return null;
            }
        } catch (IllegalArgumentException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unknown flow type '" + flowType + "'; skipping Moesif flow step event.");
            }
            return null;
        }
    }

    /**
     * Build the anonymous Moesif identifier from the flow context identifier.
     */
    private String buildAnonymousId(String contextId) {

        return StringUtils.isBlank(contextId) ? NOT_AVAILABLE : CTX + contextId;
    }

    /**
     * Returns {@code true} when the step event signals that the flow has run to completion,
     * i.e. the END node of the flow graph has executed with a COMPLETE response status.
     */
    private boolean isFlowComplete(Map<String, Object> properties) {

        return Constants.END_NODE_ID.equals(properties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_ID))
                && Constants.COMPLETE.equals(
                        properties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_RESPONSE_STATUS));
    }

    /**
     * Publish a single registration funnel step event payload to the Moesif stream.
     */
    private void publishFlowStepToMoesif(Object[] payload, String orgUuid, String actionName,
                                         String anonymousId, boolean analyticsEnabled) {

        try {

            Object[] metadataArray = MoesifHandlerUtils.getMetaDataArray(orgUuid, actionName, anonymousId,
                    null, NOT_AVAILABLE, analyticsEnabled);
            org.wso2.carbon.databridge.commons.Event databridgeEvent =
                    new org.wso2.carbon.databridge.commons.Event(
                            FLOW_STREAM_NAME, System.currentTimeMillis(),
                            metadataArray, null, payload);
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Published Moesif registration funnel step event for tenant: " + orgUuid);
            }
        } catch (IdentityRuntimeException e) {
            LOG.error("Error while publishing Moesif flow step event for tenant: " + orgUuid, e);
        }
    }

    /**
     * Publish a user-link event to the Moesif Users API once the flow has completed and the actual
     * user ID is known, linking the anonymous (flow context) identifier with the actual user.
     */
    private void publishUserLinkToMoesif(String userId, String anonymousId, String orgUuid,
                                         boolean analyticsEnabled) {

        try {
            Object[] metadataArray = MoesifHandlerUtils.getUserLinkMetaDataArray(userId, anonymousId, analyticsEnabled);
            org.wso2.carbon.databridge.commons.Event databridgeEvent =
                    new org.wso2.carbon.databridge.commons.Event(
                            USER_LINK_STREAM_NAME, System.currentTimeMillis(),
                            metadataArray, null, null);
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Published Moesif user-link event for tenant: " + orgUuid);
            }
        } catch (IdentityRuntimeException e) {
            LOG.error("Error while publishing Moesif user-link event for tenant: " + orgUuid, e);
        }
    }

    private MoesifHandlerUtils.PublishDecision resolvePublishDecision() {

        if (this.configs.getModuleProperties() == null
                || !Boolean.parseBoolean(this.configs.getModuleProperties().getProperty(FLOW_PUBLISHER_ENABLED))) {
            return MoesifHandlerUtils.doNotPublish();
        }
        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        return MoesifHandlerUtils.resolvePublishDecision(tenantDomain,
                MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY);
    }
}
