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

package org.wso2.carbon.identity.moesif.configuration.constant;

/**
 * Constants specific to the Moesif configuration management module.
 *
 * <p>Governance connector property names and other cross-module constants are
 * defined in {@code MoesifCommonConstants} in the {@code moesif.common} module.</p>
 */
public class MoesifConfigurationConstants {

    public static final String MOESIF_SECRET_PROVIDER = "MOESIF_SECRET_PROVIDER";

    // TOML configuration property keys (under [analytics.moesif] section in deployment.toml).
    public static final String PROVIDER_URL_CONFIG = "Analytics.Moesif.ProviderURL";
    public static final String AUTH_TYPE_CONFIG = "Analytics.Moesif.AuthType";
    public static final String API_KEY_HEADER_CONFIG = "Analytics.Moesif.ApiKeyHeader";
    public static final String STREAM_VERSION_CONFIG = "Analytics.Moesif.StreamVersion";
    public static final String INLINE_BODY_CONFIG = "Analytics.Moesif.InlineBody";
    public static final String ENABLED_CONFIG = "Analytics.Moesif.Enabled";

    public static final String AUTH_PUBLISHER_RESOURCE_NAME =
            "IsAnalytics-Publisher-moesif-MoesifUserAuthenticationData";
    public static final String REGISTRATION_PUBLISHER_RESOURCE_NAME =
            "IsAnalytics-Publisher-moesif-MoesifUserRegistrationData";
    public static final String FLOW_PUBLISHER_RESOURCE_NAME =
            "IsAnalytics-Publisher-moesif-MoesifFlowData";
    public static final String ORG_SWITCH_PUBLISHER_RESOURCE_NAME =
            "IsAnalytics-Publisher-moesif-MoesifOrgSwitchData";
    public static final String SESSION_PUBLISHER_RESOURCE_NAME =
            "IsAnalytics-Publisher-moesif-MoesifSessionData";
    public static final String TOKEN_ISSUANCE_PUBLISHER_RESOURCE_NAME =
            "IsAnalytics-Publisher-moesif-MoesifOAuthTokenIssuanceData";
    public static final String USER_LINK_PUBLISHER_RESOURCE_NAME =
            "IsAnalytics-Publisher-moesif-MoesifUserLinkData";

    // Per-publisher IS Analytics event stream names (matches stream definitions in handler module).
    public static final String AUTH_PUBLISHER_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifUserAuthenticationData";
    public static final String REGISTRATION_PUBLISHER_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifUserRegistrationData";
    public static final String FLOW_PUBLISHER_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifFlowData";
    public static final String ORG_SWITCH_PUBLISHER_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifOrgSwitchData";
    public static final String SESSION_PUBLISHER_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifSessionData";
    public static final String TOKEN_ISSUANCE_PUBLISHER_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifOAuthTokenIssuanceData";
    public static final String USER_LINK_PUBLISHER_STREAM_NAME =
            "org.wso2.is.analytics.stream.MoesifUserLinkData";

    public static final String MOESIF_AUTHENTICATION_PUBLISHER = "moesif-authentication-publisher";
    public static final String MOESIF_REGISTRATION_PUBLISHER = "moesif-registration-publisher";
    public static final String MOESIF_FLOW_PUBLISHER = "moesif-flow-publisher";
    public static final String MOESIF_ORG_SWITCH_PUBLISHER = "moesif-org-switch-publisher";
    public static final String MOESIF_SESSION_PUBLISHER = "moesif-session-publisher";
    public static final String MOESIF_TOKEN_ISSUANCE_PUBLISHER = "moesif-token-issuance-publisher";

    /**
     * Publisher for user-link events (Moesif Users API). Not exposed as a separately toggleable
     * publisher type — link events are emitted by the flow handler and therefore follow the flow
     * publisher enablement.
     */
    public static final String MOESIF_USER_LINK_PUBLISHER = "moesif-user-link-publisher";

    private MoesifConfigurationConstants() {

    }
}
