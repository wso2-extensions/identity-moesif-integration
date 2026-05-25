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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_TOKEN_ISSUANCE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TOKEN_ISSUANCE_STREAM_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TOKEN_ISSUANCE_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TOKEN_ISSUANCE_PUBLISHER_NAME;

/**
 * Event handler that publishes OAuth token-issuance events to Moesif via the HTTP output event adapter.
 *
 * <p>Subscribes to {@link IdentityEventConstants.Event#POST_ISSUE_ACCESS_TOKEN_V2} which is fired
 * from {@code AccessTokenEventUtil#publishTokenIssueEvent(...)} on every successful access-token
 * issuance in the OAuth bundle.</p>
 *
 * <p>This handler emits the merged data set of two pre-existing publishers:
 * <ul>
 *     <li>{@code OAuth2AccessTokenIssueEventPublisher} (asgardeo-metering) — tenant/grant/IAT/org context fields.</li>
 *     <li>{@code OAuthTokenIssuanceDASDataPublisher} (identity-data-publisher-oauth) — scopes,
 *         token validity, remote IP, user attributes.</li>
 * </ul>
 * It also carries the {@code EXISTING_TOKEN_USED} flag through to the payload so consumers can
 * tell newly-minted tokens apart from reissues of existing tokens — unlike the metering publisher,
 * which silently drops reissues.</p>
 *
 * <p>All grant types are accepted (the metering publisher restricts to {@code client_credentials}).
 * Sub-organisation requests are resolved to the root organisation tenant so the Moesif HTTP
 * publisher deployed on the root tenant is used, consistent with the other Moesif handlers.</p>
 */
public class MoesifOAuthTokenIssuanceDataPublishHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(MoesifOAuthTokenIssuanceDataPublishHandler.class);

    /**
     * Event property keys not yet exposed via {@code IdentityEventConstants.EventProperty}.
     * Mirror the string values declared in {@code OAuthConstants.EventProperty} so we don't
     * have to introduce a new bundle dependency just to read these fields.
     */
    private static final String EVENT_PROP_AUTHORIZED_SCOPES = "AUTHORIZED_SCOPES";
    private static final String EVENT_PROP_UNAUTHORIZED_SCOPES = "UNAUTHORIZED_SCOPES";
    private static final String EVENT_PROP_ACCESS_TOKEN_VALIDITY_MILLIS = "ACCESS_TOKEN_VALIDITY_MILLIS";
    private static final String EVENT_PROP_REFRESH_TOKEN_VALIDITY_MILLIS = "REFRESH_TOKEN_VALIDITY_MILLIS";
    private static final String EVENT_PROP_REMOTE_IP = "REMOTE_IP";

    private static final DateTimeFormatter IAT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

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
        if (StringUtils.isNotBlank(tenantDomain)
                && !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)
                && rootTenantDomain.equals(tenantDomain)) {
            // The event publisher should have populated ROOT_TENANT_DOMAIN, but recompute defensively.
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

        // The REMOTE_IP event property is populated by AccessTokenEventUtil from
        // tokenReqDTO.getHttpServletRequestWrapper().getRemoteAddr(). When absent we keep
        // NOT_AVAILABLE — the HTTP adapter omits the field from request.ipAddress in that case.
        String ipAddress = StringUtils.defaultIfBlank(
                asString(properties.get(EVENT_PROP_REMOTE_IP)), NOT_AVAILABLE);

        Object[] metaData = MoesifHandlerUtils.getMetaDataArray(
                companyId, ACTION_NAME_TOKEN_ISSUANCE, userId, NOT_AVAILABLE, ipAddress);

        boolean existingTokenUsed = asBoolean(
                properties.get(IdentityEventConstants.EventProperty.EXISTING_TOKEN_USED));
        boolean subOrgRequest = StringUtils.isNotBlank(tenantDomain)
                && StringUtils.isNotBlank(rootTenantDomain)
                && !tenantDomain.equals(rootTenantDomain);

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

        // 1-9: metering fields (preserved from OAuth2AccessTokenIssueEventPublisher).
        payload[0] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN));
        payload[1] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.CLIENT_ID));
        payload[2] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.GRANT_TYPE));
        payload[3] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.USER_TYPE));
        payload[4] = formatIat(p.get(IdentityEventConstants.EventProperty.IAT));
        payload[5] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.ISSUER_ORGANIZATION_ID));
        payload[6] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.ACCESSING_ORGANIZATION_ID));
        payload[7] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.APP_RESIDENT_TENANT_ID));
        payload[8] = StringUtils.defaultIfBlank(rootTenantDomain, NOT_AVAILABLE);

        // 10-13: user / token-identity fields from oauth-publisher.
        payload[9] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.USER_NAME));
        payload[10] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.USER_ID));
        payload[11] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.USER_STORE_DOMAIN));
        payload[12] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.TOKEN_ID));

        // 14-15: scopes.
        payload[13] = stringOrNotAvailable(p.get(EVENT_PROP_AUTHORIZED_SCOPES));
        payload[14] = stringOrNotAvailable(p.get(EVENT_PROP_UNAUTHORIZED_SCOPES));

        // 16-17: validity periods (LONG on the stream).
        payload[15] = asLong(p.get(EVENT_PROP_ACCESS_TOKEN_VALIDITY_MILLIS));
        payload[16] = asLong(p.get(EVENT_PROP_REFRESH_TOKEN_VALIDITY_MILLIS));

        // 18: remote IP.
        payload[17] = stringOrNotAvailable(p.get(EVENT_PROP_REMOTE_IP));

        // 19-20: flags.
        payload[18] = existingTokenUsed;
        payload[19] = subOrgRequest;

        // 21: app-resident org UUID (mirrors APP_RESIDENT_TENANT_ID — same pattern as
        // service-provider-residing-org-id in the analytics login payload).
        payload[20] = StringUtils.defaultIfBlank(appResidentOrgUuid, NOT_AVAILABLE);

        // 22-23: error fields. Populated when tokenRespDTO carries an error; empty/NOT_AVAILABLE
        // on the happy path.
        payload[21] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.ERROR_CODE));
        payload[22] = stringOrNotAvailable(p.get(IdentityEventConstants.EventProperty.ERROR_MESSAGE));

        // 24: publishing timestamp (stays last).
        payload[23] = Instant.now().toString();

        return payload;
    }

    private String resolveAppResidentOrgUuid(String clientId, String tenantDomain,
                                             Object appResidentTenantIdRaw) {

        // Path 1: fragment app — main definition is in the primary org.
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

        // Path 2: non-fragment app — use APP_RESIDENT_TENANT_ID.
        int appResidentTenantId = asInt(appResidentTenantIdRaw);
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

    private static int asInt(Object value) {

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

    private static String stringOrNotAvailable(Object value) {

        if (value == null) {
            return NOT_AVAILABLE;
        }
        String s = value.toString();
        return StringUtils.isBlank(s) ? NOT_AVAILABLE : s;
    }

    private static boolean asBoolean(Object value) {

        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    private static String formatIat(Object iat) {

        if (iat instanceof Long) {
            return IAT_FORMATTER.format(Instant.ofEpochMilli((Long) iat));
        }
        if (iat instanceof Number) {
            return IAT_FORMATTER.format(Instant.ofEpochMilli(((Number) iat).longValue()));
        }
        if (iat instanceof String && StringUtils.isNotBlank((String) iat)) {
            try {
                return IAT_FORMATTER.format(Instant.ofEpochMilli(Long.parseLong((String) iat)));
            } catch (NumberFormatException ignored) {
                // Fall through to NOT_AVAILABLE.
            }
        }
        return NOT_AVAILABLE;
    }

    private static long asLong(Object value) {

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.isNotBlank((String) value)) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 0L;
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
