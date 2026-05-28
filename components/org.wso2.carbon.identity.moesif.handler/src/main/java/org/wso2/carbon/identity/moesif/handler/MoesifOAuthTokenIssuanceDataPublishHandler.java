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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;

import java.time.Instant;
import java.util.Map;

import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_TOKEN_ISSUANCE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TOKEN_ISSUANCE_STREAM_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TOKEN_ISSUANCE_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TOKEN_ISSUANCE_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils.asBoolean;
import static org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils.asLong;
import static org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils.getISOTimestamp;
import static org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils.getStringOrNotAvailable;

/**
 * Custom event handler that listens to OAuth token issuance events
 * and publishes relevant data to Moesif via the HTTP output event adapter.
 */
public class MoesifOAuthTokenIssuanceDataPublishHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(MoesifOAuthTokenIssuanceDataPublishHandler.class);
    private static final String EVENT_PROP_AUTHORIZED_SCOPES = "AUTHORIZED_SCOPES";
    private static final String EVENT_PROP_UNAUTHORIZED_SCOPES = "UNAUTHORIZED_SCOPES";
    private static final String EVENT_PROP_ACCESS_TOKEN_VALIDITY_MILLIS = "ACCESS_TOKEN_VALIDITY_MILLIS";
    private static final String EVENT_PROP_REFRESH_TOKEN_VALIDITY_MILLIS = "REFRESH_TOKEN_VALIDITY_MILLIS";
    private static final String EVENT_PROP_REMOTE_IP = "REMOTE_IP";

    @Override
    public String getName() {

        return TOKEN_ISSUANCE_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        if (!isEnabled()) {
            return;
        }

        if (event == null
                || !IdentityEventConstants.Event.POST_ISSUE_ACCESS_TOKEN_V2.equals(event.getEventName())) {
            return;
        }

        Map<String, Object> properties = event.getEventProperties();
        if (properties == null || properties.isEmpty()) {
            LOG.warn("Event properties are null or empty for the event: " + event.getEventName());
            return;
        }

        String tenantDomain = asString(properties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN));

        // Resolve root org tenant + Moesif company UUID. For super tenant the root is itself.
        String rootTenantDomain = StringUtils.defaultIfBlank(
                asString(properties.get(IdentityEventConstants.EventProperty.ROOT_TENANT_DOMAIN)), tenantDomain);

        if (StringUtils.isBlank(tenantDomain) && StringUtils.isBlank(rootTenantDomain)) {
            return;
        }

        if (StringUtils.isNotBlank(tenantDomain)
                && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)
                && rootTenantDomain.equals(tenantDomain)) {
            try {
                rootTenantDomain =
                        OrganizationManagementUtil.getRootOrgTenantDomainBySubOrgTenantDomain(tenantDomain);
            } catch (OrganizationManagementException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "Could not resolve root tenant for '%s'; using original.", tenantDomain), e);
                }
            }
        }

        String companyId = NOT_AVAILABLE;
        if (StringUtils.isNotBlank(rootTenantDomain)) {
            try {
                String resolved = MoesifHandlerDataHolder.getInstance()
                        .getOrganizationManager()
                        .resolveOrganizationId(rootTenantDomain);
                if (StringUtils.isNotBlank(resolved)) {
                    companyId = resolved;
                }
            } catch (OrganizationManagementException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "Could not resolve organisation ID for tenant '%s'; using NOT_AVAILABLE.",
                            rootTenantDomain), e);
                }
            }
        }

        String userId = StringUtils.defaultIfBlank(
                asString(properties.get(IdentityEventConstants.EventProperty.USER_ID)), NOT_AVAILABLE);

        String ipAddress = StringUtils.defaultIfBlank(
                asString(properties.get(EVENT_PROP_REMOTE_IP)), NOT_AVAILABLE);

        Object[] metaData = MoesifHandlerUtils.getMetaDataArray(
                companyId, ACTION_NAME_TOKEN_ISSUANCE, userId, NOT_AVAILABLE, ipAddress);

        boolean existingTokenUsed = asBoolean(properties.get(IdentityEventConstants.EventProperty.EXISTING_TOKEN_USED));
        boolean subOrgRequest = !StringUtils.equals(rootTenantDomain, tenantDomain);

        String clientId = asString(properties.get(IdentityEventConstants.EventProperty.CLIENT_ID));
        String appResidentOrgUuid = resolveAppResidentOrgUuid(
                clientId, tenantDomain,
                properties.get(IdentityEventConstants.EventProperty.APP_RESIDENT_TENANT_ID));

        Object[] payloadData = buildPayload(properties, rootTenantDomain, existingTokenUsed, subOrgRequest,
                appResidentOrgUuid);

        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                new org.wso2.carbon.databridge.commons.Event(
                        TOKEN_ISSUANCE_STREAM_NAME, System.currentTimeMillis(),
                        metaData, null, payloadData);

        try {
            FrameworkUtils.startTenantFlow(StringUtils.defaultIfBlank(rootTenantDomain,
                    MultitenantConstants.SUPER_TENANT_DOMAIN_NAME));
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
        } finally {
            FrameworkUtils.endTenantFlow();
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format(
                    "Published Moesif OAuth token-issuance event for tenant '%s' (root='%s', reissued=%s).",
                    tenantDomain, rootTenantDomain, existingTokenUsed));
        }
    }

    /**
     * Build the stream payload in the order declared by the
     * {@code MoesifOAuthTokenIssuanceData:2.0.0} stream definition.
     */
    private Object[] buildPayload(Map<String, Object> p, String rootTenantDomain,
                                  boolean existingTokenUsed, boolean subOrgRequest,
                                  String appResidentOrgUuid) {

        Object[] payload = new Object[24];

        payload[0] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN));
        payload[1] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.CLIENT_ID));
        payload[2] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.GRANT_TYPE));
        payload[3] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.USER_TYPE));
        payload[4] = getISOTimestamp(p.get(IdentityEventConstants.EventProperty.IAT));
        payload[5] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.ISSUER_ORGANIZATION_ID));
        payload[6] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.ACCESSING_ORGANIZATION_ID));
        payload[7] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.APP_RESIDENT_TENANT_ID));
        payload[8] = getStringOrNotAvailable(rootTenantDomain);

        payload[9] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.USER_NAME));
        payload[10] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.USER_ID));
        payload[11] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.USER_STORE_DOMAIN));
        payload[12] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.TOKEN_ID));

        payload[13] = getStringOrNotAvailable(p.get(EVENT_PROP_AUTHORIZED_SCOPES));
        payload[14] = getStringOrNotAvailable(p.get(EVENT_PROP_UNAUTHORIZED_SCOPES));

        payload[15] = asLong(p.get(EVENT_PROP_ACCESS_TOKEN_VALIDITY_MILLIS));
        payload[16] = asLong(p.get(EVENT_PROP_REFRESH_TOKEN_VALIDITY_MILLIS));
        payload[17] = getStringOrNotAvailable(p.get(EVENT_PROP_REMOTE_IP));

        payload[18] = existingTokenUsed;
        payload[19] = subOrgRequest;

        payload[20] = getStringOrNotAvailable(appResidentOrgUuid);

        payload[21] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.ERROR_CODE));
        payload[22] = getStringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.ERROR_MESSAGE));

        payload[23] = Instant.now().toString();

        return payload;
    }

    private String resolveAppResidentOrgUuid(String clientId, String tenantDomain,
                                             Object appResidentTenantIdRaw) {

        if (StringUtils.isNotBlank(clientId) && StringUtils.isNotBlank(tenantDomain)) {
            try {
                if (OAuth2Util.isFragmentApp(clientId, tenantDomain)) {
                    String primaryOrgId = MoesifHandlerDataHolder.getInstance()
                            .getOrganizationManager()
                            .getPrimaryOrganizationId(tenantDomain);
                    if (StringUtils.isNotBlank(primaryOrgId)) {
                        return primaryOrgId;
                    }
                }
            } catch (IdentityOAuth2Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "Could not determine if clientId '%s' (tenant '%s') is a fragment app; "
                                    + "falling back to APP_RESIDENT_TENANT_ID resolution.",
                            clientId, tenantDomain), e);
                }
            } catch (OrganizationManagementException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "Could not resolve primary organization for fragment app tenant '%s'; "
                                    + "falling back to APP_RESIDENT_TENANT_ID resolution.", tenantDomain), e);
                }
            }
        }

        int appResidentTenantId = getTenantId(appResidentTenantIdRaw);
        if (appResidentTenantId == -1) {
            return NOT_AVAILABLE;
        }
        try {
            String appResidentTenantDomain = IdentityTenantUtil.getTenantDomain(appResidentTenantId);
            if (StringUtils.isBlank(appResidentTenantDomain)) {
                return NOT_AVAILABLE;
            }
            String resolved = MoesifHandlerDataHolder.getInstance()
                    .getOrganizationManager()
                    .resolveOrganizationId(appResidentTenantDomain);
            return StringUtils.defaultIfBlank(resolved, NOT_AVAILABLE);
        } catch (OrganizationManagementException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format(
                        "Could not resolve org UUID for APP_RESIDENT_TENANT_ID '%d'; using NOT_AVAILABLE.",
                        appResidentTenantId), e);
            }
            return NOT_AVAILABLE;
        }
    }

    private static int getTenantId(Object value) {

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.isNotBlank((String) value)) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return -1;
    }

    private static String asString(Object value) {

        return value == null ? null : value.toString();
    }

    private boolean isEnabled() {

        if (this.configs.getModuleProperties() != null) {
            String handlerEnabled = this.configs.getModuleProperties()
                    .getProperty(TOKEN_ISSUANCE_PUBLISHER_ENABLED);
            if (Boolean.parseBoolean(handlerEnabled)) {
                String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
                return MoesifHandlerUtils.isHandlerEnabledForPrimaryTenant(tenantDomain,
                        MoesifCommonConstants.MOESIF_TOKEN_ISSUANCE_PUBLISHER_ENABLED_PROPERTY);
            }
        }
        return false;
    }
}
