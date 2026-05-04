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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;
import org.wso2.carbon.identity.recovery.IdentityRecoveryConstants;
import org.wso2.carbon.identity.recovery.util.Utils;
import org.wso2.carbon.user.api.Claim;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreManager;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import static org.wso2.carbon.identity.event.IdentityEventConstants.EventProperty.USER_STORE_MANAGER;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.UserOnboardedMethod.ADMIN_INITIATED;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.UserOnboardedMethod.SELF_SIGNUP;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.UserOnboardedMethod.USER_INVITE;

/**
 * Utility class for building Moesif-formatted payloads from identity events.
 */
public class MoesifHandlerUtils {

    private static final Log LOG = LogFactory.getLog(MoesifHandlerUtils.class);
    private static final String USER_CREATED_TIME_URI = "http://wso2.org/claims/created";
    private static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String USER_AGENT_HEADER = "User-Agent";

    private MoesifHandlerUtils() {

    }

    /**
     * Build a payload array for user registration events.
     *
     * @param eventProperties Event properties containing registration data.
     * @param tenantDomain    Tenant domain.
     * @return Payload object array.
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
            orgId = MoesifHandlerDataHolder.getInstance().getOrganizationManager()
                    .resolveOrganizationId(tenantDomain);
        } catch (OrganizationManagementException e) {
            throw new RuntimeException(e);
        }

        String userCreatedTime = getCreatedTimestamp(claims.get(USER_CREATED_TIME_URI));
        String userStoreDomainName = userStoreManager.getRealmConfiguration()
                .getUserStoreProperty(UserCoreConstants.RealmConfig.PROPERTY_DOMAIN_NAME);
        String userOnboardedMethod = getUserOnboardedMethod(
                (String[]) eventProperties.get(IdentityEventConstants.EventProperty.ROLE_LIST));

        Object[] payload = new Object[5];
        payload[0] = userCreatedTime;
        payload[1] = userStoreDomainName;
        payload[2] = tenantDomain;
        payload[3] = userOnboardedMethod;
        payload[4] = orgId != null ? orgId : "";

        return payload;
    }

    /**
     * Build a payload array for a registration funnel step event.
     *
     * @param flowType     The flow type (e.g., REGISTRATION).
     * @param stepType     The step type at this node (e.g., VIEW, EXECUTION, END).
     * @param stepNumber   Formatted step+edge identifier (e.g., STEP_2_BRANCH_1).
     * @param nodeType     The type of node.
     * @param tenantDomain Tenant domain.
     * @param flowId       The flow context identifier.
     * @param errorCode    Error code if the step failed, or null.
     * @return Payload object array.
     */
    public static Object[] buildMoesifFlowStepPayload(String flowType, String stepType, String stepNumber,
                                                        String nodeType, String tenantDomain, String flowId,
                                                        String errorCode) {

        String publishingTime = Instant.now().toString();

        Object[] payloadData = new Object[8];
        payloadData[0] = flowType != null ? flowType : MoesifHandlerConstants.NOT_AVAILABLE;
        payloadData[1] = stepType != null ? stepType : MoesifHandlerConstants.NOT_AVAILABLE;
        payloadData[2] = stepNumber != null ? stepNumber : MoesifHandlerConstants.NOT_AVAILABLE;
        payloadData[3] = nodeType != null ? nodeType : MoesifHandlerConstants.NOT_AVAILABLE;
        payloadData[4] = flowId != null ? flowId : MoesifHandlerConstants.NOT_AVAILABLE;
        payloadData[5] = tenantDomain != null ? tenantDomain : MoesifHandlerConstants.NOT_AVAILABLE;
        payloadData[6] = publishingTime;
        payloadData[7] = errorCode != null ? errorCode : MoesifHandlerConstants.NOT_AVAILABLE;

        return payloadData;
    }

    /**
     * Build the metadata array used as the correlating context for a Moesif event.
     *
     * @param orgUuid    Organisation UUID.
     * @param actionName Moesif action name.
     * @param userId     User identifier.
     * @param userAgent  User-Agent string, or {@code NOT_AVAILABLE}.
     * @return Metadata object array.
     */
    public static Object[] getMetaDataArray(String orgUuid, String actionName, String userId, String userAgent) {

        Object[] metaData = new Object[4];
        metaData[0] = orgUuid != null ? orgUuid : MoesifHandlerConstants.NOT_AVAILABLE;
        metaData[1] = actionName != null ? actionName : MoesifHandlerConstants.NOT_AVAILABLE;
        metaData[2] = userId != null ? userId : MoesifHandlerConstants.NOT_AVAILABLE;
        metaData[3] = userAgent != null ? userAgent : MoesifHandlerConstants.NOT_AVAILABLE;

        return metaData;
    }

    /**
     * Extract the User-Agent header value from the HTTP request embedded in the event.
     *
     * @param event Identity event.
     * @return Optional User-Agent string.
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
     * Check whether Moesif publishing is enabled for the primary (root) organisation
     * that owns the given tenant domain.
     *
     * @param tenantDomain Tenant domain of the current sub-organisation.
     * @return {@code true} only when the governance property is explicitly {@code "true"}.
     */
    public static boolean isMoesifEnabledForPrimaryTenant(String tenantDomain) {

        try {
            String primaryOrgTenantDomain = tenantDomain;
            if (!MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                primaryOrgTenantDomain = OrganizationManagementUtil
                        .getRootOrgTenantDomainBySubOrgTenantDomain(tenantDomain);
            }
            Property[] properties = MoesifHandlerDataHolder.getInstance().getIdentityGovernanceService()
                    .getConfiguration(
                            new String[]{MoesifHandlerConstants.MOESIF_PUBLISHER_ENABLED_PROPERTY},
                            primaryOrgTenantDomain);
            return properties != null && properties.length > 0
                    && Boolean.parseBoolean(properties[0].getValue());
        } catch (OrganizationManagementException | IdentityGovernanceException e) {
            LOG.warn("Failed to determine Moesif enabled status for tenant '" + tenantDomain
                    + "'. Defaulting to disabled.", e);
            return false;
        }
    }

    /**
     * Get the current UTC timestamp in the format yyyy-MM-dd HH:mm:ss.
     *
     * @return Current UTC timestamp string.
     */
    public static String getTimestamp() {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(SIMPLE_DATE_FORMAT);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
        return simpleDateFormat.format(Instant.now().toEpochMilli());
    }

    private static String getCreatedTimestamp(String createdTime) {

        if (org.apache.commons.lang3.StringUtils.isBlank(createdTime)) {
            return getTimestamp();
        }
        return convertZuluDateFormat(createdTime);
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

    private static String getUserOnboardedMethod(String[] roleList) {

        String userOnboardedMethod = ADMIN_INITIATED.name();
        if (roleList != null) {
            List<String> roles = Arrays.asList(roleList);
            if (roles.contains(IdentityRecoveryConstants.SELF_SIGNUP_ROLE)) {
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
}
