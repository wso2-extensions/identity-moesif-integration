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
 * Error constants for the Moesif configuration management module.
 *
 * <p>Error codes follow the pattern:
 * <ul>
 *   <li>MOESIF_6xxxx – client errors (bad input, resource not found, conflict)</li>
 *   <li>MOESIF_65xxx – server errors (infrastructure, XML, encryption, config)</li>
 * </ul>
 * </p>
 */
public class MoesifConfigurationErrorConstants {

    private MoesifConfigurationErrorConstants() {

    }

    /**
     * Enum of all error codes, messages, and descriptions used in the
     * Moesif configuration management module.
     */
    public enum ErrorMessages {

        // ── Client errors (60xxx) ──────────────────────────────────────────────
        ERROR_API_KEY_REQUIRED(
                "MOESIF_60001",
                "Invalid input: API key value is required.",
                "API key value cannot be blank or empty."),
        ERROR_PUBLISHER_ALREADY_EXISTS(
                "MOESIF_60002",
                "A Moesif publisher configuration already exists.",
                "Conflict: a publisher configuration is already registered. Delete the existing one first."),
        ERROR_CONFIGURATION_MANAGEMENT_CLIENT(
                "MOESIF_60003",
                "Configuration management client error.",
                "A client error occurred while processing the publisher configuration operation."),
        ERROR_PUBLISHER_NOT_FOUND(
                "MOESIF_60004",
                "Moesif publisher configuration not found.",
                "No Moesif publisher configuration exists. Add a publisher first."),
        ERROR_INVALID_PUBLISHER_TYPE(
                "MOESIF_60005",
                "Invalid publisher type(s) provided. Invalid types: %s. Supported: %s.",
                "The request contains publisher type keys that are not supported."),

        // ── Server errors (65xxx) ──────────────────────────────────────────────
        ERROR_GENERATING_PUBLISHER_XML(
                "MOESIF_65001",
                "Error generating event publisher XML.",
                "Failed to create the publisher configuration XML document."),
        ERROR_TRANSFORMING_PUBLISHER_XML(
                "MOESIF_65002",
                "Error transforming event publisher XML.",
                "Failed to serialise the publisher configuration XML."),
        ERROR_RETRIEVING_PUBLISHER_RESOURCE(
                "MOESIF_65003",
                "Error accessing the Moesif publisher resource.",
                "An internal error occurred while reading or writing the publisher resource."),
        ERROR_ENCRYPTING_API_KEY(
                "MOESIF_65004",
                "Error while encrypting Moesif API key.",
                "Failed to encrypt the Moesif API key secret."),
        ERROR_RESOLVING_API_KEY(
                "MOESIF_65020",
                "Error while resolving existing Moesif API key.",
                "Failed to resolve the existing Moesif API key secret from the secret manager."),
        ERROR_CONFIGURATION_MANAGEMENT_SERVER(
                "MOESIF_65005",
                "Configuration management server error.",
                "An internal server error occurred while processing the publisher configuration operation."),
        ERROR_ADDING_PUBLISHER(
                "MOESIF_65006",
                "Error while adding Moesif publisher: %s",
                "An internal error occurred while performing the add publisher operation."),
        ERROR_RETRIEVING_PUBLISHERS(
                "MOESIF_65007",
                "Error while retrieving Moesif publishers.",
                "An internal error occurred while performing the retrieve publishers operation."),
        ERROR_UPDATING_PUBLISHER(
                "MOESIF_65008",
                "Error while updating Moesif publisher: %s",
                "An internal error occurred while performing the update publisher operation."),
        ERROR_DELETING_PUBLISHER(
                "MOESIF_65009",
                "Error while deleting Moesif publisher: %s",
                "An internal error occurred while performing the delete publisher operation."),
        ERROR_MISSING_PROVIDER_URL(
                "MOESIF_65010",
                "Missing required configuration: " + MoesifConfigurationConstants.PROVIDER_URL_CONFIG,
                "The property '" + MoesifConfigurationConstants.PROVIDER_URL_CONFIG
                        + "' must be set in deployment.toml under [analytics.moesif]."),
        ERROR_MISSING_AUTH_TYPE(
                "MOESIF_65011",
                "Missing required configuration: " + MoesifConfigurationConstants.AUTH_TYPE_CONFIG,
                "The property '" + MoesifConfigurationConstants.AUTH_TYPE_CONFIG
                        + "' must be set in deployment.toml under [analytics.moesif]."),
        ERROR_MISSING_API_KEY_HEADER(
                "MOESIF_65012",
                "Missing required configuration: " + MoesifConfigurationConstants.API_KEY_HEADER_CONFIG,
                "The property '" + MoesifConfigurationConstants.API_KEY_HEADER_CONFIG
                        + "' must be set in deployment.toml under [analytics.moesif]."),
        ERROR_MISSING_STREAM_VERSION(
                "MOESIF_65014",
                "Missing required configuration: " + MoesifConfigurationConstants.STREAM_VERSION_CONFIG,
                "The property '" + MoesifConfigurationConstants.STREAM_VERSION_CONFIG
                        + "' must be set in deployment.toml under [analytics.moesif]."),
        ERROR_DELETING_API_KEY_SECRET(
                "MOESIF_65015",
                "Failed to delete Moesif API key secret for publisher: %s. " +
                        "The secret may need to be cleaned up manually.",
                "An error occurred while deleting the Moesif API key secret from the secret manager."),
        ERROR_REDEPLOYING_PUBLISHER_CONFIG(
                "MOESIF_65016",
                "Error re-deploying Moesif event publisher configuration: %s",
                "An error occurred while re-deploying the event publisher configuration."),
        ERROR_UNDEPLOYING_PUBLISHER_CONFIG(
                "MOESIF_65021",
                "Error un-deploying Moesif event publisher configuration: %s",
                "An error occurred while un-deploying the event publisher configuration."),
        ERROR_UPDATING_GOVERNANCE_CONFIG(
                "MOESIF_65017",
                "Failed to update Moesif governance properties for tenant: %s",
                "An error occurred while updating the Moesif governance configuration properties."),
        ERROR_READING_GOVERNANCE_CONFIG(
                "MOESIF_65018",
                "Failed to read Moesif governance properties for tenant: %s",
                "An error occurred while reading the Moesif governance configuration properties."),
        ERROR_MOESIF_DISABLED("MOESIF_65019",
                "Moesif analytics is disabled.",
                "The Moesif analytics publisher is currently disabled.");

        private final String code;
        private final String message;
        private final String description;

        ErrorMessages(String code, String message, String description) {

            this.code = code;
            this.message = message;
            this.description = description;
        }

        public String getCode() {

            return code;
        }

        public String getMessage() {

            return message;
        }

        public String getDescription() {

            return description;
        }

        @Override
        public String toString() {

            return code + ": " + message;
        }
    }
}



