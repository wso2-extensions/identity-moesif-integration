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
 * Constants for Moesif configuration management.
 */
public class MoesifConfigurationConstants {

    /**
     * Identity.xml / deployment.toml property key for the Moesif portal API master token.
     * Used server-wide to call the Moesif id_tokens endpoint for generating dashboard-viewer tokens.
     *
     * <p>Add to {@code <IS_HOME>/repository/conf/identity/identity.xml}:
     * <pre>{@code
     * <Moesif>
     *     <MasterApiToken>[secretAlias:moesif.masterApiToken]</MasterApiToken>
     * </Moesif>
     * }</pre>
     * Or in {@code deployment.toml}:
     * <pre>{@code
     * [identity.moesif]
     * master_api_token = "$secret{moesif.masterApiToken}"
     * }</pre>
     */
    public static final String MOESIF_MASTER_API_TOKEN_PROPERTY = "Moesif.MasterApiToken";

    /** Secret provider name under which Moesif publisher API keys are stored. */
    public static final String MOESIF_SECRET_PROVIDER = "MOESIF_SECRET_PROVIDER";

    /** Governance connector property key used to enable/disable Moesif publishing per tenant. */
    public static final String MOESIF_PUBLISHER_ENABLED_PROPERTY = "moesif.publisher.enabled";

    private MoesifConfigurationConstants() {

    }
}
