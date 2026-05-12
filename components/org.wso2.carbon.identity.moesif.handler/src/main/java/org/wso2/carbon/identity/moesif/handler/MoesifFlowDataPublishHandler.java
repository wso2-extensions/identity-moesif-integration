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
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_INVITED_USER_REGISTRATION_FLOW;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_PASSWORD_RECOVERY_FLOW;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_USER_REGISTRATION_FLOW;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_STREAM_NAME;

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

        if (!isEnabled()) {
            return;
        }

        if (IdentityEventConstants.Event.POST_FLOW_EXECUTION_STEP_EVENT.equals(event.getEventName())) {
            handleFlowStepEvent(event);
        } else {
            LOG.error("Event " + event.getEventName() + " cannot be handled");
        }
    }

    /**
     * Handle the POST_FLOW_EXECUTION_STEP_EVENT, which is fired after every step execution in the flow.
     * We publish a Moesif funnel event for each step, with properties resolved from the flow context.
     */
    private void handleFlowStepEvent(Event event) {

        Map<String, Object> properties = event.getEventProperties();

        publishFlowEventForNode(properties);
    }

    /**
     * Build and publish a single funnel event for the given node.
     */
    private void publishFlowEventForNode(Map<String, Object> properties) {


        String userId = resolveUserId((String) properties.get(IdentityEventConstants.EventProperty.CONTEXT_ID));
        String flowType = (String) properties.get(IdentityEventConstants.EventProperty.FLOW_TYPE);
        String tenantDomain = (String) properties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN);
        try {
            String orgId = MoesifHandlerDataHolder.getInstance().getOrganizationManager()
                    .resolveOrganizationId(properties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN).toString());
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
            String actionName;
            switch (Constants.FlowTypes.valueOf(flowType)) {
                case Constants.FlowTypes.REGISTRATION:
                    actionName = ACTION_NAME_USER_REGISTRATION_FLOW;
                    break;
                case Constants.FlowTypes.INVITED_USER_REGISTRATION:
                    actionName = ACTION_NAME_INVITED_USER_REGISTRATION_FLOW;
                    break;
                case Constants.FlowTypes.PASSWORD_RECOVERY:
                    actionName = ACTION_NAME_PASSWORD_RECOVERY_FLOW;
                    break;
                default:
                    return;
            }
            Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(properties, orgId, parentOrgId);

            try {
                FrameworkUtils.startTenantFlow(rootTenantDomain);
                publishFlowStepToMoesif(payload, parentOrgId, actionName, userId);
            } finally {
                FrameworkUtils.endTenantFlow();
            }
        } catch (OrganizationManagementException e) {
            LOG.error("Error while resolving organization information for tenant: " + tenantDomain, e);
        }
    }

    /**
     * Resolve the Moesif user_id from the flow user (username), falling back to the flow context identifier.
     */
    private String resolveUserId(String contextId) {

        return CTX + contextId;
    }

    /**
     * Publish a single registration funnel step event payload to the Moesif stream.
     */
    private void publishFlowStepToMoesif(Object[] payload, String orgUuid, String actionName, String userId) {

        try {

            Object[] metadataArray = MoesifHandlerUtils.getMetaDataArray(orgUuid, actionName, userId, null);
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

    private boolean isEnabled() {

        if (this.configs.getModuleProperties() != null) {
            String handlerEnabled = this.configs.getModuleProperties()
                    .getProperty(FLOW_PUBLISHER_ENABLED);
            if (Boolean.parseBoolean(handlerEnabled)) {
                String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
                return MoesifHandlerUtils.isHandlerEnabledForPrimaryTenant(tenantDomain, MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY);
            }
        }
        return false;
    }
}
