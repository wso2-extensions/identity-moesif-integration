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

import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.event.bean.ModuleConfiguration;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.flow.mgt.model.GraphConfig;
import org.wso2.carbon.identity.flow.mgt.model.NodeConfig;
import org.wso2.carbon.identity.flow.mgt.model.NodeEdge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link MoesifFlowDataPublishHandler}.
 *
 * <p>The core BFS algorithm ({@code buildFlowStepIdMap}) is private, so it is exercised
 * via reflection to keep the tests decoupled from the OSGi runtime while still giving
 * meaningful coverage of the step-labelling logic.
 */
public class MoesifFlowDataPublishHandlerTest {

    private MoesifFlowDataPublishHandler handler;
    private Method buildFlowStepIdMap;

    @BeforeClass
    public void setUp() throws Exception {

        handler = new MoesifFlowDataPublishHandler();
        buildFlowStepIdMap = MoesifFlowDataPublishHandler.class
                .getDeclaredMethod("buildFlowStepIdMap", GraphConfig.class);
        buildFlowStepIdMap.setAccessible(true);
    }

    // ---- getName ---------------------------------------------------------------

    @Test
    public void testGetName() {

        assertEquals(handler.getName(), "moesifFlowPublisher");
    }

    // ---- handleEvent — disabled path ------------------------------------------

    /**
     * When the handler is disabled (module property is {@code "false"}), {@code handleEvent}
     * must return immediately without publishing or throwing any exception.
     */
    @Test
    public void testHandleEventSkipsWhenHandlerDisabled() throws Exception {

        MoesifFlowDataPublishHandler disabledHandler = new MoesifFlowDataPublishHandler();
        injectDisabledConfig(disabledHandler);

        Event event = new Event("POST_FLOW_EXECUTION_STEP_EVENT", new HashMap<>());
        disabledHandler.handleEvent(event);
    }

    /**
     * When the handler is disabled, any unrecognised event name must also be silently ignored.
     */
    @Test
    public void testHandleEventSkipsUnknownEventWhenDisabled() throws Exception {

        MoesifFlowDataPublishHandler disabledHandler = new MoesifFlowDataPublishHandler();
        injectDisabledConfig(disabledHandler);

        Event event = new Event("UNKNOWN_EVENT", new HashMap<>());
        disabledHandler.handleEvent(event);
    }

    // ---- buildFlowStepIdMap — linear graph (A → B → C) -------------------------

    /**
     * A linear graph: start → middle → end.
     * Expected labels: start=STEP_1, middle=STEP_2, end=STEP_3.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildFlowStepIdMapLinear() throws Exception {

        NodeConfig nodeA = mockNode("A", "B", null);
        NodeConfig nodeB = mockNode("B", "C", null);
        NodeConfig nodeC = mockNode("C", null, null);

        GraphConfig graph = mockGraph("A", nodeA, nodeB, nodeC);

        Map<String, String> result = (Map<String, String>) buildFlowStepIdMap.invoke(handler, graph);

        assertNotNull(result);
        assertEquals(result.get("A"), "STEP_1");
        assertEquals(result.get("B"), "STEP_2");
        assertEquals(result.get("C"), "STEP_3");
    }

    /**
     * A single-node graph: just the start node.
     * Expected: start=STEP_1.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildFlowStepIdMapSingleNode() throws Exception {

        NodeConfig nodeA = mockNode("A", null, null);
        GraphConfig graph = mockGraph("A", nodeA);

        Map<String, String> result = (Map<String, String>) buildFlowStepIdMap.invoke(handler, graph);

        assertNotNull(result);
        assertEquals(result.get("A"), "STEP_1");
    }

    /**
     * A fork-join graph:
     * <pre>
     *   A → B (branch 1)
     *   A → C (branch 2)
     *   B → D
     *   C → D
     * </pre>
     * Expected:
     * <ul>
     *   <li>A = STEP_1</li>
     *   <li>B = STEP_2_BRANCH_1</li>
     *   <li>C = STEP_2_BRANCH_2</li>
     *   <li>D = STEP_3  (converged — no branch suffix)</li>
     * </ul>
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildFlowStepIdMapForkJoin() throws Exception {

        // A branches to B and C via edges (no single nextNodeId)
        NodeConfig nodeA = mockNodeWithEdges("A", null, "B", "C");
        NodeConfig nodeB = mockNode("B", "D", null);
        NodeConfig nodeC = mockNode("C", "D", null);
        NodeConfig nodeD = mockNode("D", null, null);

        GraphConfig graph = mockGraph("A", nodeA, nodeB, nodeC, nodeD);

        Map<String, String> result = (Map<String, String>) buildFlowStepIdMap.invoke(handler, graph);

        assertNotNull(result);
        assertEquals(result.get("A"), "STEP_1");
        assertEquals(result.get("B"), "STEP_2_BRANCH_1");
        assertEquals(result.get("C"), "STEP_2_BRANCH_2");
        assertEquals(result.get("D"), "STEP_3");
    }

    /**
     * A two-level branching graph:
     * <pre>
     *   A → B → D
     *   A → C → D
     *   D → E
     * </pre>
     * All nodes should exist in the result map.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildFlowStepIdMapAllNodesLabelled() throws Exception {

        NodeConfig nodeA = mockNodeWithEdges("A", null, "B", "C");
        NodeConfig nodeB = mockNode("B", "D", null);
        NodeConfig nodeC = mockNode("C", "D", null);
        NodeConfig nodeD = mockNode("D", "E", null);
        NodeConfig nodeE = mockNode("E", null, null);

        GraphConfig graph = mockGraph("A", nodeA, nodeB, nodeC, nodeD, nodeE);

        Map<String, String> result = (Map<String, String>) buildFlowStepIdMap.invoke(handler, graph);

        assertNotNull(result);
        // All 5 nodes must be labelled
        assertEquals(result.size(), 5);
        assertTrue(result.containsKey("A"));
        assertTrue(result.containsKey("B"));
        assertTrue(result.containsKey("C"));
        assertTrue(result.containsKey("D"));
        assertTrue(result.containsKey("E"));
        // Start node always STEP_1
        assertEquals(result.get("A"), "STEP_1");
    }

    /**
     * Graph with unknown start node — should return an empty map rather than throw.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildFlowStepIdMapUnknownStartNode() throws Exception {

        NodeConfig nodeA = mockNode("A", null, null);
        GraphConfig graph = mockGraph("UNKNOWN", nodeA);

        Map<String, String> result = (Map<String, String>) buildFlowStepIdMap.invoke(handler, graph);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * Build a mock {@link NodeConfig} with a single successor via {@code nextNodeId}.
     */
    private NodeConfig mockNode(String id, String nextNodeId, String type) {

        NodeConfig node = Mockito.mock(NodeConfig.class);
        when(node.getId()).thenReturn(id);
        when(node.getNextNodeId()).thenReturn(nextNodeId);
        when(node.getEdges()).thenReturn(null);
        when(node.getType()).thenReturn(type != null ? type : "PROMPT_NODE");
        return node;
    }

    /**
     * Build a mock {@link NodeConfig} that branches to multiple successors via {@link NodeEdge}s.
     */
    private NodeConfig mockNodeWithEdges(String id, String nextNodeId, String... edgeTargets) {

        NodeConfig node = Mockito.mock(NodeConfig.class);
        when(node.getId()).thenReturn(id);
        when(node.getNextNodeId()).thenReturn(nextNodeId);
        when(node.getType()).thenReturn("DECISION_NODE");

        NodeEdge[] edges = Arrays.stream(edgeTargets)
                .map(target -> {
                    NodeEdge edge = Mockito.mock(NodeEdge.class);
                    when(edge.getTargetNodeId()).thenReturn(target);
                    return edge;
                })
                .toArray(NodeEdge[]::new);
        when(node.getEdges()).thenReturn(Arrays.asList(edges));
        return node;
    }

    /**
     * Injects a mocked {@link ModuleConfiguration} that reports the flow publisher as disabled.
     */
    private static void injectDisabledConfig(AbstractEventHandler target) throws Exception {

        ModuleConfiguration mockConfig = Mockito.mock(ModuleConfiguration.class);
        Properties props = new Properties();
        props.setProperty("moesifFlowPublisher.enable", "false");
        when(mockConfig.getModuleProperties()).thenReturn(props);

        Field configsField = AbstractEventHandler.class.getDeclaredField("configs");
        configsField.setAccessible(true);
        configsField.set(target, mockConfig);
    }

    /**
     * Build a mock {@link GraphConfig} with the given start node ID and node set.
     */
    private GraphConfig mockGraph(String startId, NodeConfig... nodes) {

        Map<String, NodeConfig> nodeMap = new HashMap<>();
        for (NodeConfig n : nodes) {
            nodeMap.put(n.getId(), n);
        }

        GraphConfig graph = Mockito.mock(GraphConfig.class);
        when(graph.getFirstNodeId()).thenReturn(startId);
        when(graph.getNodeConfigs()).thenReturn(nodeMap);
        return graph;
    }
}
