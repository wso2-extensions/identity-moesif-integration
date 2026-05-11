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
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.identity.application.common.model.Property;

import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.flow.execution.engine.model.FlowEventContext;
import org.wso2.carbon.identity.flow.execution.engine.model.FlowExecutionStep;
import org.wso2.carbon.identity.flow.execution.engine.model.NodeResponse;

import org.wso2.carbon.identity.flow.mgt.model.ExecutorDTO;
import org.wso2.carbon.identity.flow.mgt.model.NodeConfig;
import org.wso2.carbon.identity.governance.IdentityGovernanceException;
import org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants;
import org.wso2.carbon.identity.moesif.handler.internal.MoesifHandlerDataHolder;
import org.wso2.carbon.identity.organization.management.service.exception.OrganizationManagementException;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;
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

import static org.wso2.carbon.identity.event.IdentityEventConstants.EventProperty.USER_STORE_MANAGER;
import static org.wso2.carbon.identity.moesif.handler.constant.MoesifHandlerConstants.NOT_AVAILABLE;
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

        Object[] payload = new Object[5];
        payload[0] = userCreatedTime;
        payload[1] = userStoreDomainName;
        payload[2] = tenantDomain;
        payload[3] = userOnboardedMethod;
        payload[4] = orgId != null ? orgId : "";

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
        payloadData[0] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.FLOW_TYPE));
        payloadData[1] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.STEP_TYPE));
        payloadData[2] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_ID));
        payloadData[3] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_TYPE));
        payloadData[4] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.CONTEXT_ID));
        payloadData[5] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN));
        payloadData[6] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_RESPONSE_STATUS));
        payloadData[7] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.CURRENT_NODE_RESPONSE_TYPE));
        payloadData[8] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.APPLICATION_ID));
        payloadData[9] = replaceIfStringNotAvailable((String)
                eventProperties.get(IdentityEventConstants.EventProperty.EXECUTOR_NAME));
        payloadData[10] = orgId;
        payloadData[11] = !StringUtils.equals(orgId, parentOrgId);
        payloadData[12] = publishingTime;
        payloadData[13] = replaceIfStringNotAvailable(IdentityEventConstants.EventProperty.ERROR_CODE);

        return payloadData;
    }

    public static String replaceIfStringNotAvailable(String value) {

        return value != null ? value : NOT_AVAILABLE;
    }

    private static String getCreatedTimestamp(String createdTime) {

        if (org.apache.commons.lang3.StringUtils.isBlank(createdTime)) {
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

    public static Object[] getMetaDataArray(String orgUuid, String actionName, String userId, String userAgent) {
        Object[] metaData = new Object[4];

        metaData[0] = orgUuid != null ? orgUuid : NOT_AVAILABLE;
        metaData[1] = actionName != null ? actionName : NOT_AVAILABLE;
        metaData[2] = userId != null ? userId : NOT_AVAILABLE;
        metaData[3] = userAgent != null ? userAgent : NOT_AVAILABLE;

        return metaData;
    }

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
     * Checks whether Moesif publishing is enabled for the primary (root) organisation that owns
     * the given tenant domain.
     *
     * <p>The method resolves the org hierarchy upward to the primary organisation and then reads
     * the {@code moesif.publisher.enabled} governance connector property for that tenant.</p>
     *
     * @param tenantDomain tenant domain of the current sub-organisation
     * @return {@code true} only when the governance property is explicitly set to {@code "true"}
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
}
