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

    // Configuration property keys (under [analytics.moesif] section in deployment.toml).
    public static final String PROVIDER_URL_CONFIG = "Analytics.Moesif.ProviderURL";
    public static final String AUTH_TYPE_CONFIG = "Analytics.Moesif.AuthType";
    public static final String API_KEY_HEADER_CONFIG = "Analytics.Moesif.ApiKeyHeader";
    public static final String STREAM_NAME_CONFIG = "Analytics.Moesif.StreamName";
    public static final String STREAM_VERSION_CONFIG = "Analytics.Moesif.StreamVersion";
    public static final String INLINE_BODY_CONFIG = "Analytics.Moesif.InlineBody";

    private MoesifConfigurationConstants() {

    }
}
