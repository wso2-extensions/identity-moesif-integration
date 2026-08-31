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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.event.bean.ModuleConfiguration;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link MoesifUserAuthenticationDataPublishHandler}.
 */
public class MoesifUserAuthenticationDataPublishHandlerTest {

    private MoesifUserAuthenticationDataPublishHandler handler;

    @BeforeMethod
    public void setUp() throws Exception {

        handler = new MoesifUserAuthenticationDataPublishHandler();
        injectDisabledConfig(handler);
    }

    @Test
    public void testGetName() {

        assertEquals(handler.getName(), "moesifUserAuthenticationPublisher");
    }

    @Test
    public void testHandleEventSkipsAuthStepSuccessWhenDisabled() throws Exception {

        Event event = new Event("AUTHENTICATION_STEP_SUCCESS", new HashMap<>());
        handler.handleEvent(event);
    }

    @Test
    public void testHandleEventSkipsAuthStepFailureWhenDisabled() throws Exception {

        Event event = new Event("AUTHENTICATION_STEP_FAILURE", new HashMap<>());
        handler.handleEvent(event);
    }

    @Test
    public void testHandleEventSkipsAuthSuccessWhenDisabled() throws Exception {

        Event event = new Event("AUTHENTICATION_SUCCESS", new HashMap<>());
        handler.handleEvent(event);
    }

    @Test
    public void testHandleEventSkipsAuthFailureWhenDisabled() throws Exception {

        Event event = new Event("AUTHENTICATION_FAILURE", new HashMap<>());
        handler.handleEvent(event);
    }

    @Test
    public void testHandleEventSkipsUnknownEventWhenDisabled() throws Exception {

        Event event = new Event("UNKNOWN_EVENT", new HashMap<>());
        handler.handleEvent(event);
    }

    @Test
    public void testIdentifierFirstStepIsDetected() throws Exception {

        assertTrue(invokeIsIdentifierFirstStep(buildStepEvent("IdentifierExecutor")));
    }

    @Test
    public void testOtherAuthenticatorStepIsNotIdentifierFirst() throws Exception {

        assertFalse(invokeIsIdentifierFirstStep(buildStepEvent("BasicAuthenticator")));
    }

    @Test
    public void testStepWithoutAuthenticatorIsNotIdentifierFirst() throws Exception {

        assertFalse(invokeIsIdentifierFirstStep(buildStepEvent(null)));
    }

    @Test
    public void testStepWithoutParamsIsNotIdentifierFirst() throws Exception {

        assertFalse(invokeIsIdentifierFirstStep(new Event("AUTHENTICATION_STEP_SUCCESS", new HashMap<>())));
    }

    private static Event buildStepEvent(String authenticator) {

        Map<String, Object> params = new HashMap<>();
        if (authenticator != null) {
            params.put("authenticator", authenticator);
        }
        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("params", params);
        return new Event("AUTHENTICATION_STEP_SUCCESS", eventProperties);
    }

    private boolean invokeIsIdentifierFirstStep(Event event) throws Exception {

        Method method = MoesifUserAuthenticationDataPublishHandler.class
                .getDeclaredMethod("isIdentifierFirstStep", Event.class);
        method.setAccessible(true);
        return (boolean) method.invoke(handler, event);
    }

    private static void injectDisabledConfig(Object target) throws Exception {

        ModuleConfiguration mockConfig = Mockito.mock(ModuleConfiguration.class);
        Properties props = new Properties();
        props.setProperty("moesifUserAuthenticationPublisher.enable", "false");
        when(mockConfig.getModuleProperties()).thenReturn(props);

        Field configsField = findField(target.getClass(), "configs");
        configsField.setAccessible(true);
        configsField.set(target, mockConfig);
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {

        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + name + "' not found in hierarchy of " + clazz.getName());
    }
}
