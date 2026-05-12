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

package org.wso2.carbon.identity.moesif.handler.util;

import org.mockito.Mockito;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import javax.servlet.http.HttpServletRequest;

import static org.wso2.carbon.identity.event.IdentityEventConstants.EventProperty.REQUEST;

/**
 * Unit tests for {@link MoesifHandlerUtils}.
 */
public class MoesifHandlerUtilsTest {

    @Test
    public void testGetMetaDataArrayAllFields() {

        Object[] meta = MoesifHandlerUtils.getMetaDataArray("org-uuid-1", "User-Registration", "user-1",
                "Mozilla/5.0");
        assertNotNull(meta);
        assertEquals(meta.length, 4);
        assertEquals(meta[0], "org-uuid-1");
        assertEquals(meta[1], "User-Registration");
        assertEquals(meta[2], "user-1");
        assertEquals(meta[3], "Mozilla/5.0");
    }

    @Test
    public void testGetMetaDataArrayNullFieldsDefaultToNotAvailable() {

        Object[] meta = MoesifHandlerUtils.getMetaDataArray(null, null, null, null);
        assertNotNull(meta);
        assertEquals(meta.length, 4);
        assertEquals(meta[0], MoesifCommonConstants.NOT_AVAILABLE);
        assertEquals(meta[1], MoesifCommonConstants.NOT_AVAILABLE);
        assertEquals(meta[2], MoesifCommonConstants.NOT_AVAILABLE);
        assertEquals(meta[3], MoesifCommonConstants.NOT_AVAILABLE);
    }

    @Test
    public void testGetMetaDataArrayPartialNulls() {

        Object[] meta = MoesifHandlerUtils.getMetaDataArray("org-uuid", null, "user-42", null);
        assertEquals(meta[0], "org-uuid");
        assertEquals(meta[1], MoesifCommonConstants.NOT_AVAILABLE);
        assertEquals(meta[2], "user-42");
        assertEquals(meta[3], MoesifCommonConstants.NOT_AVAILABLE);
    }

    @Test
    public void testBuildMoesifFlowStepPayloadAllFields() {

        Map<String, Object> eventProperties = buildFlowStepEventProperties(
                "REGISTRATION", "VIEW", "STEP_1_VIEW_SUCCESS", "PROMPT_NODE", "flow-ctx-123",
                "carbon.super", "SUCCESS", "NEXT", "app-1", "basic-executor", "OTP_EXPIRED");

        Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(eventProperties, "org-1", "parent-org");
        assertNotNull(payload);
        assertEquals(payload.length, 14);
        assertEquals(payload[0], "REGISTRATION");
        assertEquals(payload[1], "VIEW");
        assertEquals(payload[2], "STEP_1_VIEW_SUCCESS");
        assertEquals(payload[3], "PROMPT_NODE");
        assertEquals(payload[4], "flow-ctx-123");
        assertEquals(payload[5], "carbon.super");
        assertEquals(payload[6], "SUCCESS");
        assertEquals(payload[7], "NEXT");
        assertEquals(payload[8], "app-1");
        assertEquals(payload[9], "basic-executor");
        assertEquals(payload[10], "org-1");
        assertEquals(payload[11], true);
        assertNotNull(payload[12], "Publishing timestamp should not be null");
        assertEquals(payload[13], IdentityEventConstants.EventProperty.ERROR_CODE);
    }

    @Test
    public void testBuildMoesifFlowStepPayloadWithErrorCode() {

        Map<String, Object> eventProperties = buildFlowStepEventProperties(
                "PASSWORD_RECOVERY", "TASK_EXECUTION", "STEP_2_TASK_EXECUTION_SUCCESS", "TASK_NODE", "ctx-abc",
                "wso2.com", "SUCCESS", "EXECUTED", "app-22", "task-executor", "OTP_EXPIRED");

        Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(eventProperties, "org-1", "org-1");
        assertEquals(payload[0], "PASSWORD_RECOVERY");
        assertEquals(payload[11], false);
        assertEquals(payload[13], IdentityEventConstants.EventProperty.ERROR_CODE);
    }

    @Test
    public void testBuildMoesifFlowStepPayloadNullsDefaultToNotAvailable() {

        Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(new HashMap<>(), null, null);
        for (int i = 0; i < 10; i++) {
            assertEquals(payload[i], MoesifCommonConstants.NOT_AVAILABLE,
                    "Null field at index " + i + " should be NOT_AVAILABLE");
        }
        assertEquals(payload[10], null);
        assertEquals(payload[11], false);
        assertNotNull(payload[12]);
        assertEquals(payload[13], IdentityEventConstants.EventProperty.ERROR_CODE);
    }

    @Test
    public void testExtractUserAgentPresentInRequest() {

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent/1.0");

        Map<String, Object> props = new HashMap<>();
        props.put(REQUEST, request);
        Event event = new Event("TEST_EVENT", props);

        Optional<String> userAgent = MoesifHandlerUtils.extractUserAgent(event);
        assertTrue(userAgent.isPresent());
        assertEquals(userAgent.get(), "TestAgent/1.0");
    }

    @Test
    public void testExtractUserAgentAbsentInRequest() {

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn(null);

        Map<String, Object> props = new HashMap<>();
        props.put(REQUEST, request);
        Event event = new Event("TEST_EVENT", props);

        Optional<String> userAgent = MoesifHandlerUtils.extractUserAgent(event);
        assertFalse(userAgent.isPresent());
    }

    @Test
    public void testExtractUserAgentNoRequestProperty() {

        Event event = new Event("TEST_EVENT", new HashMap<>());
        Optional<String> userAgent = MoesifHandlerUtils.extractUserAgent(event);
        assertFalse(userAgent.isPresent());
    }

    @Test
    public void testGetTimestampReturnsNonNullFormattedString() {

        String timestamp = MoesifHandlerUtils.getTimestamp();
        assertNotNull(timestamp);
        // Format: yyyy-MM-dd HH:mm:ss (19 chars)
        assertEquals(timestamp.length(), 19);
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    private Map<String, Object> buildFlowStepEventProperties(String flowType, String stepType, String currentNodeId,
                                                             String currentNodeType, String contextId,
                                                             String tenantDomain, String nodeResponseStatus,
                                                             String nodeResponseType, String applicationId,
                                                             String executorName, String errorCode) {

        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put(IdentityEventConstants.EventProperty.FLOW_TYPE, flowType);
        eventProperties.put(IdentityEventConstants.EventProperty.STEP_TYPE, stepType);
        eventProperties.put(IdentityEventConstants.EventProperty.CURRENT_NODE_ID, currentNodeId);
        eventProperties.put(IdentityEventConstants.EventProperty.CURRENT_NODE_TYPE, currentNodeType);
        eventProperties.put(IdentityEventConstants.EventProperty.CONTEXT_ID, contextId);
        eventProperties.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, tenantDomain);
        eventProperties.put(IdentityEventConstants.EventProperty.CURRENT_NODE_RESPONSE_STATUS, nodeResponseStatus);
        eventProperties.put(IdentityEventConstants.EventProperty.CURRENT_NODE_RESPONSE_TYPE, nodeResponseType);
        eventProperties.put(IdentityEventConstants.EventProperty.APPLICATION_ID, applicationId);
        eventProperties.put(IdentityEventConstants.EventProperty.EXECUTOR_NAME, executorName);
        eventProperties.put(IdentityEventConstants.EventProperty.ERROR_CODE, errorCode);
        return eventProperties;
    }
}
