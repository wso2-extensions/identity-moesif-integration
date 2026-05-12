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

    public static final String MOESIF_SECRET_PROVIDER = "MOESIF_SECRET_PROVIDER";

    public static final String MOESIF_PUBLISHER_ENABLED_PROPERTY = "moesif.publisher.enabled";

    public static final String MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY =
            "moesif.authentication.publisher.enable";

    public static final String MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY =
            "moesif.registration.publisher.enable";

    public static final String MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY =
            "moesif.flow.publisher.enable";

    public static final String MOESIF_OAUTH_TOKEN_PUBLISHER_ENABLED_PROPERTY =
            "moesif.oAuthToken.publisher.enable";

    private MoesifConfigurationConstants() {

    }
}
