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
import org.wso2.carbon.identity.moesif.configuration.util.MoesifSecretProcessor;
import org.wso2.carbon.identity.secret.mgt.core.exception.SecretManagementException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.carbon.identity.moesif.configuration.constant.MoesifConfigurationConstants.*;

/**
 * Implementation of {@link MoesifConfigurationManagementService}.
 *
 * <p>The Moesif event publishers are deployed by default for every organisation from the templated
 * event-publisher definitions shipped with the server feature, so this service no longer creates,
 * stores or (re)deploys per-tenant publisher configurations. It now manages only the two pieces of
 * per-tenant state Moesif analytics actually needs:</p>
 * <ul>
 *   <li>the Moesif collector key, stored in the secret store under the
 *       {@link MoesifConfigurationConstants#MOESIF_SECRET_PROVIDER} namespace, and</li>
 *   <li>the per-event-type enablement governance toggles.</li>
 * </ul>
 */
public class MoesifConfigurationManagementServiceImpl implements MoesifConfigurationManagementService {

    private static final Log log = LogFactory.getLog(MoesifConfigurationManagementServiceImpl.class);

    private static final String MOESIF_PUBLISHER_CANONICAL_NAME = "moesif-publisher";

    private static final String API_KEY_VALUE = "apiKeyValue";
    private static final String API_KEY_HEADER = "apiKeyHeader";
    private static final String API_KEY_AUTH_TYPE = "API_KEY";

    /**
     * Ordered map of publisher type key → governance property constant.
     */
    private static final Map<String, String> PUBLISHER_TYPE_PROPERTY_MAP;

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
        props.put(MOESIF_TOKEN_ISSUANCE_PUBLISHER,
                MoesifCommonConstants.MOESIF_TOKEN_ISSUANCE_PUBLISHER_ENABLED_PROPERTY);
        PUBLISHER_TYPE_PROPERTY_MAP = Collections.unmodifiableMap(props);
    }

    @Override
    public MoesifPublisherDTO addMoesifPublisher(String apiKeyValue, Map<String, Boolean> eventPublisherEnablement,
                                                 boolean enableAllPublishers)
            throws MoesifConfigurationManagementException {

        validateIfMoesifEnabled();
        if (StringUtils.isBlank(apiKeyValue)) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getCode(),
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getMessage(),
                    ErrorMessages.ERROR_API_KEY_REQUIRED.getDescription());
        }
        validatePublisherTypes(eventPublisherEnablement);

        if (isApiKeyConfigured()) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_PUBLISHER_ALREADY_EXISTS.getCode(),
                    ErrorMessages.ERROR_PUBLISHER_ALREADY_EXISTS.getMessage(),
                    ErrorMessages.ERROR_PUBLISHER_ALREADY_EXISTS.getDescription());
        }

        Map<String, Boolean> resolved = eventPublisherEnablement != null ?
                eventPublisherEnablement : Collections.emptyMap();

        storeApiKey(apiKeyValue);
        updateAllGovernanceConfigs(resolved, enableAllPublishers);

        return buildResultDTO(eventPublisherEnablement, enableAllPublishers);
    }

    @Override
    public MoesifPublisherDTO getMoesifPublisher() throws MoesifConfigurationManagementException {

        validateIfMoesifEnabled();
        if (!isApiKeyConfigured()) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getCode(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getMessage(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getDescription());
        }
        return buildCurrentPublisherDTO();
    }

    @Override
    public List<MoesifPublisherDTO> getMoesifPublishers() throws MoesifConfigurationManagementException {

        List<MoesifPublisherDTO> publishers = new ArrayList<>();
        if (isApiKeyConfigured()) {
            publishers.add(buildCurrentPublisherDTO());
        }
        return publishers;
    }

    @Override
    public MoesifPublisherDTO updateMoesifPublisher(String apiKeyValue, Map<String, Boolean> eventPublisherEnablement,
                                                    boolean enableAllPublishers)
            throws MoesifConfigurationManagementException {

        validateIfMoesifEnabled();
        validatePublisherTypes(eventPublisherEnablement);

        if (!isApiKeyConfigured()) {
            throw new MoesifConfigurationManagementClientException(
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getCode(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getMessage(),
                    ErrorMessages.ERROR_PUBLISHER_NOT_FOUND.getDescription());
        }

        Map<String, Boolean> resolved = eventPublisherEnablement != null ?
                eventPublisherEnablement : Collections.emptyMap();

        // A blank key on update means "keep the existing collector key" — only the governance
        // toggles are being changed.
        if (StringUtils.isNotBlank(apiKeyValue)) {
            storeApiKey(apiKeyValue);
        }
        updateAllGovernanceConfigs(resolved, enableAllPublishers);

        return buildResultDTO(eventPublisherEnablement, enableAllPublishers);
    }

    @Override
    public void deleteMoesifPublisher() throws MoesifConfigurationManagementException {

        validateIfMoesifEnabled();

        try {
            MoesifSecretProcessor.deleteSecrets(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER,
                    API_KEY_AUTH_TYPE, API_KEY_VALUE);
        } catch (SecretManagementException e) {
            log.error(String.format(ErrorMessages.ERROR_DELETING_API_KEY_SECRET.getMessage(),
                    MOESIF_PUBLISHER_CANONICAL_NAME), e);
        }

        updateAllGovernanceConfigs(Collections.emptyMap(), false);
    }

    /**
     * Builds the DTO describing the Moesif configuration currently in effect for the tenant: the shared
     * (server-level) publisher settings plus the per-event-type enablement governance toggles. The
     * collector key is never returned.
     */
    private MoesifPublisherDTO buildCurrentPublisherDTO() {

        MoesifPublisherDTO dto = new MoesifPublisherDTO();
        dto.setName(MOESIF_PUBLISHER_CANONICAL_NAME);
        populateSharedConfigIntoDto(dto);
        populateGovernanceConfigsIntoDto(dto);
        return dto;
    }

    private MoesifPublisherDTO buildResultDTO(Map<String, Boolean> eventPublisherEnablement,
                                              boolean enableAllPublishers) {

        MoesifPublisherDTO result = new MoesifPublisherDTO();
        result.setName(MOESIF_PUBLISHER_CANONICAL_NAME);
        result.setPublisherTypes(eventPublisherEnablement);
        result.setEnableAllPublishers(enableAllPublishers);
        return result;
    }

    /**
     * Stores (or updates) the Moesif collector key in the secret store. The event publishers read it
     * from the same secret namespace at publish time via their {@code http.secret.provider} property.
     */
    private void storeApiKey(String apiKeyValue) throws MoesifConfigurationManagementServerException {

        try {
            MoesifSecretProcessor.encryptSecret(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER,
                    API_KEY_AUTH_TYPE, API_KEY_VALUE, apiKeyValue);
        } catch (SecretManagementException e) {
            throw new MoesifConfigurationManagementServerException(
                    ErrorMessages.ERROR_ENCRYPTING_API_KEY.getCode(),
                    ErrorMessages.ERROR_ENCRYPTING_API_KEY.getMessage(),
                    e.getMessage(), e);
        }
    }

    private boolean isApiKeyConfigured() {

        return MoesifSecretProcessor.isSecretConfigured(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER,
                API_KEY_AUTH_TYPE, API_KEY_VALUE);
    }

    private void validatePublisherTypes(Map<String, Boolean> eventPublisherEnablement)
            throws MoesifConfigurationManagementClientException {

        if (eventPublisherEnablement == null || eventPublisherEnablement.isEmpty()) {
            return;
        }
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

    /**
     * Populates the shared, server-level publisher settings (read from deployment configuration) into
     * the DTO. These are no longer stored per tenant; they reflect the values the templated event
     * publishers are rendered with. Best-effort: blank values are simply omitted.
     */
    private void populateSharedConfigIntoDto(MoesifPublisherDTO dto) {

        String providerURL = IdentityUtil.getProperty(MoesifConfigurationConstants.PROVIDER_URL_CONFIG);
        String authType = IdentityUtil.getProperty(MoesifConfigurationConstants.AUTH_TYPE_CONFIG);
        String apiKeyHeader = IdentityUtil.getProperty(MoesifConfigurationConstants.API_KEY_HEADER_CONFIG);
        String streamVersion = IdentityUtil.getProperty(MoesifConfigurationConstants.STREAM_VERSION_CONFIG);
        String inlineBody = IdentityUtil.getProperty(MoesifConfigurationConstants.INLINE_BODY_CONFIG);

        if (StringUtils.isNotBlank(providerURL)) {
            dto.setProviderURL(providerURL);
        }
        if (StringUtils.isNotBlank(authType)) {
            dto.setAuthType(authType);
        }
        if (StringUtils.isNotBlank(streamVersion)) {
            dto.setStreamVersion(streamVersion);
        }
        if (StringUtils.isNotBlank(inlineBody)) {
            dto.setInlineBody(inlineBody);
        }
        dto.setSecretProvider(MoesifConfigurationConstants.MOESIF_SECRET_PROVIDER);
        if (StringUtils.isNotBlank(apiKeyHeader)) {
            dto.getProperties().put(API_KEY_HEADER, apiKeyHeader);
        }
    }

    private void updateAllGovernanceConfigs(Map<String, Boolean> publisherTypes, boolean enableAllPublishers) {

        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        Map<String, String> configProperties = new HashMap<>();

        // Master toggle: when on, all supported publishers are enabled regardless of the individual map.
        configProperties.put(MoesifCommonConstants.MOESIF_ENABLE_ALL_PUBLISHERS_PROPERTY,
                String.valueOf(enableAllPublishers));
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
            String[] propertyNames = governancePropertyNames();
            Property[] properties =
                    MoesifConfigurationDataHolder.getInstance().getIdentityGovernanceService()
                            .getConfiguration(propertyNames, tenantDomain);
            if (properties == null) {
                return;
            }
            boolean enableAll = false;
            for (Property property : properties) {
                if (MoesifCommonConstants.MOESIF_ENABLE_ALL_PUBLISHERS_PROPERTY.equals(property.getName())) {
                    enableAll = Boolean.parseBoolean(property.getValue());
                    break;
                }
            }
            dto.setEnableAllPublishers(enableAll);
            if (enableAll) {
                // Master toggle on: report every supported publisher as enabled, ignoring individual toggles.
                Map<String, Boolean> allEnabled = new LinkedHashMap<>();
                for (String typeKey : PUBLISHER_TYPE_PROPERTY_MAP.keySet()) {
                    allEnabled.put(typeKey, Boolean.TRUE);
                }
                dto.setPublisherTypes(allEnabled);
            } else {
                dto.setPublisherTypes(getPublisherTypes(properties));
            }
        } catch (IdentityGovernanceException e) {
            log.error(String.format(ErrorMessages.ERROR_READING_GOVERNANCE_CONFIG.getMessage(), tenantDomain), e);
        }
    }

    /**
     * The governance property names read for a GET: the master "enable all" toggle plus every
     * per-publisher toggle.
     */
    private static String[] governancePropertyNames() {

        List<String> names = new ArrayList<>();
        names.add(MoesifCommonConstants.MOESIF_ENABLE_ALL_PUBLISHERS_PROPERTY);
        names.addAll(PUBLISHER_TYPE_PROPERTY_MAP.values());
        return names.toArray(new String[0]);
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
