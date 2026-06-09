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
import org.wso2.carbon.identity.configuration.mgt.core.ConfigurationManager;
import org.wso2.carbon.identity.configuration.mgt.core.exception.ConfigurationManagementClientException;
import org.wso2.carbon.identity.configuration.mgt.core.model.Resource;
import org.wso2.carbon.identity.configuration.mgt.core.model.Resources;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.moesif.configuration.constant.MoesifConfigurationConstants;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementClientException;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementException;
import org.wso2.carbon.identity.moesif.configuration.internal.MoesifConfigurationDataHolder;
import org.wso2.carbon.identity.moesif.configuration.model.MoesifPublisherDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link MoesifConfigurationManagementServiceImpl}.
 *
 * <p>All OSGi-bound services ({@link ConfigurationManager}, etc.) are replaced by
 * Mockito mocks injected directly into {@link MoesifConfigurationDataHolder} via its
 * public setters before every test method.</p>
 *
 * <p>{@link IdentityUtil} is statically mocked so that {@code validateIfMoesifEnabled()}
 * returns {@code true} by default in all tests.</p>
 */
public class MoesifConfigurationManagementServiceImplTest {

    private MoesifConfigurationManagementServiceImpl service;
    private ConfigurationManager mockConfigManager;
    private MockedStatic<IdentityUtil> mockedIdentityUtil;

    private static final String RESOURCE_TYPE = "Publisher";
    /**
     * Canonical auth publisher resource name — used as the existence-check resource
     * in add/get/update/delete operations.
     */
    /** The user-link publisher resource is the canonical (always-deployed) resource. */
    private static final String CANONICAL_PUBLISHER_RESOURCE_NAME =
            MoesifConfigurationConstants.USER_LINK_PUBLISHER_RESOURCE_NAME;

    @BeforeMethod
    public void setUp() {

        mockConfigManager = Mockito.mock(ConfigurationManager.class);
        MoesifConfigurationDataHolder.getInstance().setConfigurationManager(mockConfigManager);
        service = new MoesifConfigurationManagementServiceImpl();

        // Static-mock IdentityUtil so validateIfMoesifEnabled() passes by default.
        mockedIdentityUtil = Mockito.mockStatic(IdentityUtil.class);
        mockedIdentityUtil.when(() -> IdentityUtil.getProperty(MoesifConfigurationConstants.ENABLED_CONFIG))
                .thenReturn("true");
    }

    @AfterMethod
    public void tearDown() {

        if (mockedIdentityUtil != null) {
            mockedIdentityUtil.close();
        }
    }

    // ── addMoesifPublisher — input validation (name is fixed internally) ──────

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testAddPublisherBlankApiKeyThrows() throws MoesifConfigurationManagementException {

        service.addMoesifPublisher("", null);
    }

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testAddPublisherNullApiKeyThrows() throws MoesifConfigurationManagementException {

        service.addMoesifPublisher(null, null);
    }

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testAddPublisherAlreadyExistsThrows() throws Exception {

        when(mockConfigManager.getResource(eq(RESOURCE_TYPE), eq(CANONICAL_PUBLISHER_RESOURCE_NAME)))
                .thenReturn(buildResource(CANONICAL_PUBLISHER_RESOURCE_NAME));
        service.addMoesifPublisher("apiKey", null);
    }

    // ── getMoesifPublisher ────────────────────────────────────────────────────

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testGetPublisherNotFoundThrows() throws Exception {

        stubResourceNotFound(CANONICAL_PUBLISHER_RESOURCE_NAME);
        service.getMoesifPublisher();
    }

    // ── getMoesifPublishers ───────────────────────────────────────────────────

    /**
     * When the resource type does not exist yet (first run), the method must silently return
     * an empty list rather than propagating the framework exception.
     */
    @Test
    public void testGetPublishersReturnsEmptyWhenResourceTypeNotExists() throws Exception {

        // "CONFIGM_00008" = ERROR_CODE_RESOURCE_TYPE_DOES_NOT_EXISTS
        ConfigurationManagementClientException typeNotFound =
                new ConfigurationManagementClientException("Resource type not found", "CONFIGM_00008");
        when(mockConfigManager.getResourcesByType(RESOURCE_TYPE)).thenThrow(typeNotFound);

        List<MoesifPublisherDTO> result = service.getMoesifPublishers();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * When the resource list returned by the configuration manager is empty, the method
     * must return an empty list.
     */
    @Test
    public void testGetPublishersReturnsEmptyWhenNoResources() throws Exception {

        Resources emptyResources = new Resources(Collections.emptyList());
        when(mockConfigManager.getResourcesByType(RESOURCE_TYPE)).thenReturn(emptyResources);

        List<MoesifPublisherDTO> result = service.getMoesifPublishers();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * When resources exist, they must be mapped to DTOs and returned.
     */
    @Test
    public void testGetPublishersReturnsMappedDTOs() throws Exception {

        Resource resource = buildResource(CANONICAL_PUBLISHER_RESOURCE_NAME);
        Resources resources = new Resources(Collections.singletonList(resource));
        when(mockConfigManager.getResourcesByType(RESOURCE_TYPE)).thenReturn(resources);

        List<MoesifPublisherDTO> result = service.getMoesifPublishers();
        assertNotNull(result);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getName(), CANONICAL_PUBLISHER_RESOURCE_NAME);
    }

    // ── updateMoesifPublisher — input validation ──────────────────────────────

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testUpdatePublisherApiKeyBlankKeyThrows() throws MoesifConfigurationManagementException {

        service.updateMoesifPublisher("", null);
    }

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testUpdatePublisherApiKeyNullKeyThrows() throws MoesifConfigurationManagementException {

        service.updateMoesifPublisher(null, null);
    }

    @Test(expectedExceptions = MoesifConfigurationManagementClientException.class)
    public void testUpdatePublisherApiKeyNotFoundThrows() throws Exception {

        stubResourceNotFound(CANONICAL_PUBLISHER_RESOURCE_NAME);
        service.updateMoesifPublisher("newKey", null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Stubs {@link ConfigurationManager#getResource} to throw a "resource not found" client
     * exception (error code {@code "CONFIGM_00017"}) for the given publisher name.
     * The {@code getPublisherResource} method in the impl translates this into
     * {@link java.util.Optional#empty()}.
     */
    private void stubResourceNotFound(String publisherName) throws Exception {

        ConfigurationManagementClientException notFound =
                new ConfigurationManagementClientException("Resource not found", "CONFIGM_00017");
        when(mockConfigManager.getResource(eq(RESOURCE_TYPE), eq(publisherName))).thenThrow(notFound);
    }

    /**
     * Build a minimal {@link Resource} with the given name and an empty attributes list.
     */
    private static Resource buildResource(String name) {

        Resource resource = new Resource();
        resource.setResourceName(name);
        resource.setAttributes(new ArrayList<>());
        return resource;
    }
}
