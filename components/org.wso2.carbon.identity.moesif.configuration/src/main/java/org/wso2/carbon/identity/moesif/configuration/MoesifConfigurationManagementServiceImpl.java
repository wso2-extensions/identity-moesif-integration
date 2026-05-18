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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.configuration.mgt.core.exception.ConfigurationManagementClientException;
import org.wso2.carbon.identity.configuration.mgt.core.exception.ConfigurationManagementException;
import org.wso2.carbon.identity.configuration.mgt.core.model.Attribute;
import org.wso2.carbon.identity.configuration.mgt.core.model.Resource;
import org.wso2.carbon.identity.configuration.mgt.core.model.ResourceFile;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.moesif.configuration.constant.MoesifConfigurationConstants;
import org.wso2.carbon.identity.moesif.configuration.constant.MoesifConfigurationErrorConstants.ErrorMessages;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementClientException;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementException;
import org.wso2.carbon.identity.moesif.configuration.exception.MoesifConfigurationManagementServerException;
import org.wso2.carbon.identity.moesif.configuration.internal.MoesifConfigurationDataHolder;
import org.wso2.carbon.identity.moesif.configuration.model.MoesifPublisherDTO;
import org.wso2.carbon.identity.moesif.configuration.util.MoesifPublisherUtils;
import org.wso2.carbon.identity.moesif.configuration.util.MoesifSecretProcessor;
import org.wso2.carbon.identity.secret.mgt.core.exception.SecretManagementException;
import org.wso2.carbon.identity.tenant.resource.manager.exception.TenantResourceManagementException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import static org.wso2.carbon.identity.configuration.mgt.core.constant.ConfigurationConstants.ErrorMessages.ERROR_CODE_RESOURCE_DOES_NOT_EXISTS;
import static org.wso2.carbon.identity.configuration.mgt.core.constant.ConfigurationConstants.ErrorMessages.ERROR_CODE_RESOURCE_TYPE_DOES_NOT_EXISTS;
import static org.wso2.carbon.identity.moesif.configuration.constant.MoesifConfigurationConstants.*;

/**
 * Implementation of {@link MoesifConfigurationManagementService}.
 * Manages Moesif event publisher configurations using ConfigurationManager for persistence
 * and ResourceManager for event publisher deployment.
 */
public class MoesifConfigurationManagementServiceImpl implements MoesifConfigurationManagementService {

    private static final Log log = LogFactory.getLog(MoesifConfigurationManagementServiceImpl.class);

    private static final String MOESIF_PUBLISHER_RESOURCE_TYPE = "Publisher";
    private static final String RESOURCE_NOT_EXISTS_ERROR_CODE = "CONFIGM_00017";
    private static final String MOESIF_PUBLISHER_CANONICAL_NAME = "moesif-publisher";

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

    /**
     * Ordered map of publisher type key → governance property constant.
     */
    private static final Map<String, String> PUBLISHER_TYPE_PROPERTY_MAP;

    /**
     * Ordered map of publisher type key → IS Analytics event publisher resource name.
     * Each entry corresponds to one event publisher XML deployed in the server.
     */
    private static final Map<String, String> PUBLISHER_RESOURCE_MAP;

    /**
     * Ordered map of publisher type key → IS Analytics event stream name (without version).
     * Must align with stream definitions in the handler module.
     */
    private static final Map<String, String> PUBLISHER_STREAM_MAP;

    static {
        Map<String, String> props = new LinkedHashMap<>();
        props.put(MOESIF_AUTHENTICATION_PUBLISHER,
                MoesifCommonConstants.MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY);
        props.put(MOESIF_REGISTRATION_PUBLISHER,
                MoesifCommonConstants.MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY);
        props.put(MOESIF_FLOW_PUBLISHER,
                MoesifCommonConstants.MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY);
        props.put(MOESIF_ORG_SWITCH_PUBLISHER,
                MoesifCommonConstants.MOESIF_ORG_SWITCH_PUBLISHER_ENABLED_PROPERTY);
        props.put(MOESIF_SESSION_PUBLISHER,
                MoesifCommonConstants.MOESIF_SESSION_PUBLISHER_ENABLED_PROPERTY);
        PUBLISHER_TYPE_PROPERTY_MAP = Collections.unmodifiableMap(props);

        Map<String, String> resources = new LinkedHashMap<>();
        resources.put(MOESIF_AUTHENTICATION_PUBLISHER,
                MoesifConfigurationConstants.AUTH_PUBLISHER_RESOURCE_NAME);
        resources.put(MOESIF_REGISTRATION_PUBLISHER,
                MoesifConfigurationConstants.REGISTRATION_PUBLISHER_RESOURCE_NAME);
        resources.put(MOESIF_FLOW_PUBLISHER,
                MoesifConfigurationConstants.FLOW_PUBLISHER_RESOURCE_NAME);
        resources.put(MOESIF_ORG_SWITCH_PUBLISHER,
                MoesifConfigurationConstants.ORG_SWITCH_PUBLISHER_RESOURCE_NAME);
        resources.put(MOESIF_SESSION_PUBLISHER,
                MoesifConfigurationConstants.SESSION_PUBLISHER_RESOURCE_NAME);
        PUBLISHER_RESOURCE_MAP = Collections.unmodifiableMap(resources);

        Map<String, String> streams = new LinkedHashMap<>();
        streams.put(MOESIF_AUTHENTICATION_PUBLISHER,
                MoesifConfigurationConstants.AUTH_PUBLISHER_STREAM_NAME);
        streams.put(MOESIF_REGISTRATION_PUBLISHER,
                MoesifConfigurationConstants.REGISTRATION_PUBLISHER_STREAM_NAME);
        streams.put(MOESIF_FLOW_PUBLISHER,
                MoesifConfigurationConstants.FLOW_PUBLISHER_STREAM_NAME);
        streams.put(MOESIF_ORG_SWITCH_PUBLISHER,
                MoesifConfigurationConstants.ORG_SWITCH_PUBLISHER_STREAM_NAME);
        streams.put(MOESIF_SESSION_PUBLISHER,
                MoesifConfigurationConstants.SESSION_PUBLISHER_STREAM_NAME);
        PUBLISHER_STREAM_MAP = Collections.unmodifiableMap(streams);
    }

    @Override
    public MoesifPublisherDTO addMoesifPublisher(String apiKeyValue, Map<String, Boolean> eventPublisherEnablement)
            throws MoesifConfigurationManagementException {

        validateIfMoesifEnabled();
        if (StringUtils.isBlank(apiKeyValue)) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getCode(),
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getMessage(),
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getDescription());
        }

        if (eventPublisherEnablement != null && !eventPublisherEnablement.isEmpty()) {
            List<String> unsupportedKeys = eventPublisherEnablement.keySet().stream()
                    .filter(key -> !PUBLISHER_TYPE_PROPERTY_MAP.containsKey(key))
                    .toList();
            if (!unsupportedKeys.isEmpty()) {
                throw new MoesifConfigurationManagementClientException(
                        ErrorMessages.ERROR_INVALID_PUBLISHER_TYPE.getCode(),
                        String.format(ErrorMessages.ERROR_INVALID_PUBLISHER_TYPE.getMessage(),
                                unsupportedKeys, PUBLISHER_TYPE_PROPERTY_MAP.keySet()),
                        ErrorMessages.ERROR_INVALID_PUBLISHER_TYPE.getDescription());
            }
        }

        // Use the auth publisher resource as the canonical existence check.
        Optional<Resource> existingResource =
                getPublisherResource(MoesifConfigurationConstants.AUTH_PUBLISHER_RESOURCE_NAME);
        if (existingResource.isPresent()) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_PUBLISHER_ALREADY_EXISTS.getCode(),
                    ErrorMessages.ERROR_PUBLISHER_ALREADY_EXISTS.getMessage(),
                    ErrorMessages.ERROR_PUBLISHER_ALREADY_EXISTS.getDescription());
        }

        try {
            for (Map.Entry<String, String> entry : PUBLISHER_RESOURCE_MAP.entrySet()) {
                String typeKey = entry.getKey();
                String resourceName = entry.getValue();
                String streamName = PUBLISHER_STREAM_MAP.get(typeKey);
                MoesifPublisherDTO dto = buildPublisherDTOFromConfig(apiKeyValue, resourceName, streamName);
                Resource resource = buildResourceFromMoesifPublisher(dto);
                MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                        .addResource(MOESIF_PUBLISHER_RESOURCE_TYPE, resource);
                reDeployEventPublisherConfiguration(resource);
            }
        } catch (ConfigurationManagementException e) {
            throw handleConfigurationMgtException(e,
                    String.format(ErrorMessages.ERROR_ADDING_PUBLISHER.getMessage(), MOESIF_PUBLISHER_CANONICAL_NAME));
        }

        Map<String, Boolean> resolved = eventPublisherEnablement != null ?
                eventPublisherEnablement : Collections.emptyMap();
        updateAllGovernanceConfigs(resolved);

        MoesifPublisherDTO result = new MoesifPublisherDTO();
        result.setName(MOESIF_PUBLISHER_CANONICAL_NAME);
        result.setPublisherTypes(eventPublisherEnablement);
        return result;
    }

    @Override
    public MoesifPublisherDTO getMoesifPublisher()
            throws MoesifConfigurationManagementException {

        validateIfMoesifEnabled();
        // Use auth publisher resource as the canonical source for shared config attributes.
        Optional<Resource> resourceOptional =
                getPublisherResource(MoesifConfigurationConstants.AUTH_PUBLISHER_RESOURCE_NAME);
        if (resourceOptional.isEmpty()) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getCode(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getMessage(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getDescription());
        }
        MoesifPublisherDTO dto = buildMoesifPublisherFromResource(resourceOptional.get());
        dto.setName(MOESIF_PUBLISHER_CANONICAL_NAME);
        populateGovernanceConfigsIntoDto(dto);
        return dto;
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
            throw handleConfigurationMgtException(e, ErrorMessages.ERROR_RETRIEVING_PUBLISHERS.getMessage());
        }
    }

    @Override
    public MoesifPublisherDTO updateMoesifPublisher(String apiKeyValue, Map<String, Boolean> eventPublisherEnablement)
            throws MoesifConfigurationManagementException {

        validateIfMoesifEnabled();
        if (StringUtils.isBlank(apiKeyValue)) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getCode(),
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getMessage(),
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getDescription());
        }

        if (eventPublisherEnablement != null && !eventPublisherEnablement.isEmpty()) {
            List<String> unsupportedKeys = eventPublisherEnablement.keySet().stream()
                    .filter(key -> !PUBLISHER_TYPE_PROPERTY_MAP.containsKey(key))
                    .toList();
            if (!unsupportedKeys.isEmpty()) {
                throw new MoesifConfigurationManagementClientException(
                        ErrorMessages.ERROR_INVALID_PUBLISHER_TYPE.getCode(),
                        String.format(ErrorMessages.ERROR_INVALID_PUBLISHER_TYPE.getMessage(),
                                unsupportedKeys, PUBLISHER_TYPE_PROPERTY_MAP.keySet()),
                        ErrorMessages.ERROR_INVALID_PUBLISHER_TYPE.getDescription());
            }
        }

        Optional<Resource> existingResource =
                getPublisherResource(MoesifConfigurationConstants.AUTH_PUBLISHER_RESOURCE_NAME);
        if (existingResource.isEmpty()) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getCode(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getMessage(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getDescription());
        }

        for (Map.Entry<String, String> entry : PUBLISHER_RESOURCE_MAP.entrySet()) {
            String typeKey = entry.getKey();
            String resourceName = entry.getValue();
            String streamName = PUBLISHER_STREAM_MAP.get(typeKey);
            MoesifPublisherDTO dto = buildPublisherDTOFromConfig(apiKeyValue, resourceName, streamName);
            Resource resource = buildResourceFromMoesifPublisher(dto);
            try {
                upsertResource(resource);
                reDeployEventPublisherConfiguration(resource);
            } catch (ConfigurationManagementException e) {
                throw handleConfigurationMgtException(e,
                        String.format(ErrorMessages.ERROR_UPDATING_PUBLISHER.getMessage(), resourceName));
            }
        }

        Map<String, Boolean> resolved = eventPublisherEnablement != null ?
                eventPublisherEnablement : Collections.emptyMap();
        updateAllGovernanceConfigs(resolved);

        MoesifPublisherDTO result = new MoesifPublisherDTO();
        result.setName(MOESIF_PUBLISHER_CANONICAL_NAME);
        result.setPublisherTypes(eventPublisherEnablement);
        return result;
    }

    @Override
    public void deleteMoesifPublisher() throws MoesifConfigurationManagementException {

        validateIfMoesifEnabled();
        Optional<Resource> resourceOptional =
                getPublisherResource(MoesifConfigurationConstants.AUTH_PUBLISHER_RESOURCE_NAME);
        if (resourceOptional.isEmpty()) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getCode(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getMessage(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getDescription());
        }

        try {
            for (String resourceName : PUBLISHER_RESOURCE_MAP.values()) {
                deleteResourceIfExists(resourceName);
            }
        } catch (ConfigurationManagementException e) {
            throw handleConfigurationMgtException(e,
                    String.format(ErrorMessages.ERROR_DELETING_PUBLISHER.getMessage(),
                            MOESIF_PUBLISHER_CANONICAL_NAME));
        }

        try {
            MoesifSecretProcessor.deleteSecrets(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER,
                    API_KEY_AUTH_TYPE, API_KEY_VALUE);
        } catch (SecretManagementException e) {
            log.error(String.format(ErrorMessages.ERROR_DELETING_API_KEY_SECRET.getMessage(),
                    MOESIF_PUBLISHER_CANONICAL_NAME), e);
        }

        updateAllGovernanceConfigs(Collections.emptyMap());
    }

    /**
     * Reads shared Moesif publisher configuration from the TOML file via
     * {@link IdentityUtil#getProperty(String)} and builds a {@link MoesifPublisherDTO}
     * for the given publisher resource name and its corresponding event stream.
     *
     * @param apiKeyValue  the Moesif API key to store.
     * @param resourceName the IS Analytics event publisher resource name for this publisher.
     * @param streamName   the IS Analytics event stream name this publisher subscribes to.
     */
    private MoesifPublisherDTO buildPublisherDTOFromConfig(String apiKeyValue, String resourceName, String streamName)
            throws MoesifConfigurationManagementServerException {

        String providerURL = IdentityUtil.getProperty(MoesifConfigurationConstants.PROVIDER_URL_CONFIG);
        String authType = IdentityUtil.getProperty(MoesifConfigurationConstants.AUTH_TYPE_CONFIG);
        String apiKeyHeader = IdentityUtil.getProperty(MoesifConfigurationConstants.API_KEY_HEADER_CONFIG);
        String streamVersion = IdentityUtil.getProperty(MoesifConfigurationConstants.STREAM_VERSION_CONFIG);
        String inlineBody = IdentityUtil.getProperty(MoesifConfigurationConstants.INLINE_BODY_CONFIG);

        if (StringUtils.isBlank(providerURL)) {
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_MISSING_PROVIDER_URL.getCode(),
                    ErrorMessages.ERROR_MISSING_PROVIDER_URL.getMessage(),
                    ErrorMessages.ERROR_MISSING_PROVIDER_URL.getDescription());
        }
        if (StringUtils.isBlank(authType)) {
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_MISSING_AUTH_TYPE.getCode(),
                    ErrorMessages.ERROR_MISSING_AUTH_TYPE.getMessage(),
                    ErrorMessages.ERROR_MISSING_AUTH_TYPE.getDescription());
        }
        if (StringUtils.isBlank(apiKeyHeader)) {
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_MISSING_API_KEY_HEADER.getCode(),
                    ErrorMessages.ERROR_MISSING_API_KEY_HEADER.getMessage(),
                    ErrorMessages.ERROR_MISSING_API_KEY_HEADER.getDescription());
        }
        if (StringUtils.isBlank(streamVersion)) {
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_MISSING_STREAM_VERSION.getCode(),
                    ErrorMessages.ERROR_MISSING_STREAM_VERSION.getMessage(),
                    ErrorMessages.ERROR_MISSING_STREAM_VERSION.getDescription());
        }

        MoesifPublisherDTO dto = new MoesifPublisherDTO();
        dto.setName(resourceName);
        dto.setProviderURL(providerURL);
        dto.setAuthType(authType);
        dto.setStreamName(streamName);
        dto.setStreamVersion(streamVersion);
        dto.setInlineBody(inlineBody == null ? "" : inlineBody);
        dto.setSecretProvider(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER);
        dto.getProperties().put(API_KEY_HEADER, apiKeyHeader);
        dto.getProperties().put(API_KEY_VALUE, apiKeyValue);
        return dto;
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
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_GENERATING_PUBLISHER_XML.getCode(),
                    ErrorMessages.ERROR_GENERATING_PUBLISHER_XML.getMessage(),
                    e.getMessage(), e);
        } catch (TransformerException e) {
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_TRANSFORMING_PUBLISHER_XML.getCode(),
                    ErrorMessages.ERROR_TRANSFORMING_PUBLISHER_XML.getMessage(),
                    e.getMessage(), e);
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
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_ENCRYPTING_API_KEY.getCode(),
                    ErrorMessages.ERROR_ENCRYPTING_API_KEY.getMessage(),
                    e.getMessage(), e);
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

    /**
     * Upserts a publisher resource: replaces it if it already exists, adds it otherwise.
     * This allows update operations to work even when only a subset of publisher resources
     * was previously created.
     */
    private void upsertResource(Resource resource) throws ConfigurationManagementException {

        try {
            MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                    .replaceResource(MOESIF_PUBLISHER_RESOURCE_TYPE, resource);
        } catch (ConfigurationManagementClientException e) {
            if (RESOURCE_NOT_EXISTS_ERROR_CODE.equals(e.getErrorCode()) ||
                    ERROR_CODE_RESOURCE_DOES_NOT_EXISTS.getCode().equals(e.getErrorCode())) {
                MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                        .addResource(MOESIF_PUBLISHER_RESOURCE_TYPE, resource);
            } else {
                throw e;
            }
        }
    }

    /**
     * Deletes a publisher resource, silently skipping it if it does not exist.
     * This prevents delete failures when only a subset of publisher resources was created.
     */
    private void deleteResourceIfExists(String resourceName) throws ConfigurationManagementException {

        try {
            MoesifConfigurationDataHolder.getInstance().getConfigurationManager()
                    .deleteResource(MOESIF_PUBLISHER_RESOURCE_TYPE, resourceName);
        } catch (ConfigurationManagementClientException e) {
            if (RESOURCE_NOT_EXISTS_ERROR_CODE.equals(e.getErrorCode()) ||
                    ERROR_CODE_RESOURCE_DOES_NOT_EXISTS.getCode().equals(e.getErrorCode())) {
                log.debug(String.format("Publisher resource '%s' not found during delete — skipping.", resourceName));
            } else {
                throw e;
            }
        }
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
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_RETRIEVING_PUBLISHER_RESOURCE.getCode(),
                    ErrorMessages.ERROR_RETRIEVING_PUBLISHER_RESOURCE.getMessage(),
                    e.getMessage(), e);
        } catch (ConfigurationManagementException e) {
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_RETRIEVING_PUBLISHER_RESOURCE.getCode(),
                    ErrorMessages.ERROR_RETRIEVING_PUBLISHER_RESOURCE.getMessage(),
                    e.getMessage(), e);
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
            log.warn(String.format(ErrorMessages.ERROR_REDEPLOYING_PUBLISHER_CONFIG.getMessage(), e.getMessage()));
        }
    }

    private boolean isResourceTypeNotExistsError(ConfigurationManagementException e) {

        return e instanceof ConfigurationManagementClientException &&
                ERROR_CODE_RESOURCE_TYPE_DOES_NOT_EXISTS.getCode().equals(e.getErrorCode());
    }

    private MoesifConfigurationManagementException handleConfigurationMgtException(
            ConfigurationManagementException e, String message) {

        if (e instanceof ConfigurationManagementClientException) {
            return new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_CONFIGURATION_MANAGEMENT_CLIENT.getCode(),
                    message, e.getMessage());
        }
        return new MoesifConfigurationManagementServerException(
                ErrorMessages.ERROR_CONFIGURATION_MANAGEMENT_SERVER.getCode(),
                message, e.getMessage(), e);
    }

    private void updateAllGovernanceConfigs(Map<String, Boolean> publisherTypes) {

        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        Map<String, String> configProperties = new HashMap<>();

        // Iterate all known publisher type keys — keys absent from publisherTypes default to false.
        for (Map.Entry<String, String> entry : PUBLISHER_TYPE_PROPERTY_MAP.entrySet()) {
            boolean enabled = Boolean.TRUE.equals(publisherTypes.get(entry.getKey()));
            configProperties.put(entry.getValue(), String.valueOf(enabled));
        }
        try {
            MoesifConfigurationDataHolder.getInstance().getIdentityGovernanceService()
                    .updateConfiguration(tenantDomain, configProperties);
        } catch (IdentityGovernanceException e) {
            log.error(String.format(ErrorMessages.ERROR_UPDATING_GOVERNANCE_CONFIG.getMessage(), tenantDomain), e);
        }
    }

    private void populateGovernanceConfigsIntoDto(MoesifPublisherDTO dto) {

        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        try {
            String[] propertyNames = PUBLISHER_TYPE_PROPERTY_MAP.values().toArray(new String[0]);
            Property[] properties =
                    MoesifConfigurationDataHolder.getInstance().getIdentityGovernanceService()
                            .getConfiguration(propertyNames, tenantDomain);
            if (properties == null) {
                return;
            }
            // Build a reverse map: governance property name → publisher type key
            Map<String, Boolean> publisherTypes = getPublisherTypes(properties);
            dto.setPublisherTypes(publisherTypes);
        } catch (IdentityGovernanceException e) {
            log.error(String.format(ErrorMessages.ERROR_READING_GOVERNANCE_CONFIG.getMessage(), tenantDomain), e);
        }
    }

    private static Map<String, Boolean> getPublisherTypes(Property[] properties) {
        Map<String, String> propertyToTypeKey = new HashMap<>();
        for (Map.Entry<String, String> entry : PUBLISHER_TYPE_PROPERTY_MAP.entrySet()) {
            propertyToTypeKey.put(entry.getValue(), entry.getKey());
        }
        Map<String, Boolean> publisherTypes = new LinkedHashMap<>();
        for (Property prop : properties) {
            String typeKey = propertyToTypeKey.get(prop.getName());
            if (typeKey != null) {
                publisherTypes.put(typeKey, Boolean.parseBoolean(prop.getValue()));
            }
        }
        return publisherTypes;
    }


    private void validateIfMoesifEnabled() throws MoesifConfigurationManagementServerException {

        String enabled = IdentityUtil.getProperty(MoesifConfigurationConstants.ENABLED_CONFIG);
        if (!Boolean.parseBoolean(enabled)) {
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_MOESIF_DISABLED.getCode(),
                    ErrorMessages.ERROR_MOESIF_DISABLED.getMessage(),
                    ErrorMessages.ERROR_MOESIF_DISABLED.getDescription());
        }
    }
}
