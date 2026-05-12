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

    public static final String USER_AUTHENTICATION_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifUserAuthenticationData:1.0.0";
    public static final String REGISTRATION_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifUserRegistrationData:1.0.0";
    public static final String FLOW_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifFlowData:1.0.0";

    public static final String USER_REGISTRATION_PUBLISHER_ENABLED = "moesifUserRegistrationPublisher.enable";
    public static final String USER_AUTHENTICATION_PUBLISHER_ENABLED = "moesifUserAuthenticationPublisher.enable";
    public static final String FLOW_PUBLISHER_ENABLED = "moesifFlowPublisher.enable";

    public static final String ACTION_NAME_USER_AUTHENTICATION = "User-Authentication";
    public static final String ACTION_NAME_USER_REGISTRATION = "User-Registration";
    public static final String ACTION_NAME_USER_REGISTRATION_FLOW = "User-Registration-Flow";
    public static final String ACTION_NAME_PASSWORD_RECOVERY_FLOW = "Password-Recovery-Flow";
    public static final String ACTION_NAME_FLOW_DEFAULT = "-Flow";

    public static final String FLOW_TYPE_REGISTRATION = "REGISTRATION";
    public static final String FLOW_TYPE_PASSWORD_RECOVERY = "PASSWORD_RECOVERY";

    /** Event name for user registration events. */
    public static final String POST_ADD_USER = "POST_ADD_USER";

    /** Tenant domain names property key used to resolve publishing domains. */
    public static final String TENANT_DOMAIN_NAMES = "tenantDomainNames";

    /**
     * FlowExecutionContext property key for the forward-BFS-derived nodeId → step-label map.
     * Cached once per flow to avoid re-computing on every step event.
     */
    public static final String MOESIF_FLOW_STEP_ID_MAP_KEY = "MOESIF_FLOW_STEP_ID_MAP";

    // Authentication data payload field names used as metadata keys in Moesif.
    public static final String FIELD_EVENT_ID = "eventId";
    public static final String FIELD_EVENT_TYPE = "eventType";
    public static final String FIELD_CONTEXT_ID = "contextId";
    public static final String FIELD_REMOTE_IP = "remoteIp";
    public static final String FIELD_USERNAME = "userName";
    public static final String FIELD_USERNAME_USER_INPUT = "usernameUserInput";
    public static final String FIELD_LOCAL_USERNAME = "localUserName";
    public static final String FIELD_USERSTORE_DOMAIN = "userstoreDomain";
    public static final String FIELD_REMEMBER_ME = "rememberMeEnabled";
    public static final String FIELD_IDENTITY_PROVIDERS = "identityProviders";
    public static final String FIELD_AUTHENTICATORS = "authenticators";
    public static final String FIELD_SERVICE_PROVIDER = "serviceProvider";
    public static final String FIELD_INBOUND_AUTH_TYPE = "inboundAuthType";
    public static final String FIELD_LOGIN_TIMESTAMP = "loginTimestamp";
    public static final String FIELD_TENANT_DOMAIN = "tenantDomain";
    public static final String FIELD_ERROR_CODE = "errorCode";
    public static final String FIELD_PUBLISH_TIMESTAMP = "publishTimestamp";
    public static final String FIELD_USER_AGENT = "userAgent";
    public static final String TIME = "time";

    public static final String FIELD_IDENTIFIED_USER_ID = "identifiedUserId";

    public static final String ORG_SWITCH_PUBLISHER_NAME = "moesifOrgSwitchPublisher";
    public static final String ORG_SWITCH_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifOrgSwitchData:1.0.0";
    public static final String ORG_SWITCH_PUBLISHER_ENABLED = "moesifOrgSwitchPublisher.enable";
    public static final String ACTION_NAME_ORG_SWITCH = "Organization-Switch";
    public static final String POST_ORGANIZATION_SWITCH_EVENT = "POST_ORGANIZATION_SWITCH_EVENT";
    /** Event property key carrying the {@code OAuthTokenReqMessageContext}. */
    public static final String OAUTH_TOKEN_REQ_MESSAGE_CONTEXT = "OAUTH_TOKEN_REQ_MESSAGE_CONTEXT";
    /** Property key under which the {@code OAuthAppDO} is stored in the token-request context. */
    public static final String OAUTH_APP_DO_PROPERTY = "OAuthAppDO";

    public enum UserOnboardedMethod {

        ADMIN_INITIATED,
        USER_INVITE,
        SELF_SIGNUP
    }

    private MoesifHandlerConstants() {

    }
}
