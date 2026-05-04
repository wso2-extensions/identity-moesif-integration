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

import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementException;
import org.wso2.carbon.identity.moesif.configuration.model.MoesifPublisherDTO;

import java.util.List;

/**
 * Service interface for Moesif publisher configuration management operations.
 * Manages the lifecycle of Moesif event publishers that forward authentication
 * and registration events to the Moesif Actions API via the HTTP output event adapter.
 */
public interface MoesifConfigurationManagementService {

    /**
     * Create a new Moesif event publisher, storing the supplied API key as an encrypted secret.
     * All other publisher properties (URL, auth type, stream names, etc.) are taken from
     * hardcoded defaults. The Moesif portal API token used for generating dashboard-viewer
     * id_tokens is read directly from the IS server configuration via SecureVault.
     *
     * @param name        Publisher name (unique identifier).
     * @param apiKeyValue Plain-text Moesif collector API key (app_token).
     * @param moesifOrgId Moesif organization ID returned by the provision API.
     * @param moesifAppId Moesif application ID for the provisioned app.
     * @return Created Moesif publisher DTO (contains only the name; secrets are not returned).
     * @throws MoesifConfigurationManagementException If an error occurs while creating the publisher.
     */
    MoesifPublisherDTO addMoesifPublisher(String name, String apiKeyValue, String moesifOrgId,
                                           String moesifAppId)
            throws MoesifConfigurationManagementException;

    /**
     * Retrieve a Moesif event publisher configuration by name.
     *
     * @param publisherName Publisher name.
     * @return Moesif publisher configuration.
     * @throws MoesifConfigurationManagementException If an error occurs while retrieving the publisher.
     */
    MoesifPublisherDTO getMoesifPublisher(String publisherName) throws MoesifConfigurationManagementException;

    /**
     * Retrieve all Moesif event publisher configurations for the tenant.
     *
     * @return List of Moesif publisher configurations.
     * @throws MoesifConfigurationManagementException If an error occurs while retrieving the publishers.
     */
    List<MoesifPublisherDTO> getMoesifPublishers() throws MoesifConfigurationManagementException;

    /**
     * Update the API key for an existing Moesif event publisher and redeploy it.
     *
     * @param name        Publisher name.
     * @param apiKeyValue New plain-text Moesif API key value.
     * @return Updated Moesif publisher DTO.
     * @throws MoesifConfigurationManagementException If an error occurs while updating the publisher.
     */
    MoesifPublisherDTO updateMoesifPublisherApiKey(String name, String apiKeyValue)
            throws MoesifConfigurationManagementException;

    /**
     * Delete a Moesif event publisher configuration by name.
     *
     * @param publisherName Publisher name.
     * @throws MoesifConfigurationManagementException If an error occurs while deleting the publisher.
     */
    void deleteMoesifPublisher(String publisherName) throws MoesifConfigurationManagementException;

    /**
     * Generate a Moesif dashboard-viewer id_token for the given publisher.
     * Uses the stored moesifOrgId and the Moesif portal API token read from the IS server
     * configuration to call Moesif's id_tokens endpoint with {@code role=dashboard-viewer}.
     *
     * @param publisherName Publisher name.
     * @return Short-lived dashboard-viewer id_token from Moesif.
     * @throws MoesifConfigurationManagementException If an error occurs while generating the token.
     */
    String getDashboardViewerToken(String publisherName) throws MoesifConfigurationManagementException;
}
