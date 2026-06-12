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
import org.wso2.carbon.consent.mgt.core.ConsentManager;
import org.wso2.carbon.consent.mgt.core.exception.ConsentManagementException;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.consent.mgt.core.model.ReceiptInput;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants;
import org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ConsentEventType;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.moesif.handler.util.MoesifHandlerUtils;
import org.wso2.carbon.identity.organization.management.service.constant.OrganizationManagementConstants;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;

import java.util.Map;

import static org.wso2.carbon.consent.mgt.core.constant.ConsentConstants.AUTHZ_STATUS;
import static org.wso2.carbon.consent.mgt.core.constant.ConsentConstants.RECEIPT_ID;
import static org.wso2.carbon.consent.mgt.core.constant.ConsentConstants.RECEIPT_INPUT;
import static org.wso2.carbon.consent.mgt.core.constant.ConsentConstants.InterceptorConstants.POST_ADD_RECEIPT;
import static org.wso2.carbon.consent.mgt.core.constant.ConsentConstants.InterceptorConstants.POST_AUTHORIZE_CONSENT;
import static org.wso2.carbon.consent.mgt.core.constant.ConsentConstants.InterceptorConstants.POST_DELETE_RECEIPT;
import static org.wso2.carbon.consent.mgt.core.constant.ConsentConstants.InterceptorConstants.POST_REVOKE_RECEIPT;
import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.ACTION_NAME_CONSENT;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.CONSENT_PUBLISHER_ENABLED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.CONSENT_PUBLISHER_NAME;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.CONSENT_STREAM_NAME;

/**
 * Event handler that publishes consent events to Moesif.
 *
 * <p>Publishes the consent lifecycle events from carbon-consent-management to a single Moesif Actions
 * stream under the {@code Consent} action; the operation is carried in the payload {@code eventType}
 * field. Only consent metadata is published — never any PII value the user consented to.
 */
public class MoesifConsentDataPublishHandler extends AbstractEventHandler {

    private static final Log LOG = LogFactory.getLog(MoesifConsentDataPublishHandler.class);

    @Override
    public String getName() {

        return CONSENT_PUBLISHER_NAME;
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        String eventName = event.getEventName();
        if (!isConsentEvent(eventName) || !isModuleEnabled()) {
            return;
        }

        Map<String, Object> eventProperties = event.getEventProperties();
        String tenantDomain = (String) eventProperties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN);
        if (StringUtils.isBlank(tenantDomain)) {
            tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
        }

        if (!MoesifHandlerUtils.isHandlerEnabledForPrimaryTenant(tenantDomain,
                MoesifCommonConstants.MOESIF_CONSENT_PUBLISHER_ENABLED_PROPERTY)) {
            return;
        }

        String userId = NOT_AVAILABLE;
        Object[] payloadData;

        switch (eventName) {
            case POST_ADD_RECEIPT: {
                ReceiptInput receiptInput = (ReceiptInput) eventProperties.get(RECEIPT_INPUT);
                userId = MoesifHandlerUtils.resolveConsentUserId(receiptInput);
                payloadData = MoesifHandlerUtils.buildConsentGrantPayload(
                        ConsentEventType.GRANTED.name(), receiptInput, tenantDomain);
                break;
            }
            case POST_AUTHORIZE_CONSENT: {
                String receiptId = (String) eventProperties.get(RECEIPT_ID);
                String authStatus = (String) eventProperties.get(AUTHZ_STATUS);
                userId = MoesifHandlerUtils.getStringOrNotAvailable(
                        eventProperties.get(IdentityEventConstants.EventProperty.USER_ID));
                payloadData = MoesifHandlerUtils.buildConsentAuthorizePayload(
                        ConsentEventType.AUTHORIZED.name(), receiptId, authStatus, tenantDomain);
                break;
            }
            case POST_REVOKE_RECEIPT: {
                String receiptId = (String) eventProperties.get(RECEIPT_ID);
                // The receipt still exists on revoke, so look it up for the user id and details.
                Receipt receipt = resolveReceipt(receiptId);
                if (receipt != null) {
                    userId = MoesifHandlerUtils.getStringOrNotAvailable(receipt.getPiiPrincipalId());
                }
                payloadData = MoesifHandlerUtils.buildConsentRevokeOrDeletePayload(
                        ConsentEventType.REVOKED.name(), receiptId, receipt, tenantDomain);
                break;
            }
            case POST_DELETE_RECEIPT: {
                String receiptId = (String) eventProperties.get(RECEIPT_ID);
                // The receipt is usually already gone on delete; then it is published at company level.
                Receipt receipt = resolveReceipt(receiptId);
                if (receipt != null) {
                    userId = MoesifHandlerUtils.getStringOrNotAvailable(receipt.getPiiPrincipalId());
                }
                payloadData = MoesifHandlerUtils.buildConsentRevokeOrDeletePayload(
                        ConsentEventType.DELETED.name(), receiptId, receipt, tenantDomain);
                break;
            }
            default:
                return;
        }

        String companyId = NOT_AVAILABLE;
        String rootTenantDomain = tenantDomain;
        try {
            String resolvedOrgId = OrganizationManagementConstants.SUPER_ORG_ID;
            if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                rootTenantDomain =
                        OrganizationManagementUtil.getRootOrgTenantDomainBySubOrgTenantDomain(tenantDomain);
                resolvedOrgId = MoesifHandlerDataHolder.getInstance()
                        .getOrganizationManager()
                        .resolveOrganizationId(rootTenantDomain);
            }
            if (StringUtils.isNotBlank(resolvedOrgId)) {
                companyId = resolvedOrgId;
            }
        } catch (OrganizationManagementException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not resolve organisation ID for tenant '" + tenantDomain
                        + "'; using NOT_AVAILABLE as company ID.", e);
            }
        }

        Object[] metaData = MoesifHandlerUtils.getMetaDataArray(
                companyId, ACTION_NAME_CONSENT, userId, NOT_AVAILABLE, NOT_AVAILABLE);

        org.wso2.carbon.databridge.commons.Event databridgeEvent =
                new org.wso2.carbon.databridge.commons.Event(
                        CONSENT_STREAM_NAME, System.currentTimeMillis(),
                        metaData, null, payloadData);

        try {
            FrameworkUtils.startTenantFlow(rootTenantDomain);
            MoesifHandlerDataHolder.getInstance().getPublisherService().publish(databridgeEvent);
        } finally {
            FrameworkUtils.endTenantFlow();
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Published Moesif consent event '" + eventName + "' for tenant: " + tenantDomain);
        }
    }

    private boolean isConsentEvent(String eventName) {

        return POST_ADD_RECEIPT.equals(eventName)
                || POST_AUTHORIZE_CONSENT.equals(eventName)
                || POST_REVOKE_RECEIPT.equals(eventName)
                || POST_DELETE_RECEIPT.equals(eventName);
    }

    private Receipt resolveReceipt(String receiptId) {

        if (StringUtils.isBlank(receiptId)) {
            return null;
        }
        ConsentManager consentManager = MoesifHandlerDataHolder.getInstance().getConsentManager();
        if (consentManager == null) {
            return null;
        }
        try {
            return consentManager.getReceipt(receiptId);
        } catch (ConsentManagementException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not resolve consent receipt '" + receiptId
                        + "'; publishing without receipt details.", e);
            }
            return null;
        }
    }

    private boolean isModuleEnabled() {

        if (this.configs.getModuleProperties() != null) {
            String handlerEnabled = this.configs.getModuleProperties().getProperty(CONSENT_PUBLISHER_ENABLED);
            return Boolean.parseBoolean(handlerEnabled);
        }
        return false;
    }
}
