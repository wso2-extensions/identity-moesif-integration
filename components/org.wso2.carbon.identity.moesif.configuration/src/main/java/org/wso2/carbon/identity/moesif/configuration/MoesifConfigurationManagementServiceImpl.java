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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.configuration.mgt.core.exception.ConfigurationManagementClientException;
import org.wso2.carbon.identity.configuration.mgt.core.exception.ConfigurationManagementException;
import org.wso2.carbon.identity.configuration.mgt.core.model.Attribute;
import org.wso2.carbon.identity.configuration.mgt.core.model.Resource;
import org.wso2.carbon.identity.configuration.mgt.core.model.ResourceFile;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.moesif.configuration.constant.MoesifConfigurationConstants;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementClientException;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementException;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementServerException;
import org.wso2.carbon.identity.moesif.configuration.internal.MoesifConfigurationDataHolder;
import org.wso2.carbon.identity.moesif.configuration.model.MoesifPublisherDTO;
import org.wso2.carbon.identity.moesif.configuration.util.MoesifPublisherUtils;
import org.wso2.carbon.identity.moesif.configuration.util.MoesifSecretProcessor;
import org.wso2.carbon.identity.secret.mgt.core.exception.SecretManagementException;
import org.wso2.carbon.identity.tenant.resource.manager.exception.TenantResourceManagementException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import static org.wso2.carbon.identity.configuration.mgt.core.constant.ConfigurationConstants.ErrorMessages.ERROR_CODE_RESOURCE_DOES_NOT_EXISTS;
import static org.wso2.carbon.identity.configuration.mgt.core.constant.ConfigurationConstants.ErrorMessages.ERROR_CODE_RESOURCE_TYPE_DOES_NOT_EXISTS;

/**
 * Implementation of {@link MoesifConfigurationManagementService}.
 * Manages Moesif event publisher configurations using ConfigurationManager for persistence
 * and ResourceManager for event publisher deployment.
 */
public class MoesifConfigurationManagementServiceImpl implements MoesifConfigurationManagementService {

    private static final Log log = LogFactory.getLog(MoesifConfigurationManagementServiceImpl.class);

    private static final String MOESIF_PUBLISHER_RESOURCE_TYPE = "Publisher";
    private static final String RESOURCE_NOT_EXISTS_ERROR_CODE = "CONFIGM_00017";

    private static final String PROVIDER_URL = "providerURL";
    private static final String AUTH_TYPE = "authType";
    private static final String STREAM_NAME = "streamName";
    private static final String STREAM_VERSION = "streamVersion";
    private static final String INLINE_BODY = "inlineBody";
    private static final String PUBLISHER_TYPE_PROPERTY = "publisherType";
    private static final String MOESIF_PUBLISHER_TYPE = "MOESIF";
    private static final String API_KEY_VALUE = "apiKeyValue";
    private static final String API_KEY_HEADER = "apiKeyHeader";
    private static final String API_KEY_AUTH_TYPE = "API_KEY";

    private static final String MOESIF_ORG_ID = "moesifOrgId";
    private static final String MOESIF_APP_ID = "moesifAppId";
    private static final String MOESIF_PORTAL_API_BASE_URL = "https://api.moesif.com";

    private static final String DEFAULT_PROVIDER_URL = "https://api.moesif.net/v1/actions";
    private static final String DEFAULT_AUTH_TYPE = "API_KEY";
    private static final String DEFAULT_API_KEY_HEADER = "X-Moesif-Application-Id";
    private static final String DEFAULT_STREAM_VERSION = "1.0.0";
    private static final String DEFAULT_INLINE_BODY = "";

    private static final String PUBLISHER_NAME = "moesifPublisher";
    private static final String PUBLISHER_STREAM = "org.wso2.is.analytics.stream.MoesifData";

    @Override
    public MoesifPublisherDTO addMoesifPublisher(String name, String apiKeyValue, String moesifOrgId,
                                                  String moesifAppId)
            throws MoesifConfigurationManagementException {

        if (StringUtils.isBlank(name)) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60001",
                    "Invalid input: publisher name is required.",
                    "Publisher name cannot be empty.");
        }
        if (StringUtils.isBlank(apiKeyValue)) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60001",
                    "Invalid input: API key value is required.",
                    "API key value cannot be empty.");
        }

        Optional<Resource> existingResource = getPublisherResource(name);
        if (existingResource.isPresent()) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60002",
                    "A Moesif publisher already exists with the name: " + name,
                    "Conflict: publisher name already in use.");
        }

        if (!PUBLISHER_NAME.equals(name)) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60001",
                    "Unsupported publisher name: " + name,
                    "Publisher name must be: " + PUBLISHER_NAME);
        }

        MoesifPublisherDTO dto = new MoesifPublisherDTO();
        dto.setName(name);
        dto.setProviderURL(DEFAULT_PROVIDER_URL);
        dto.setAuthType(DEFAULT_AUTH_TYPE);
        dto.setStreamName(PUBLISHER_STREAM);
        dto.setStreamVersion(DEFAULT_STREAM_VERSION);
        dto.setInlineBody(DEFAULT_INLINE_BODY);
        dto.setSecretProvider(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER);
        dto.getProperties().put(API_KEY_HEADER, DEFAULT_API_KEY_HEADER);
        dto.getProperties().put(API_KEY_VALUE, apiKeyValue);
        dto.setMoesifOrgId(moesifOrgId);
        dto.setMoesifAppId(moesifAppId);

        Resource resource = buildResourceFromMoesifPublisher(dto);
        try {
            MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                    .addResource(MOESIF_PUBLISHER_RESOURCE_TYPE, resource);
            reDeployEventPublisherConfiguration(resource);
            updateMoesifEnabledGovernanceConfig("true");
            MoesifPublisherDTO result = new MoesifPublisherDTO();
            result.setName(name);
            return result;
        } catch (ConfigurationManagementException e) {
            throw handleConfigurationMgtException(e, "Error while adding Moesif publisher: " + name);
        }
    }

    @Override
    public MoesifPublisherDTO getMoesifPublisher(String publisherName)
            throws MoesifConfigurationManagementException {

        Optional<Resource> resourceOptional = getPublisherResource(publisherName);
        if (resourceOptional.isEmpty()) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60004",
                    "Moesif publisher not found: " + publisherName,
                    "No Moesif publisher exists with the given name.");
        }
        return buildMoesifPublisherFromResource(resourceOptional.get());
    }

    @Override
    public List<MoesifPublisherDTO> getMoesifPublishers() throws MoesifConfigurationManagementException {

        try {
            List<Resource> resources = MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                    .getResourcesByType(MOESIF_PUBLISHER_RESOURCE_TYPE).getResources();
            if (resources == null || resources.isEmpty()) {
                return new ArrayList<>();
            }
            return resources.stream()
                    .map(this::buildMoesifPublisherFromResource)
                    .collect(Collectors.toList());
        } catch (ConfigurationManagementException e) {
            if (isResourceTypeNotExistsError(e)) {
                return new ArrayList<>();
            }
            throw handleConfigurationMgtException(e, "Error while retrieving Moesif publishers.");
        }
    }

    @Override
    public MoesifPublisherDTO updateMoesifPublisherApiKey(String name, String apiKeyValue)
            throws MoesifConfigurationManagementException {

        if (StringUtils.isBlank(name)) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60001",
                    "Invalid input: publisher name is required.",
                    "Publisher name cannot be empty.");
        }
        if (StringUtils.isBlank(apiKeyValue)) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60001",
                    "Invalid input: API key value is required.",
                    "API key value cannot be empty.");
        }

        Optional<Resource> existingResource = getPublisherResource(name);
        if (existingResource.isEmpty()) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60004",
                    "Moesif publisher not found: " + name,
                    "No Moesif publisher exists with the given name.");
        }

        if (!PUBLISHER_NAME.equals(name)) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60001",
                    "Unsupported publisher name: " + name,
                    "Publisher name must be: " + PUBLISHER_NAME);
        }

        MoesifPublisherDTO dto = new MoesifPublisherDTO();
        dto.setName(name);
        dto.setProviderURL(DEFAULT_PROVIDER_URL);
        dto.setAuthType(DEFAULT_AUTH_TYPE);
        dto.setStreamName(PUBLISHER_STREAM);
        dto.setStreamVersion(DEFAULT_STREAM_VERSION);
        dto.setInlineBody(DEFAULT_INLINE_BODY);
        dto.setSecretProvider(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER);
        dto.getProperties().put(API_KEY_HEADER, DEFAULT_API_KEY_HEADER);
        dto.getProperties().put(API_KEY_VALUE, apiKeyValue);

        Resource resource = buildResourceFromMoesifPublisher(dto);
        try {
            MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                    .replaceResource(MOESIF_PUBLISHER_RESOURCE_TYPE, resource);
            reDeployEventPublisherConfiguration(resource);
            updateMoesifEnabledGovernanceConfig("true");
            MoesifPublisherDTO result = new MoesifPublisherDTO();
            result.setName(name);
            return result;
        } catch (ConfigurationManagementException e) {
            throw handleConfigurationMgtException(e, "Error while updating Moesif publisher: " + name);
        }
    }

    @Override
    public void deleteMoesifPublisher(String publisherName) throws MoesifConfigurationManagementException {

        Optional<Resource> resourceOptional = getPublisherResource(publisherName);
        if (resourceOptional.isEmpty()) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60004",
                    "Moesif publisher not found: " + publisherName,
                    "No Moesif publisher exists with the given name.");
        }

        try {
            MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                    .deleteResource(MOESIF_PUBLISHER_RESOURCE_TYPE, publisherName);
        } catch (ConfigurationManagementException e) {
            throw handleConfigurationMgtException(e, "Error while deleting Moesif publisher: " + publisherName);
        }

        try {
            MoesifSecretProcessor.deleteSecrets(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER,
                    API_KEY_AUTH_TYPE, API_KEY_VALUE);
        } catch (SecretManagementException e) {
            log.error("Failed to delete Moesif API key secret for publisher: " + publisherName
                    + ". The secret may need to be cleaned up manually.", e);
        }

        updateMoesifEnabledGovernanceConfig("false");
    }

    @Override
    public String getDashboardViewerToken(String publisherName) throws MoesifConfigurationManagementException {

        Optional<Resource> resourceOpt = getPublisherResource(publisherName);
        if (resourceOpt.isEmpty()) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60004",
                    "Moesif publisher not found: " + publisherName,
                    "No Moesif publisher exists with the given name.");
        }

        String moesifOrgId = null;
        for (Attribute attr : resourceOpt.get().getAttributes()) {
            if (MOESIF_ORG_ID.equals(attr.getKey())) {
                moesifOrgId = attr.getValue();
                break;
            }
        }
        if (StringUtils.isBlank(moesifOrgId)) {
            throw new MoesifConfigurationManagementClientException("MOESIF_60005",
                    "moesifOrgId is not configured for publisher: " + publisherName,
                    "Publisher is missing the moesifOrgId attribute.");
        }

        String moesifApiToken = IdentityUtil.getProperty(
                MoesifConfigurationConstants.MOESIF_MASTER_API_TOKEN_PROPERTY);
        if (StringUtils.isBlank(moesifApiToken)) {
            throw new MoesifConfigurationManagementServerException("MOESIF_65007",
                    "Moesif master API token is not configured.",
                    "Configure \"" + MoesifConfigurationConstants.MOESIF_MASTER_API_TOKEN_PROPERTY
                            + "\" in identity.xml (deployment.toml).");
        }

        return callMoesifForDashboardToken(moesifOrgId, moesifApiToken);
    }

    private String callMoesifForDashboardToken(String moesifOrgId, String moesifApiToken)
            throws MoesifConfigurationManagementServerException {

        HttpURLConnection conn = null;
        try {
            URL url = new URL(MOESIF_PORTAL_API_BASE_URL + "/v1/portal/" + moesifOrgId
                    + "/oauth/id_tokens?role=dashboard-viewer");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Api-Token", moesifApiToken);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int statusCode = conn.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new MoesifConfigurationManagementServerException("MOESIF_65006",
                        "Moesif id_token call failed with HTTP status: " + statusCode,
                        "Unexpected status from Moesif: " + statusCode);
            }

            StringBuilder responseBody = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody.toString());
            String idToken = root.path("id_token").asText();
            if (StringUtils.isBlank(idToken)) {
                throw new MoesifConfigurationManagementServerException("MOESIF_65006",
                        "Moesif id_token response did not contain an id_token field.",
                        "Missing id_token in Moesif response.");
            }
            return idToken;
        } catch (IOException e) {
            throw new MoesifConfigurationManagementServerException("MOESIF_65005",
                    "IO error while calling Moesif id_token API.", e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Resource buildResourceFromMoesifPublisher(MoesifPublisherDTO moesifPublisher)
            throws MoesifConfigurationManagementServerException {

        InputStream inputStream;
        try {
            inputStream = MoesifPublisherUtils.generateMoesifPublisher(
                    moesifPublisher.getName(),
                    moesifPublisher.getStreamName(),
                    moesifPublisher.getStreamVersion(),
                    moesifPublisher.getProviderURL(),
                    moesifPublisher.getSecretProvider(),
                    moesifPublisher.getAuthType(),
                    moesifPublisher.getInlineBody(),
                    moesifPublisher.getProperties());
        } catch (ParserConfigurationException e) {
            throw new MoesifConfigurationManagementServerException("MOESIF_65001",
                    "Error generating event publisher XML.", e.getMessage(), e);
        } catch (TransformerException e) {
            throw new MoesifConfigurationManagementServerException("MOESIF_65002",
                    "Error transforming event publisher XML.", e.getMessage(), e);
        }

        Resource resource = new Resource();
        resource.setResourceName(moesifPublisher.getName());

        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute(PUBLISHER_TYPE_PROPERTY, MOESIF_PUBLISHER_TYPE));
        attributes.add(new Attribute(PROVIDER_URL, moesifPublisher.getProviderURL()));
        attributes.add(new Attribute(STREAM_NAME, moesifPublisher.getStreamName()));
        attributes.add(new Attribute(STREAM_VERSION, moesifPublisher.getStreamVersion()));
        if (StringUtils.isNotBlank(moesifPublisher.getAuthType())) {
            attributes.add(new Attribute(AUTH_TYPE, moesifPublisher.getAuthType()));
        }
        if (StringUtils.isNotBlank(moesifPublisher.getInlineBody())) {
            attributes.add(new Attribute(INLINE_BODY, moesifPublisher.getInlineBody()));
        }

        try {
            for (Map.Entry<String, String> entry : moesifPublisher.getProperties().entrySet()) {
                if (StringUtils.isBlank(entry.getKey()) || StringUtils.isBlank(entry.getValue())) {
                    continue;
                }
                if (API_KEY_VALUE.equals(entry.getKey())) {
                    attributes.add(new Attribute(entry.getKey(),
                            MoesifSecretProcessor.encryptSecret(
                                    MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER,
                                    API_KEY_AUTH_TYPE, API_KEY_VALUE, entry.getValue())));
                } else {
                    attributes.add(new Attribute(entry.getKey(), entry.getValue()));
                }
            }
        } catch (SecretManagementException e) {
            throw new MoesifConfigurationManagementServerException("MOESIF_65003",
                    "Error while encrypting Moesif API key.", e.getMessage(), e);
        }

        if (StringUtils.isNotBlank(moesifPublisher.getMoesifOrgId())) {
            attributes.add(new Attribute(MOESIF_ORG_ID, moesifPublisher.getMoesifOrgId()));
        }
        if (StringUtils.isNotBlank(moesifPublisher.getMoesifAppId())) {
            attributes.add(new Attribute(MOESIF_APP_ID, moesifPublisher.getMoesifAppId()));
        }
        resource.setAttributes(attributes);

        ResourceFile file = new ResourceFile();
        file.setName(moesifPublisher.getName());
        file.setInputStream(inputStream);
        List<ResourceFile> resourceFiles = new ArrayList<>();
        resourceFiles.add(file);
        resource.setFiles(resourceFiles);

        return resource;
    }

    private MoesifPublisherDTO buildMoesifPublisherFromResource(Resource resource) {

        MoesifPublisherDTO dto = new MoesifPublisherDTO();
        dto.setName(resource.getResourceName());

        if (resource.getAttributes() != null) {
            for (Attribute attribute : resource.getAttributes()) {
                String key = attribute.getKey();
                String value = attribute.getValue();
                if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
                    continue;
                }
                switch (key) {
                    case PROVIDER_URL:
                        dto.setProviderURL(value);
                        break;
                    case AUTH_TYPE:
                        dto.setAuthType(value);
                        break;
                    case STREAM_NAME:
                        dto.setStreamName(value);
                        break;
                    case STREAM_VERSION:
                        dto.setStreamVersion(value);
                        break;
                    case INLINE_BODY:
                        dto.setInlineBody(value);
                        break;
                    case MOESIF_ORG_ID:
                        dto.setMoesifOrgId(value);
                        break;
                    case MOESIF_APP_ID:
                        dto.setMoesifAppId(value);
                        break;
                    case PUBLISHER_TYPE_PROPERTY:
                        break;
                    default:
                        dto.getProperties().put(key, value);
                        break;
                }
            }
        }
        return dto;
    }

    private Optional<Resource> getPublisherResource(String publisherName)
            throws MoesifConfigurationManagementServerException {

        try {
            Resource resource = MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                    .getResource(MOESIF_PUBLISHER_RESOURCE_TYPE, publisherName);
            return Optional.ofNullable(resource);
        } catch (ConfigurationManagementClientException e) {
            if (RESOURCE_NOT_EXISTS_ERROR_CODE.equals(e.getErrorCode()) ||
                    ERROR_CODE_RESOURCE_DOES_NOT_EXISTS.getCode().equals(e.getErrorCode()) ||
                    ERROR_CODE_RESOURCE_TYPE_DOES_NOT_EXISTS.getCode().equals(e.getErrorCode())) {
                return Optional.empty();
            }
            throw new MoesifConfigurationManagementServerException("MOESIF_65003",
                    "Error retrieving Moesif publisher resource.", e.getMessage(), e);
        } catch (ConfigurationManagementException e) {
            throw new MoesifConfigurationManagementServerException("MOESIF_65003",
                    "Error retrieving Moesif publisher resource.", e.getMessage(), e);
        }
    }

    private void reDeployEventPublisherConfiguration(Resource resource) {

        if (resource.getFiles() == null || resource.getFiles().isEmpty()) {
            return;
        }
        ResourceFile file = resource.getFiles().getFirst();
        try {
            MoesifConfigurationDataHolder.getInstance().getResourceManager()
                    .addEventPublisherConfiguration(file);
        } catch (TenantResourceManagementException e) {
            log.warn("Error re-deploying Moesif event publisher configuration: " + e.getMessage());
        }
    }

    private boolean isResourceTypeNotExistsError(ConfigurationManagementException e) {

        return e instanceof ConfigurationManagementClientException &&
                ERROR_CODE_RESOURCE_TYPE_DOES_NOT_EXISTS.getCode().equals(e.getErrorCode());
    }

    private MoesifConfigurationManagementException handleConfigurationMgtException(
            ConfigurationManagementException e, String message) {

        if (e instanceof ConfigurationManagementClientException) {
            return new MoesifConfigurationManagementClientException("MOESIF_60003", message, e.getMessage());
        }
        return new MoesifConfigurationManagementServerException("MOESIF_65003", message, e.getMessage(), e);
    }

    private void updateMoesifEnabledGovernanceConfig(String enabled) {

        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        Map<String, String> configProperties = new HashMap<>();
        configProperties.put(MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY, enabled);
        try {
            MoesifConfigurationDataHolder.getInstance().getIdentityGovernanceService()
                    .updateConfiguration(tenantDomain, configProperties);
        } catch (IdentityGovernanceException e) {
            log.error("Failed to update Moesif governance property '"
                    + MoesifConfigurationConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY
                    + "' to '" + enabled + "' for tenant: " + tenantDomain, e);
        }
    }
}
