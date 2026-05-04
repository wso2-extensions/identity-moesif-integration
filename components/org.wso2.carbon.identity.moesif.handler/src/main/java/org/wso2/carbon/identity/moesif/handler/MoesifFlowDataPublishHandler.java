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
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.base.IdentityRuntimeException;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.flow.execution.engine.model.FlowExecutionContext;
import org.wso2.carbon.identity.flow.execution.engine.model.FlowExecutionStep;
import org.wso2.carbon.identity.flow.mgt.model.GraphConfig;
import org.wso2.carbon.identity.flow.mgt.model.NodeConfig;
import org.wso2.carbon.identity.flow.mgt.model.NodeEdge;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_FLOW_DEFAULT;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_PASSWORD_RECOVERY_FLOW;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_USER_REGISTRATION_FLOW;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_STREAM_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_TYPE_PASSWORD_RECOVERY;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.FLOW_TYPE_REGISTRATION;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.MOESIF_FLOW_STEP_ID_MAP_KEY;

/**
 * Event handler that publishes flow step events to Moesif via the HTTP output event adapter.
 * Publishes a Moesif funnel event for each step in registration and password-recovery flows.
 */
public class MoesifFlowDataPublishHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(MoesifFlowDataPublishHandler.class);
    private static final String ERROR = "_ERROR";
    private static final String STEP_1 = "STEP_1";
    private static final String STEP = "STEP_";
    private static final String BRANCH = "BRANCH_";
    private static final String CTX = "ctx_";

    @Override
    public String getName() {

        return FLOW_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        if (!isEnabled(event)) {
            return;
        }

        if (IdentityEventConstants.Event.POST_FLOW_EXECUTION_STEP_EVENT.equals(event.getEventName())) {
            handleFlowStepEvent(event);
        } else {
            LOG.error("Event " + event.getEventName() + " cannot be handled");
        }
    }

    private void handleFlowStepEvent(Event event) {

        Map<String, Object> properties = event.getEventProperties();
        FlowExecutionContext context = (FlowExecutionContext) properties.get(
                IdentityEventConstants.EventProperty.FLOW_CONTEXT);
        FlowExecutionStep step = (FlowExecutionStep) properties.get(
                IdentityEventConstants.EventProperty.FLOW_STEP);
        String errorCode = (String) properties.get(IdentityEventConstants.EventProperty.ERROR_CODE);

        if (context == null) {
            LOG.debug("FlowExecutionContext is null in flow step event. Skipping flow event publishing.");
            return;
        }

        String stepType = (step == null) ? null : step.getStepType();
        String userId = resolveUserId(context);

        if (org.wso2.carbon.identity.flow.mgt.Constants.NodeTypes.TASK_EXECUTION.equals(stepType)) {
            NodeConfig completedNode = context.getCurrentNode();
            if (completedNode == null) {
                LOG.debug("TASK_EXECUTION completion event received but context.getCurrentNode() is null. "
                        + "Skipping funnel event.");
                return;
            }
            publishFunnelEventForNode(context, completedNode, stepType, userId, errorCode);
            return;
        }

        NodeConfig nodeToPublish = context.getCurrentNode();
        if (nodeToPublish == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("View event received but context.getCurrentNode() is null for flow: "
                        + context.getContextIdentifier());
            }
            return;
        }
        publishFunnelEventForNode(context, nodeToPublish, stepType, userId, errorCode);
    }

    private void publishFunnelEventForNode(FlowExecutionContext context, NodeConfig node,
                                            String stepType, String userId, String errorCode) {

        String flowType = context.getFlowType();
        String flowStepId = resolveFlowStepId(context, node, errorCode);
        String actionName = FLOW_TYPE_REGISTRATION.equals(flowType) ? ACTION_NAME_USER_REGISTRATION_FLOW :
                FLOW_TYPE_PASSWORD_RECOVERY.equals(flowType) ? ACTION_NAME_PASSWORD_RECOVERY_FLOW :
                        flowType + ACTION_NAME_FLOW_DEFAULT;
        Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(flowType, stepType, flowStepId,
                node.getType(), context.getTenantDomain(), context.getContextIdentifier(), errorCode);

        publishFlowStepToMoesif(payload, context.getTenantDomain(), actionName, userId);
    }

    private String resolveFlowStepId(FlowExecutionContext context, NodeConfig node, String errorCode) {

        if (node == null) {
            return STEP_1;
        }
        GraphConfig graph = context.getGraphConfig();
        if (graph == null) {
            return STEP_1;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> flowStepIdMap =
                (Map<String, String>) context.getProperty(MOESIF_FLOW_STEP_ID_MAP_KEY);
        if (flowStepIdMap == null) {
            flowStepIdMap = buildFlowStepIdMap(graph);
            context.setProperty(MOESIF_FLOW_STEP_ID_MAP_KEY, flowStepIdMap);
        }
        boolean isError = StringUtils.isNotBlank(errorCode) || context.getCurrentNodeResponse() != null
                && context.getCurrentNodeResponse().getError() != null;
        String label = flowStepIdMap.getOrDefault(node.getId(), STEP_1) + "_" +
                context.getCurrentNode().getType() + "_" + context.getCurrentNodeResponse().getStatus();
        if (isError) {
            label += ERROR;
        }
        return label;
    }

    /**
     * Build a map of nodeId → full step label (e.g. "STEP_1", "STEP_2_BRANCH_1") for all nodes in the graph.
     * The step number is derived from the longest path from the start node to the given node.
     * Branch labels are assigned based on the outgoing edges at branch points.
     * The algorithm is a forward BFS starting from the first node.
     */
    private Map<String, String> buildFlowStepIdMap(GraphConfig graph) {

        Map<String, NodeConfig> allNodes = graph.getNodeConfigs();
        String startId = graph.getFirstNodeId();
        if (startId == null || !allNodes.containsKey(startId)) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> successors = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (NodeConfig n : allNodes.values()) {
            successors.put(n.getId(), new ArrayList<>());
            inDegree.put(n.getId(), 0);
        }
        for (NodeConfig n : allNodes.values()) {
            List<String> succs = successors.get(n.getId());
            Set<String> seen = new HashSet<>();
            if (n.getNextNodeId() != null && allNodes.containsKey(n.getNextNodeId())) {
                seen.add(n.getNextNodeId());
                succs.add(n.getNextNodeId());
            }
            if (n.getEdges() != null) {
                for (NodeEdge e : n.getEdges()) {
                    if (e.getTargetNodeId() != null && allNodes.containsKey(e.getTargetNodeId())
                            && seen.add(e.getTargetNodeId())) {
                        succs.add(e.getTargetNodeId());
                    }
                }
            }
            for (String s : succs) {
                inDegree.merge(s, 1, Integer::sum);
            }
        }

        Map<String, Integer> maxPredStep = new HashMap<>();
        Map<String, Set<String>> branchContribs = new HashMap<>();
        Map<String, Integer> remaining = new HashMap<>(inDegree);

        maxPredStep.put(startId, 0);
        branchContribs.put(startId, new HashSet<>(Collections.singleton(null)));
        remaining.put(startId, 0);

        Queue<String> queue = new LinkedList<>();
        queue.offer(startId);

        Map<String, String> result = new HashMap<>();

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            NodeConfig node = allNodes.get(nodeId);
            if (node == null) {
                continue;
            }

            int predStep = maxPredStep.getOrDefault(nodeId, 0);
            int step = predStep + 1;

            Set<String> contribs = branchContribs.getOrDefault(nodeId, Collections.emptySet());
            Set<String> distinctNonNull = new HashSet<>();
            boolean trunkPresent = false;
            for (String c : contribs) {
                if (c == null) {
                    trunkPresent = true;
                } else {
                    distinctNonNull.add(c);
                }
            }

            String branch;
            if (distinctNonNull.size() > 1 || (trunkPresent && !distinctNonNull.isEmpty())) {
                branch = null;
            } else if (distinctNonNull.size() == 1) {
                branch = distinctNonNull.iterator().next();
            } else {
                branch = null;
            }

            result.put(nodeId, branch == null ? STEP + step : STEP + step + "_" + branch);

            List<String> succs = successors.getOrDefault(nodeId, Collections.emptyList());
            for (int i = 0; i < succs.size(); i++) {
                String succId = succs.get(i);

                String branchToPass;
                if (succs.size() > 1) {
                    String suffix = BRANCH + (i + 1);
                    branchToPass = (branch != null) ? branch + "_" + suffix : suffix;
                } else {
                    branchToPass = branch;
                }

                maxPredStep.merge(succId, step, Math::max);
                branchContribs.computeIfAbsent(succId, k -> new HashSet<>()).add(branchToPass);

                if (remaining.merge(succId, -1, Integer::sum) == 0) {
                    queue.offer(succId);
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Built Moesif flow step id map for flow graph '" + graph.getId() + "': " + result);
        }
        return result;
    }

    private String resolveUserId(FlowExecutionContext context) {

        return CTX + context.getContextIdentifier();
    }

    private void publishFlowStepToMoesif(Object[] payload, String tenantDomain, String actionName, String userId) {

        try {
            String orgUuid = MoesifHandlerDataHolder.getInstance().getOrganizationManager()
                    .resolveOrganizationId(tenantDomain);
            Object[] metadataArray = MoesifHandlerUtils.getMetaDataArray(orgUuid, actionName, userId, null);
            org.wso2.carbon.databridge.commons.Event databridgeEvent =
                    new org.wso2.carbon.databridge.commons.Event(
                            FLOW_STREAM_NAME, System.currentTimeMillis(),
                            metadataArray, null, payload);
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Published Moesif flow step event for tenant: " + tenantDomain);
            }
        } catch (IdentityRuntimeException e) {
            LOG.error("Error while publishing Moesif flow step event for tenant: " + tenantDomain, e);
        } catch (OrganizationManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isEnabled(Event event) {

        if (this.configs.getModuleProperties() != null) {
            String handlerEnabled = this.configs.getModuleProperties().getProperty(FLOW_PUBLISHER_ENABLED);
            if (Boolean.parseBoolean(handlerEnabled)) {
                String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
                return MoesifHandlerUtils.isMoesifEnabledForPrimaryTenant(tenantDomain);
            }
        }
        return false;
    }
}
