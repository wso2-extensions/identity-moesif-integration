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

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.moesif.configuration.constant.MoesifConfigurationConstants;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementClientException;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementException;
import org.wso2.carbon.identity.moesif.configuration.model.MoesifPublisherDTO;
import org.wso2.carbon.identity.moesif.configuration.util.MoesifSecretProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link MoesifConfigurationManagementServiceImpl}.
 *
 * <p>The service now manages only the Moesif collector key (secret store) and the per-event-type
 * governance toggles; it no longer creates or deploys event publisher resources. {@link IdentityUtil}
 * is statically mocked so {@code validateIfMoesifEnabled()} passes, and {@link MoesifSecretProcessor}
 * is statically mocked so the "is the collector key configured?" existence check can be driven per
 * test. Paths that resolve the tenant via {@code PrivilegedCarbonContext} (the happy add/get-with-
 * governance flows) are exercised in integration tests, not here.</p>
 */
public class MoesifConfigurationManagementServiceImplTest {

    private MoesifConfigurationManagementServiceImpl service;
    private MockedStatic<IdentityUtil> mockedIdentityUtil;
    private MockedStatic<MoesifSecretProcessor> mockedSecretProcessor;

    @BeforeMethod
    public void setUp() {

        service = new MoesifConfigurationManagementServiceImpl();

        mockedIdentityUtil = Mockito.mockStatic(IdentityUtil.class);
        mockedIdentityUtil.when(() -> IdentityUtil.getProperty(MoesifConfigurationConstants.ENABLED_CONFIG))
                .thenReturn("true");

        mockedSecretProcessor = Mockito.mockStatic(MoesifSecretProcessor.class);
    }

    @AfterMethod
    public void tearDown() {

        if (mockedIdentityUtil != null) {
            mockedIdentityUtil.close();
        }
        if (mockedSecretProcessor != null) {
            mockedSecretProcessor.close();
        }
    }

    private void stubApiKeyConfigured(boolean configured) {

        mockedSecretProcessor.when(() ->
                        MoesifSecretProcessor.isSecretConfigured(anyString(), anyString(), anyString()))
                .thenReturn(configured);
    }

    // ── addMoesifPublisher — input validation ─────────────────────────────────

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testAddPublisherBlankApiKeyThrows() throws MoesifConfigurationManagementException {

        service.addMoesifPublisher("", null, false);
    }

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testAddPublisherNullApiKeyThrows() throws MoesifConfigurationManagementException {

        service.addMoesifPublisher(null, null, false);
    }

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testAddPublisherAlreadyExistsThrows() throws MoesifConfigurationManagementException {

        stubApiKeyConfigured(true);
        service.addMoesifPublisher("apiKey", null, false);
    }

    // ── getMoesifPublisher ────────────────────────────────────────────────────

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testGetPublisherNotFoundThrows() throws MoesifConfigurationManagementException {

        stubApiKeyConfigured(false);
        service.getMoesifPublisher();
    }

    // ── getMoesifPublishers ───────────────────────────────────────────────────

    @Test
    public void testGetPublishersReturnsEmptyWhenNotConfigured() throws MoesifConfigurationManagementException {

        stubApiKeyConfigured(false);
        List<MoesifPublisherDTO> result = service.getMoesifPublishers();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── updateMoesifPublisher ─────────────────────────────────────────────────

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testUpdatePublisherNotFoundThrows() throws MoesifConfigurationManagementException {

        stubApiKeyConfigured(false);
        service.updateMoesifPublisher("newKey", null, false);
    }
}
