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
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.data.publisher.oauth.OAuthDataPublisherUtils;
import org.wso2.carbon.identity.data.publisher.oauth.listener.OAuthTokenIssuanceDASDataPublisher;
import org.wso2.carbon.identity.data.publisher.oauth.model.TokenData;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;

import java.util.Arrays;

import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_TOKEN_ISSUANCE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TOKEN_ISSUANCE_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.TOKEN_ISSUANCE_STREAM_NAME;

/**
 * OAuth event interceptor that publishes token issuance events to Moesif via the HTTP output event adapter.
 *
 * <p>Extends {@link OAuthTokenIssuanceDASDataPublisher} so that the parent class handles all
 * {@code onPostTokenIssue} / {@code onPostTokenRenewal} callbacks and assembles the {@link TokenData}
 * object (covering all grant types, not just client_credentials). This class overrides only
 * {@link #publishTokenIssueEvent(TokenData)} to redirect the already-built payload to Moesif
 * instead of the standard DAS stream.</p>
 *
 * <p>The tenant flow is started with the root/parent organisation tenant domain so that the Moesif
 * event publisher deployed there is selected — consistent with all other Moesif data publish handlers.</p>
 */
public class MoesifOAuthTokenIssuanceDataPublishHandler extends OAuthTokenIssuanceDASDataPublisher {

    private static final Log LOG = LogFactory.getLog(MoesifOAuthTokenIssuanceDataPublishHandler.class);

    @Override
    public String getName() {

        return TOKEN_ISSUANCE_PUBLISHER_NAME;
    }

    /**
     * Publishes the pre-built token-issuance payload to Moesif.
     *
     * <p>The payload array is built by {@link OAuthDataPublisherUtils#buildTokenIssuancePayload(TokenData)}
     * — the same shared method used by the DAS publisher so both destinations always receive identical data.
     * Publishing is skipped when the Moesif token-issuance publisher is not enabled for the tenant.</p>
     *
     * @param tokenData Populated token data object, assembled by the parent class interceptor callbacks.
     */
    @Override
    public void publishTokenIssueEvent(TokenData tokenData) {

        String tenantDomain = tokenData.getTenantDomain();
        if (!isEnabled(tenantDomain)) {
            return;
        }

        // Resolve the root/parent org tenant domain for the tenant flow.
        String rootTenantDomain = tenantDomain;
        if (StringUtils.isNotBlank(tenantDomain) &&
                !MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
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

        // Resolve the Moesif company/org UUID from the root tenant domain.
        String orgUuid = NOT_AVAILABLE;
        if (StringUtils.isNotBlank(rootTenantDomain)) {
            try {
                String resolved = MoesifHandlerDataHolder.getInstance()
                        .getOrganizationManager()
                        .resolveOrganizationId(rootTenantDomain);
                if (StringUtils.isNotBlank(resolved)) {
                    orgUuid = resolved;
                }
            } catch (OrganizationManagementException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "Could not resolve organisation ID for tenant '%s'; using NOT_AVAILABLE.",
                            rootTenantDomain), e);
                }
            }
        }

        String userId = StringUtils.defaultIfBlank(tokenData.getUser(), NOT_AVAILABLE);

        Object[] metaData = MoesifHandlerUtils.getMetaDataArray(
                orgUuid, ACTION_NAME_TOKEN_ISSUANCE, userId, NOT_AVAILABLE);

        // Extract the three additional org-context fields from the TokenData parameters.
        String accessingOrganizationId = NOT_AVAILABLE;
        Object rawAccessingOrgId = tokenData.getParameter(
                IdentityEventConstants.EventProperty.ACCESSING_ORGANIZATION_ID);
        if (rawAccessingOrgId instanceof String && StringUtils.isNotBlank((String) rawAccessingOrgId)) {
            accessingOrganizationId = (String) rawAccessingOrgId;
        }

        String appResidentTenantId = NOT_AVAILABLE;
        Object rawAppResidentTenantId = tokenData.getParameter(
                IdentityEventConstants.EventProperty.APP_RESIDENT_TENANT_ID);
        if (rawAppResidentTenantId instanceof String && StringUtils.isNotBlank((String) rawAppResidentTenantId)) {
            appResidentTenantId = (String) rawAppResidentTenantId;
        }

        // Build the same payload as the DAS publisher via the shared util, then append the 3 new fields.
        Object[] basePayload = OAuthDataPublisherUtils.buildTokenIssuancePayload(tokenData);
        Object[] payloadData = Arrays.copyOf(basePayload, basePayload.length + 3);
        payloadData[basePayload.length] = accessingOrganizationId;
        payloadData[basePayload.length + 1] = appResidentTenantId;
        payloadData[basePayload.length + 2] = StringUtils.defaultIfBlank(rootTenantDomain, NOT_AVAILABLE);

        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                new org.wso2.carbon.databridge.commons.Event(
                        TOKEN_ISSUANCE_STREAM_NAME, System.currentTimeMillis(), metaData, null, payloadData);

        try {
            FrameworkUtils.startTenantFlow(StringUtils.defaultIfBlank(rootTenantDomain,
                    MultitenantConstants.SUPER_TENANT_DOMAIN_NAME));
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
        } finally {
            FrameworkUtils.endTenantFlow();
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Published Moesif OAuth token issuance event for tenant: %s", tenantDomain));
        }
    }

    /**
     * Returns {@code true} when the Moesif token-issuance publisher is enabled for the given tenant.
     * Checks the governance property directly — unlike {@code AbstractEventHandler}-based handlers
     * there is no deployment.toml module config available for {@code OAuthEventInterceptor} subclasses.
     *
     * @param tenantDomain The tenant domain of the token request.
     */
    private boolean isEnabled(String tenantDomain) {

        if (StringUtils.isBlank(tenantDomain)) {
            return false;
        }
        return MoesifHandlerUtils.isHandlerEnabledForPrimaryTenant(tenantDomain,
                MoesifCommonConstants.MOESIF_TOKEN_ISSUANCE_PUBLISHER_ENABLED_PROPERTY);
    }
}

