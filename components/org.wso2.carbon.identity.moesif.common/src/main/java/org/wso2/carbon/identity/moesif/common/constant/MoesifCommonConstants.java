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

package org.wso2.carbon.identity.moesif.common.constant;

/**
 * Common constants shared across all Moesif integration modules.
 *
 * <p>Centralises values that are referenced by more than one module (e.g.
 * governance connector property names used by both the configuration module
 * to register/update them and the handler module to evaluate them at runtime).
 */
public class MoesifCommonConstants {

    /** Sentinel value used in payloads when a field cannot be resolved. */
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";

    /** Enables or disables the authentication (login) event publisher per tenant. */
    public static final String MOESIF_AUTHENTICATION_PUBLISHER_ENABLED_PROPERTY =
            "moesif.authentication.publisher.enable";

    /** Enables or disables the user-registration event publisher per tenant. */
    public static final String MOESIF_REGISTRATION_PUBLISHER_ENABLED_PROPERTY =
            "moesif.registration.publisher.enable";

    /** Enables or disables the flow-step event publisher per tenant. */
    public static final String MOESIF_FLOW_PUBLISHER_ENABLED_PROPERTY =
            "moesif.flow.publisher.enable";

    /** Enables or disables (org-switch) event publisher per tenant. */
    public static final String MOESIF_ORG_SWITCH_PUBLISHER_ENABLED_PROPERTY =
            "moesif.orgSwitch.publisher.enable";

    private MoesifCommonConstants() {

    }
}

