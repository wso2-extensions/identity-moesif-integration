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

package org.wso2.carbon.identity.moesif.configuration;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.moesif.configuration.constant.MoesifConfigurationConstants;

import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link MoesifGovernanceConnectorConfig}.
 */
public class MoesifGovernanceConnectorConfigTest {

    private MoesifGovernanceConnectorConfig connectorConfig;

    @BeforeClass
    public void setUp() {

        connectorConfig = new MoesifGovernanceConnectorConfig();
    }

    @Test
    public void testGetName() {

        assertEquals(connectorConfig.getName(), "moesif-publisher");
    }

    @Test
    public void testGetFriendlyName() {

        assertEquals(connectorConfig.getFriendlyName(), "Moesif Publisher");
    }

    @Test
    public void testGetCategory() {

        assertEquals(connectorConfig.getCategory(), "Analytics");
    }

    @Test
    public void testGetSubCategory() {

        assertEquals(connectorConfig.getSubCategory(), "DEFAULT");
    }

    @Test
    public void testGetOrder() {

        assertEquals(connectorConfig.getOrder(), 0);
    }

    @Test
    public void testGetPropertyNames() {

        String[] propertyNames = connectorConfig.getPropertyNames();
        assertNotNull(propertyNames);
        assertEquals(propertyNames.length, 1);
        assertEquals(propertyNames[0], MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY);
    }

    @Test
    public void testGetDefaultPropertyValues() throws IdentityGovernanceException {

        Properties defaults = connectorConfig.getDefaultPropertyValues("carbon.super");
        assertNotNull(defaults);
        assertTrue(defaults.containsKey(MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY));
        assertEquals(defaults.getProperty(MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY), "false");
    }

    @Test
    public void testGetDefaultPropertyValuesWithNames() throws IdentityGovernanceException {

        Map<String, String> defaults = connectorConfig.getDefaultPropertyValues(
                new String[]{MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY}, "carbon.super");
        assertNotNull(defaults);
        assertTrue(defaults.containsKey(MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY));
        assertEquals(defaults.get(MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY), "false");
    }

    @Test
    public void testGetPropertyNameMapping() {

        Map<String, String> nameMapping = connectorConfig.getPropertyNameMapping();
        assertNotNull(nameMapping);
        assertTrue(nameMapping.containsKey(MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY));
        assertEquals(nameMapping.get(MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY),
                "Enable Moesif Publisher");
    }

    @Test
    public void testGetPropertyDescriptionMapping() {

        Map<String, String> descriptionMapping = connectorConfig.getPropertyDescriptionMapping();
        assertNotNull(descriptionMapping);
        assertTrue(descriptionMapping.containsKey(MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY));
    }
}
