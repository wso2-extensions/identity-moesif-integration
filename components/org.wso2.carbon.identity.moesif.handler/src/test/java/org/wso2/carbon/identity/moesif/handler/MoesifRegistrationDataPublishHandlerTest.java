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
import java.util.HashMap;
import java.util.Properties;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Unit tests for {@link MoesifRegistrationDataPublishHandler}.
 */
public class MoesifRegistrationDataPublishHandlerTest {

    private MoesifRegistrationDataPublishHandler handler;

    @BeforeMethod
    public void setUp() throws Exception {

        handler = new MoesifRegistrationDataPublishHandler();
        injectDisabledConfig(handler);
    }

    @Test
    public void testGetName() {

        assertEquals(handler.getName(), "moesifUserRegistrationPublisher");
    }

    @Test
    public void testHandleEventSkipsWhenHandlerDisabled() throws Exception {

        Event event = new Event("POST_ADD_USER", new HashMap<>());
        // No exception expected — handler is disabled and returns early.
        handler.handleEvent(event);
    }

    @Test
    public void testHandleEventSkipsUnknownEventWhenDisabled() throws Exception {

        Event event = new Event("UNKNOWN_EVENT", new HashMap<>());
        handler.handleEvent(event);
    }

    private static void injectDisabledConfig(AbstractEventHandler target) throws Exception {

        ModuleConfiguration mockConfig = Mockito.mock(ModuleConfiguration.class);
        Properties props = new Properties();
        props.setProperty("moesifUserRegistrationPublisher.enable", "false");
        when(mockConfig.getModuleProperties()).thenReturn(props);

        Field configsField = AbstractEventHandler.class.getDeclaredField("configs");
        configsField.setAccessible(true);
        configsField.set(target, mockConfig);
    }
}
