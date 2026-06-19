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

    /**
     * Server-level (deployment.toml) switch under the {@code [analytics.moesif]} section.
     *
     * <p>When enabled, the Moesif event handlers always publish events to the configured provider
     * (the downstream event router) regardless of the per-org governance toggle, and stamp the
     * per-org governance decision into the event's {@code analyticsEnabled} metaData field. The
     * event router always forwards to Azure Event Hub and uses {@code analyticsEnabled} to decide
     * whether to additionally forward the event to Moesif.</p>
     *
     * <p>When disabled (default — e.g. on-prem deployments without an event router) the handlers
     * gate publishing on the per-org governance toggle as before and the {@code analyticsEnabled}
     * indicator carries {@link #NOT_AVAILABLE} (it is not consumed downstream in this mode).</p>
     */
    public static final String ALWAYS_PUBLISH_CONFIG = "Analytics.Moesif.AlwaysPublish";

    /**
     * Name of the {@code metaData} field that carries the per-org analytics (Moesif publishing) decision.
     *
     * <p>Handlers stamp this with the per-org governance toggle value. The HTTP publisher includes it in
     * the outgoing body only when {@link #ALWAYS_PUBLISH_CONFIG} is enabled; the downstream event
     * router uses it to decide whether to forward the event to Moesif (Azure Event Hub always receives it).</p>
     */
    public static final String ANALYTICS_ENABLED_FIELD = "analyticsEnabled";

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

    /** Enables or disables the session event publisher per tenant. */
    public static final String MOESIF_SESSION_PUBLISHER_ENABLED_PROPERTY =
            "moesif.session.publisher.enable";

    /** Enables or disables the OAuth token issuance event publisher per tenant. */
    /**
     * Master per-org toggle. When enabled, all supported Moesif publishers are treated as enabled for
     * the organization regardless of the individual per-publisher toggles, and the GET response reports
     * every supported publisher as enabled. When disabled, the individual per-publisher toggles apply.
     */
    public static final String MOESIF_ENABLE_ALL_PUBLISHERS_PROPERTY = "moesif.analytics.enable.all";

    public static final String MOESIF_TOKEN_ISSUANCE_PUBLISHER_ENABLED_PROPERTY =
            "moesif.tokenIssuance.publisher.enable";

    private MoesifCommonConstants() {

    }
}

