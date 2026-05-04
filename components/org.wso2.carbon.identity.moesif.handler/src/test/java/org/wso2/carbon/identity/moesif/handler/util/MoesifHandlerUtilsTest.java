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
import org.wso2.carbon.identity.event.event.Event;
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

    // ---- getMetaDataArray -------------------------------------------------------

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
        assertEquals(meta[0], MoesifHandlerConstants.NOT_AVAILABLE);
        assertEquals(meta[1], MoesifHandlerConstants.NOT_AVAILABLE);
        assertEquals(meta[2], MoesifHandlerConstants.NOT_AVAILABLE);
        assertEquals(meta[3], MoesifHandlerConstants.NOT_AVAILABLE);
    }

    @Test
    public void testGetMetaDataArrayPartialNulls() {

        Object[] meta = MoesifHandlerUtils.getMetaDataArray("org-uuid", null, "user-42", null);
        assertEquals(meta[0], "org-uuid");
        assertEquals(meta[1], MoesifHandlerConstants.NOT_AVAILABLE);
        assertEquals(meta[2], "user-42");
        assertEquals(meta[3], MoesifHandlerConstants.NOT_AVAILABLE);
    }

    // ---- buildMoesifFlowStepPayload ---------------------------------------------

    @Test
    public void testBuildMoesifFlowStepPayloadAllFields() {

        Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(
                "REGISTRATION", "VIEW", "STEP_1_VIEW_SUCCESS",
                "PROMPT_NODE", "carbon.super", "flow-ctx-123", null);
        assertNotNull(payload);
        assertEquals(payload.length, 8);
        assertEquals(payload[0], "REGISTRATION");
        assertEquals(payload[1], "VIEW");
        assertEquals(payload[2], "STEP_1_VIEW_SUCCESS");
        assertEquals(payload[3], "PROMPT_NODE");
        assertEquals(payload[4], "flow-ctx-123");
        assertEquals(payload[5], "carbon.super");
        assertNotNull(payload[6], "Publishing timestamp should not be null");
        assertEquals(payload[7], MoesifHandlerConstants.NOT_AVAILABLE); // null errorCode → NOT_AVAILABLE
    }

    @Test
    public void testBuildMoesifFlowStepPayloadWithErrorCode() {

        Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(
                "PASSWORD_RECOVERY", "TASK_EXECUTION", "STEP_2_TASK_EXECUTION_SUCCESS",
                "TASK_NODE", "wso2.com", "ctx-abc", "OTP_EXPIRED");
        assertEquals(payload[0], "PASSWORD_RECOVERY");
        assertEquals(payload[7], "OTP_EXPIRED");
    }

    @Test
    public void testBuildMoesifFlowStepPayloadNullsDefaultToNotAvailable() {

        Object[] payload = MoesifHandlerUtils.buildMoesifFlowStepPayload(
                null, null, null, null, null, null, null);
        // Indices 0-5 are nullable fields that default to NOT_AVAILABLE.
        for (int i = 0; i < 6; i++) {
            assertEquals(payload[i], MoesifHandlerConstants.NOT_AVAILABLE,
                    "Null field at index " + i + " should be NOT_AVAILABLE");
        }
        // Index 6 is publishingTime (always set); index 7 is errorCode (null → NOT_AVAILABLE).
        assertEquals(payload[7], MoesifHandlerConstants.NOT_AVAILABLE,
                "Null errorCode at index 7 should be NOT_AVAILABLE");
    }

    // ---- extractUserAgent -------------------------------------------------------

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

    // ---- getTimestamp ----------------------------------------------------------

    @Test
    public void testGetTimestampReturnsNonNullFormattedString() {

        String timestamp = MoesifHandlerUtils.getTimestamp();
        assertNotNull(timestamp);
        // Format: yyyy-MM-dd HH:mm:ss (19 chars)
        assertEquals(timestamp.length(), 19);
        assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }
}
