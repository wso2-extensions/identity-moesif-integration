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

package org.wso2.carbon.identity.moesif.handler.constant;

/**
 * Constants for Moesif event handlers.
 */
public class MoesifHandlerConstants {

    public static final String USER_REGISTRATION_PUBLISHER_NAME = "moesifUserRegistrationPublisher";
    public static final String USER_AUTHENTICATION_PUBLISHER_NAME = "moesifUserAuthenticationPublisher";
    public static final String FLOW_PUBLISHER_NAME = "moesifFlowPublisher";
    public static final String ORG_SWITCH_PUBLISHER_NAME = "moesifOrgSwitchPublisher";
    public static final String USER_SESSION_PUBLISHER_NAME = "moesifUserSessionPublisher";
    public static final String TOKEN_ISSUANCE_PUBLISHER_NAME = "moesifOAuthTokenIssuancePublisher";

    public static final String USER_AUTHENTICATION_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifUserAuthenticationData:1.0.0";
    public static final String REGISTRATION_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifUserRegistrationData:1.0.0";
    public static final String FLOW_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifFlowData:1.0.0";
    public static final String ORG_SWITCH_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifOrgSwitchData:1.0.0";
    public static final String USER_SESSION_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifSessionData:1.0.0";
    public static final String TOKEN_ISSUANCE_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifOAuthTokenIssuanceData:1.0.0";
    public static final String USER_LINK_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifUserLinkData:1.0.0";

    public static final String USER_REGISTRATION_PUBLISHER_ENABLED = "moesifUserRegistrationPublisher.enable";
    public static final String USER_AUTHENTICATION_PUBLISHER_ENABLED = "moesifUserAuthenticationPublisher.enable";
    public static final String FLOW_PUBLISHER_ENABLED = "moesifFlowPublisher.enable";
    public static final String ORG_SWITCH_PUBLISHER_ENABLED = "moesifOrgSwitchPublisher.enable";
    public static final String USER_SESSION_PUBLISHER_ENABLE = "moesifUserSessionPublisher.enable";
    public static final String TOKEN_ISSUANCE_PUBLISHER_ENABLED = "moesifOAuthTokenIssuancePublisher.enable";

    public static final String ACTION_NAME_USER_AUTHENTICATION = "User-Authentication";
    public static final String ACTION_NAME_USER_REGISTRATION = "User-Registration";
    public static final String ACTION_NAME_USER_REGISTRATION_FLOW = "User-Registration-Flow";
    public static final String ACTION_NAME_PASSWORD_RECOVERY_FLOW = "Password-Recovery-Flow";
    public static final String ACTION_NAME_INVITED_USER_REGISTRATION_FLOW = "Invited-User-Registration-Flow";
    public static final String ACTION_NAME_ORG_SWITCH = "Organization-Switch";
    public static final String ACTION_NAME_USER_SESSION = "User-Session";
    public static final String ACTION_NAME_TOKEN_ISSUANCE = "OAuth-Token-Issuance";

    /**
     * Moesif API URL suffixes. Published in the event metaData ({@code urlSuffix}) and consumed by the
     * Moesif HTTP output adapter to route each event to the matching Moesif endpoint.
     */
    public static final String URL_SUFFIX_ACTIONS = "actions";
    public static final String URL_SUFFIX_USERS = "users";

    /**
     * Node ID of the terminal node in a flow graph. Mirrors
     * {@code org.wso2.carbon.identity.flow.mgt.Constants.END_NODE_ID}; kept as a local constant so this
     * module does not require a framework version that defines it.
     */
    public static final String FLOW_END_NODE_ID = "END";

    /** Node response status value published when a node has executed to completion. */
    public static final String NODE_RESPONSE_STATUS_COMPLETE = "COMPLETE";

    /** Event name for user registration events. */
    public static final String POST_ADD_USER = "POST_ADD_USER";

    /** Tenant domain names property key used to resolve publishing domains. */
    public static final String TENANT_DOMAIN_NAMES = "tenantDomainNames";

    public enum UserOnboardedMethod {

        ADMIN_INITIATED,
        USER_INVITE,
        SELF_SIGNUP
    }

    private MoesifHandlerConstants() {

    }
}
