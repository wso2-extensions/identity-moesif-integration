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

package org.wso2.carbon.identity.moesif.handler.util;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.ThreadContext;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.consent.mgt.core.model.PIICategoryValidity;
import org.wso2.carbon.consent.mgt.core.model.Receipt;
import org.wso2.carbon.consent.mgt.core.model.ReceiptInput;
import org.wso2.carbon.consent.mgt.core.model.ReceiptPurposeInput;
import org.wso2.carbon.consent.mgt.core.model.ReceiptService;
import org.wso2.carbon.consent.mgt.core.model.ReceiptServiceInput;
import org.wso2.carbon.identity.application.common.model.Property;

import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;
import org.wso2.carbon.user.api.Claim;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreManager;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.wso2.carbon.identity.event.IdentityEventConstants.EventProperty.USER_STORE_MANAGER;
import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.URL_SUFFIX_ACTIONS;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.URL_SUFFIX_USERS;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.UserOnboardedMethod.ADMIN_INITIATED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.UserOnboardedMethod.SELF_SIGNUP;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.UserOnboardedMethod.USER_INVITE;

import org.wso2.carbon.identity.recovery.IdentityRecoveryConstants;
import org.wso2.carbon.identity.recovery.util.Utils;

import javax.servlet.http.HttpServletRequest;

/**
 * Utility class for building Moesif-formatted payloads from authentication events.
 * The payloads are pre-formatted to Moesif action format so the HTTP adapter
 * can send them directly without transformation.
 */
public class MoesifHandlerUtils {

    private static final Log LOG = LogFactory.getLog(MoesifHandlerUtils.class);
    private static final String USER_CREATED_TIME_URI = "http://wso2.org/claims/created";
    private static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));
    private static final String CORRELATION_ID_KEY = "Correlation-ID";

    private MoesifHandlerUtils() {

    }

    /**
     * Build a Moesif action JSON payload for user registration events.
     *
     * @param eventProperties Event properties containing registration data.
     * @param tenantDomain    Tenant domain.
     * @return JSON string of the Moesif action body.
     */
    public static Object[] buildMoesifRegistrationPayload(Map<String, Object> eventProperties,
                                                          String tenantDomain) {

        UserStoreManager userStoreManager = (UserStoreManager) eventProperties.get(USER_STORE_MANAGER);

        @SuppressWarnings("unchecked")
        Map<String, String> claims =
                (Map<String, String>) eventProperties.get(IdentityEventConstants.EventProperty.USER_CLAIMS);
        if (MapUtils.isEmpty(claims)) {
            return new Object[0];
        }

        String orgId;
        try {
            orgId = MoesifHandlerDataHolder.getInstance().getOrganizationManager().resolveOrganizationId(tenantDomain);
        } catch (OrganizationManagementException e) {
            orgId = NOT_AVAILABLE;
        }

        String userCreatedTime = getCreatedTimestamp(claims.get(USER_CREATED_TIME_URI));
        String userStoreDomainName = userStoreManager.getRealmConfiguration().getUserStoreProperty
                (UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
        String userOnboardedMethod =
                getUserOnboardedMethod((String[]) eventProperties.get(IdentityEventConstants.EventProperty.ROLE_LIST));

        Object[] payload = new Object[6];
        payload[0] = userCreatedTime;
        payload[1] = userStoreDomainName;
        payload[2] = getCorrelationId();
        payload[3] = tenantDomain;
        payload[4] = userOnboardedMethod;
        payload[5] = orgId;

        return payload;
    }

    /**
     * Build a Moesif action JSON payload for a registration funnel step event.
     * Published at each node in the flow to track funnel progression.
     *
     * @return JSON string of the Moesif action body.
     */
    public static Object[] buildMoesifFlowStepPayload(Map<String, Object> eventProperties, String orgId, String parentOrgId) {

        String publishingTime = Instant.now().toString();

        Object[] payloadData = new Object[14];
        payloadData[0] = getStringOrNotAvailable(eventProperties.get(IdentityEventConstants.EventProperty.FLOW_TYPE));
        payloadData[1] = getStringOrNotAvailable(eventProperties.get(IdentityEventConstants.EventProperty.STEP_TYPE));
        payloadData[2] = getStringOrNotAvailable(
                eventProperties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_ID));
        payloadData[3] = getStringOrNotAvailable(
                eventProperties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_TYPE));
        payloadData[4] = getStringOrNotAvailable(eventProperties.get(IdentityEventConstants.EventProperty.CONTEXT_ID));
        payloadData[5] = getStringOrNotAvailable(
                eventProperties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN));
        payloadData[6] = getStringOrNotAvailable(
                eventProperties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_RESPONSE_STATUS));
        payloadData[7] = getStringOrNotAvailable(
                eventProperties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_RESPONSE_TYPE));
        payloadData[8] = getStringOrNotAvailable(
                eventProperties.get(IdentityEventConstants.EventProperty.APPLICATION_ID));
        payloadData[9] = getStringOrNotAvailable(
                eventProperties.get(IdentityEventConstants.EventProperty.EXECUTOR_NAME));
        payloadData[10] = orgId;
        payloadData[11] = !StringUtils.equals(orgId, parentOrgId);
        payloadData[12] = publishingTime;
        payloadData[13] = getStringOrNotAvailable(
                eventProperties.get(IdentityEventConstants.EventProperty.ERROR_CODE));

        return payloadData;
    }

    // Consent payload field positions for the MoesifConsentData stream. Only consent metadata
    // (ids, category/purpose names, counts, policy url/version, service details) is published —
    // never the actual PII value (e.g. the category name "email", never "abc@example.com").
    private static final int CONSENT_PAYLOAD_SIZE = 20;
    private static final int CONSENT_IDX_EVENT_TYPE = 0;
    private static final int CONSENT_IDX_RECEIPT_ID = 1;
    private static final int CONSENT_IDX_CONSENT_TYPE = 2;
    private static final int CONSENT_IDX_AUTHZ_STATUS = 3;
    private static final int CONSENT_IDX_STATE = 4;
    private static final int CONSENT_IDX_SERVICE = 5;
    private static final int CONSENT_IDX_SERVICE_DISPLAY_NAME = 6;
    private static final int CONSENT_IDX_SERVICE_COUNT = 7;
    private static final int CONSENT_IDX_PURPOSE_IDS = 8;
    private static final int CONSENT_IDX_PURPOSE_COUNT = 9;
    private static final int CONSENT_IDX_PII_CATEGORIES = 10;
    private static final int CONSENT_IDX_PII_CATEGORY_COUNT = 11;
    private static final int CONSENT_IDX_THIRD_PARTY_DISCLOSURE = 12;
    private static final int CONSENT_IDX_POLICY_URL = 13;
    private static final int CONSENT_IDX_POLICY_VERSION = 14;
    private static final int CONSENT_IDX_JURISDICTION = 15;
    private static final int CONSENT_IDX_LANGUAGE = 16;
    private static final int CONSENT_IDX_COLLECTION_METHOD = 17;
    private static final int CONSENT_IDX_TENANT_DOMAIN = 18;
    private static final int CONSENT_IDX_PUBLISH_TIME = 19;
    private static final String CONSENT_VALUE_SEPARATOR = ",";
    private static final String CONSENT_TYPE_MIXED = "MIXED";

    /**
     * Build a Moesif consent payload for a consent-grant event ({@code POST_ADD_RECEIPT}) from the
     * consent receipt input.
     */
    public static Object[] buildConsentGrantPayload(String eventType, ReceiptInput receiptInput,
                                                    String tenantDomain) {

        Object[] payload = defaultConsentPayload();
        payload[CONSENT_IDX_EVENT_TYPE] = eventType;
        payload[CONSENT_IDX_TENANT_DOMAIN] = getStringOrNotAvailable(tenantDomain);
        if (receiptInput == null) {
            return payload;
        }

        payload[CONSENT_IDX_RECEIPT_ID] = getStringOrNotAvailable(receiptInput.getConsentReceiptId());
        payload[CONSENT_IDX_STATE] = getStringOrNotAvailable(receiptInput.getState());
        payload[CONSENT_IDX_POLICY_URL] = getStringOrNotAvailable(receiptInput.getPolicyUrl());
        payload[CONSENT_IDX_POLICY_VERSION] = getStringOrNotAvailable(receiptInput.getVersion());
        payload[CONSENT_IDX_JURISDICTION] = getStringOrNotAvailable(receiptInput.getJurisdiction());
        payload[CONSENT_IDX_LANGUAGE] = getStringOrNotAvailable(receiptInput.getLanguage());
        payload[CONSENT_IDX_COLLECTION_METHOD] = getStringOrNotAvailable(receiptInput.getCollectionMethod());

        List<ReceiptServiceInput> services = receiptInput.getServices();
        if (services != null && !services.isEmpty()) {
            payload[CONSENT_IDX_SERVICE_COUNT] = services.size();
            ReceiptServiceInput primary = services.get(0);
            payload[CONSENT_IDX_SERVICE] = getStringOrNotAvailable(primary.getService());
            payload[CONSENT_IDX_SERVICE_DISPLAY_NAME] = getStringOrNotAvailable(primary.getSpDisplayName());

            Set<String> purposeIds = new LinkedHashSet<>();
            Set<String> consentTypes = new LinkedHashSet<>();
            Set<String> piiCategories = new LinkedHashSet<>();
            boolean thirdPartyDisclosure = false;
            for (ReceiptServiceInput service : services) {
                if (service == null || service.getPurposes() == null) {
                    continue;
                }
                for (ReceiptPurposeInput purpose : service.getPurposes()) {
                    if (purpose == null) {
                        continue;
                    }
                    if (purpose.getPurposeId() != null) {
                        purposeIds.add(String.valueOf(purpose.getPurposeId()));
                    }
                    if (StringUtils.isNotBlank(purpose.getConsentType())) {
                        consentTypes.add(purpose.getConsentType());
                    }
                    if (Boolean.TRUE.equals(purpose.isThirdPartyDisclosure())) {
                        thirdPartyDisclosure = true;
                    }
                    collectPiiCategoryNames(purpose.getPiiCategory(), piiCategories);
                }
            }
            payload[CONSENT_IDX_PURPOSE_IDS] = joinOrNotAvailable(purposeIds);
            payload[CONSENT_IDX_PURPOSE_COUNT] = purposeIds.size();
            payload[CONSENT_IDX_PII_CATEGORIES] = joinOrNotAvailable(piiCategories);
            payload[CONSENT_IDX_PII_CATEGORY_COUNT] = piiCategories.size();
            payload[CONSENT_IDX_THIRD_PARTY_DISCLOSURE] = thirdPartyDisclosure;
            payload[CONSENT_IDX_CONSENT_TYPE] = aggregateConsentType(consentTypes);
        }
        return payload;
    }

    /**
     * Build a Moesif consent payload for a consent-authorize event ({@code POST_AUTHORIZE_CONSENT});
     * carries the authorization (approve/deny) status. The user id is set on the event metadata.
     */
    public static Object[] buildConsentAuthorizePayload(String eventType, String receiptId, String authStatus,
                                                        String tenantDomain) {

        Object[] payload = defaultConsentPayload();
        payload[CONSENT_IDX_EVENT_TYPE] = eventType;
        payload[CONSENT_IDX_RECEIPT_ID] = getStringOrNotAvailable(receiptId);
        payload[CONSENT_IDX_AUTHZ_STATUS] = getStringOrNotAvailable(authStatus);
        payload[CONSENT_IDX_TENANT_DOMAIN] = getStringOrNotAvailable(tenantDomain);
        return payload;
    }

    /**
     * Build a Moesif consent payload for a revoke/delete event, enriched from the receipt when it could
     * be looked up (revoke); otherwise only the receipt id is published (delete).
     */
    public static Object[] buildConsentRevokeOrDeletePayload(String eventType, String receiptId, Receipt receipt,
                                                             String tenantDomain) {

        Object[] payload = defaultConsentPayload();
        payload[CONSENT_IDX_EVENT_TYPE] = eventType;
        payload[CONSENT_IDX_RECEIPT_ID] = getStringOrNotAvailable(receiptId);
        payload[CONSENT_IDX_TENANT_DOMAIN] = getStringOrNotAvailable(tenantDomain);
        if (receipt == null) {
            return payload;
        }

        payload[CONSENT_IDX_STATE] = getStringOrNotAvailable(receipt.getState());
        payload[CONSENT_IDX_POLICY_URL] = getStringOrNotAvailable(receipt.getPolicyUrl());
        payload[CONSENT_IDX_POLICY_VERSION] = getStringOrNotAvailable(receipt.getVersion());
        payload[CONSENT_IDX_JURISDICTION] = getStringOrNotAvailable(receipt.getJurisdiction());
        payload[CONSENT_IDX_LANGUAGE] = getStringOrNotAvailable(receipt.getLanguage());
        payload[CONSENT_IDX_COLLECTION_METHOD] = getStringOrNotAvailable(receipt.getCollectionMethod());

        List<ReceiptService> services = receipt.getServices();
        if (services != null && !services.isEmpty()) {
            payload[CONSENT_IDX_SERVICE_COUNT] = services.size();
            ReceiptService primary = services.get(0);
            payload[CONSENT_IDX_SERVICE] = getStringOrNotAvailable(primary.getService());
            payload[CONSENT_IDX_SERVICE_DISPLAY_NAME] = getStringOrNotAvailable(primary.getSpDisplayName());
        }
        return payload;
    }

    /**
     * Resolve the consenting user (PII principal) from the receipt input, or {@code NOT_AVAILABLE}.
     */
    public static String resolveConsentUserId(ReceiptInput receiptInput) {

        if (receiptInput == null) {
            return NOT_AVAILABLE;
        }
        return getStringOrNotAvailable(receiptInput.getPiiPrincipalId());
    }

    private static Object[] defaultConsentPayload() {

        Object[] payload = new Object[CONSENT_PAYLOAD_SIZE];
        Arrays.fill(payload, NOT_AVAILABLE);
        payload[CONSENT_IDX_SERVICE_COUNT] = 0;
        payload[CONSENT_IDX_PURPOSE_COUNT] = 0;
        payload[CONSENT_IDX_PII_CATEGORY_COUNT] = 0;
        payload[CONSENT_IDX_THIRD_PARTY_DISCLOSURE] = false;
        payload[CONSENT_IDX_PUBLISH_TIME] = Instant.now().toString();
        return payload;
    }

    // Collects the consented PII category names (e.g. "email"), not their values, for the payload.
    private static void collectPiiCategoryNames(List<PIICategoryValidity> piiCategories, Set<String> collector) {

        if (piiCategories == null) {
            return;
        }
        for (PIICategoryValidity piiCategory : piiCategories) {
            if (piiCategory == null) {
                continue;
            }
            String name = StringUtils.isNotBlank(piiCategory.getName())
                    ? piiCategory.getName() : piiCategory.getDisplayName();
            if (StringUtils.isNotBlank(name)) {
                collector.add(name);
            }
        }
    }

    private static String aggregateConsentType(Set<String> consentTypes) {

        if (consentTypes.isEmpty()) {
            return NOT_AVAILABLE;
        }
        if (consentTypes.size() == 1) {
            return consentTypes.iterator().next();
        }
        return CONSENT_TYPE_MIXED;
    }

    private static String joinOrNotAvailable(Set<String> values) {

        return values.isEmpty() ? NOT_AVAILABLE : String.join(CONSENT_VALUE_SEPARATOR, values);
    }

    /**
     * Returns the string representation of {@code value}, or {@code NOT_AVAILABLE} when the value is
     * {@code null} or resolves to a blank string.
     *
     * @param value Any object; its {@code toString()} is used when non-null.
     * @return The string value, or {@code NOT_AVAILABLE}.
     */
    public static String getStringOrNotAvailable(Object value) {

        if (value == null) {
            return NOT_AVAILABLE;
        }
        String s = value.toString();
        return StringUtils.isBlank(s) ? NOT_AVAILABLE : s;
    }

    /**
     * Converts an event property value to a {@code boolean}.
     * Accepts {@link Boolean} instances or {@link String} values parseable by {@link Boolean#parseBoolean}.
     *
     * @param value The raw property value.
     * @return The boolean value, or {@code false} when the value cannot be converted.
     */
    public static boolean asBoolean(Object value) {

        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    /**
     * Converts an event property value to a {@code long}.
     * Accepts {@link Number} instances or {@link String} values parseable as a long.
     *
     * @param value The raw property value.
     * @return The long value, or {@code 0L} when the value cannot be converted.
     */
    public static long asLong(Object value) {

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

    private static String getCreatedTimestamp(String createdTime) {

        if (StringUtils.isBlank(createdTime)) {
            return getTimestamp();
        }
        return convertZuluDateFormat(createdTime);
    }

    /**
     * Get the current UTC timestamp in the format yyyy-MM-dd HH:mm:ss.
     *
     * @return Current UTC timestamp.
     */
    public static String getTimestamp() {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(SIMPLE_DATE_FORMAT);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
        return simpleDateFormat.format(Instant.now().toEpochMilli());
    }

    private static String convertZuluDateFormat(String zuluDate) {

        SimpleDateFormat outputFormat = new SimpleDateFormat(SIMPLE_DATE_FORMAT);
        outputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return outputFormat.format(Instant.parse(zuluDate).toEpochMilli());
        } catch (DateTimeParseException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to parse created time in Zulu format: " + zuluDate, e);
            }
            return getTimestamp();
        }
    }

    /**
     * Derive the user onboarded method from the role list and email verify claim.
     *
     * @param roleList List of roles assigned to the user.
     * @return The user onboarded method.
     */
    private static String getUserOnboardedMethod(String[] roleList) {

        String userOnboardedMethod = ADMIN_INITIATED.name();
        if (roleList != null) {
            List<String> roles = Arrays.asList(roleList);
            if (roles.contains(IdentityRecoveryConstants.SELF_SIGNUP_ROLE)) {
                //This is a self signup request.
                userOnboardedMethod = SELF_SIGNUP.name();
            }
        }
        Claim emailVerifyTemporaryClaim = Utils.getEmailVerifyTemporaryClaim();
        if (emailVerifyTemporaryClaim != null) {
            if (IdentityRecoveryConstants.ASK_PASSWORD_CLAIM.equals(emailVerifyTemporaryClaim.getClaimUri())) {
                userOnboardedMethod = USER_INVITE.name();
            }
        }
        return userOnboardedMethod;
    }

    /**
     * Build the metadata array for a Moesif action event, including the client IP address.
     *
     * <p>The returned array maps positionally onto the 6-field {@code metaData} block in every
     * Moesif stream definition: {@code companyId}, {@code actionName}, {@code userId},
     * {@code userAgent}, {@code ipAddress}, {@code urlSuffix}. The Moesif HTTP adapter routes the
     * entries to different parts of the wire payload — {@code userAgent} becomes an HTTP header,
     * {@code ipAddress} becomes a nested {@code request.ipAddress} field, {@code urlSuffix} is
     * consumed to route the request to the matching Moesif endpoint, and the remaining three
     * flatten to the body root. All action events publish with the {@code actions} suffix.</p>
     *
     * @param orgUuid    The organization UUID associated with the event.
     * @param actionName The name of the action being performed (e.g. "UserAuthentication").
     * @param userId     The ID of the user associated with the event, if applicable.
     * @param userAgent  The User-Agent string from the HTTP request, if available.
     * @param ipAddress  The client IP address; {@code NOT_AVAILABLE} when the handler can't resolve it.
     * @return An Object array containing the metadata in the expected order for Moesif events.
     */
    public static Object[] getMetaDataArray(String orgUuid, String actionName, String userId,
                                            String userAgent, String ipAddress) {

        Object[] metaData = new Object[6];
        metaData[0] = orgUuid != null ? orgUuid : NOT_AVAILABLE;
        metaData[1] = actionName != null ? actionName : NOT_AVAILABLE;
        metaData[2] = userId != null ? userId : NOT_AVAILABLE;
        metaData[3] = userAgent != null ? userAgent : NOT_AVAILABLE;
        metaData[4] = ipAddress != null ? ipAddress : NOT_AVAILABLE;
        metaData[5] = URL_SUFFIX_ACTIONS;
        return metaData;
    }

    /**
     * Build the metadata array for a Moesif user-link event, published to the Moesif Users API
     * ({@code urlSuffix = users}) to link the anonymous (flow context) identifier with the actual
     * user ID once it becomes available at flow completion.
     *
     * <p>The returned array maps positionally onto the {@code metaData} block of the
     * {@code MoesifUserLinkData} stream definition: {@code userId}, {@code anonymous_id},
     * {@code urlSuffix}.</p>
     *
     * @param userId      The actual user ID resolved at flow completion.
     * @param anonymousId The anonymous identifier the flow events were published under.
     * @return An Object array containing the metadata in the expected order for user-link events.
     */
    public static Object[] getUserLinkMetaDataArray(String userId, String anonymousId) {

        Object[] metaData = new Object[3];
        metaData[0] = userId != null ? userId : NOT_AVAILABLE;
        metaData[1] = anonymousId != null ? anonymousId : NOT_AVAILABLE;
        metaData[2] = URL_SUFFIX_USERS;
        return metaData;
    }

    /**
     * Extract the User-Agent header from the HTTP request associated with the event, if available.
     *
     * @param event The identity event containing the HTTP request as a property.
     * @return An Optional containing the User-Agent string if it could be extracted, or empty if not available.
     */
    public static Optional<String> extractUserAgent(Event event) {

        try {
            HttpServletRequest request = (HttpServletRequest) event.getEventProperties()
                    .get(IdentityEventConstants.EventProperty.REQUEST);
            if (request != null) {
                return Optional.ofNullable(request.getHeader(USER_AGENT_HEADER));
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not extract User-Agent from event.", e);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether a specific Moesif handler is enabled for the primary (root) organisation that owns
     * the given tenant domain by reading the per-handler governance connector property.
     *
     * <p>The method resolves the org hierarchy upward to the primary organisation and then reads
     * the supplied {@code governancePropertyKey} for that tenant.</p>
     *
     * @param tenantDomain        tenant domain of the current sub-organisation
     * @param governancePropertyKey the governance connector property key for the specific handler
     *                            (e.g. {@code MoesifCommonConstants.MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY})
     * @return {@code true} only when the governance property is explicitly set to {@code "true"}
     */
    public static boolean isHandlerEnabledForPrimaryTenant(String tenantDomain, String governancePropertyKey) {

        try {
            String primaryOrgTenantDomain = tenantDomain;
            if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                primaryOrgTenantDomain = OrganizationManagementUtil
                        .getRootOrgTenantDomainBySubOrgTenantDomain(tenantDomain);
            }
            Property[] properties = MoesifHandlerDataHolder.getInstance().getIdentityGovernanceService()
                    .getConfiguration(new String[]{governancePropertyKey}, primaryOrgTenantDomain);
            return properties != null && properties.length > 0
                    && Boolean.parseBoolean(properties[0].getValue());
        } catch (OrganizationManagementException | IdentityGovernanceException e) {
            LOG.warn("Failed to determine Moesif enabled status for tenant '" + tenantDomain
                    + "'. Defaulting to disabled.", e);
            return false;
        }
    }

    public static String getISOTimestamp(Object timestamp) {

        if (timestamp instanceof Long) {
            return ISO_FORMATTER.format(Instant.ofEpochMilli((Long) timestamp));
        }
        if (timestamp instanceof Number) {
            return ISO_FORMATTER.format(Instant.ofEpochMilli(((Number) timestamp).longValue()));
        }
        if (timestamp instanceof String && StringUtils.isNotBlank((String) timestamp)) {
            try {
                return ISO_FORMATTER.format(Instant.ofEpochMilli(Long.parseLong((String) timestamp)));
            } catch (NumberFormatException ignored) {
                // Fall through to NOT_AVAILABLE.
            }
        }
        return NOT_AVAILABLE;
    }

    /**
     * Returns the correlation id.
     *
     * @return Correlation id.
     */
    private static String getCorrelationId() {

        return isCorrelationIDPresent() ? ThreadContext.get(CORRELATION_ID_KEY)
                : UUID.randomUUID().toString();
    }

    private static boolean isCorrelationIDPresent() {

        return ThreadContext.get(CORRELATION_ID_KEY) != null;
    }
}
