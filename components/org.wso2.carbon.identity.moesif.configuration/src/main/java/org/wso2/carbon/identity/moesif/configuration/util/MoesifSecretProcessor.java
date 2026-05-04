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

package org.wso2.carbon.identity.moesif.configuration.util;

import org.wso2.carbon.identity.moesif.configuration.internal.MoesifConfigurationDataHolder;
import org.wso2.carbon.identity.secret.mgt.core.exception.SecretManagementException;
import org.wso2.carbon.identity.secret.mgt.core.model.ResolvedSecret;
import org.wso2.carbon.identity.secret.mgt.core.model.Secret;
import org.wso2.carbon.identity.secret.mgt.core.model.SecretType;

/**
 * Utility class for encrypting and resolving secret properties of Moesif publishers.
 */
public class MoesifSecretProcessor {

    private static final String SECRET_PROPERTIES_SUFFIX = "_SECRET_PROPERTIES";

    private MoesifSecretProcessor() {

    }

    /**
     * Encrypt a secret property for a Moesif publisher and return its reference string.
     *
     * @param publisherName Name of the Moesif publisher (used as the secret namespace).
     * @param authType      Authentication type (e.g., API_KEY).
     * @param property      The property key (e.g., apiKeyValue).
     * @param value         The plaintext secret value to encrypt.
     * @return Reference string pointing to the stored secret.
     * @throws SecretManagementException If an error occurs while storing the secret.
     */
    public static String encryptSecret(String publisherName, String authType, String property, String value)
            throws SecretManagementException {

        String secretName = buildSecretName(publisherName, authType, property);
        String secretType = publisherName + SECRET_PROPERTIES_SUFFIX;
        if (!isSecretTypeExist(secretType)) {
            SecretType secretTypeObj = new SecretType();
            secretTypeObj.setName(secretType);
            secretTypeObj.setDescription("Secret Type for Moesif Publisher: " + publisherName);
            MoesifConfigurationDataHolder.getInstance().getSecretManager().addSecretType(secretTypeObj);
        }
        if (isSecretExist(secretType, secretName)) {
            updateSecret(secretType, secretName, value);
        } else {
            addSecret(secretType, secretName, value);
        }
        return buildSecretReference(secretType, secretName);
    }

    /**
     * Delete all secrets associated with a Moesif publisher.
     *
     * @param publisherName Name of the Moesif publisher.
     * @param authType      Authentication type whose secrets should be deleted.
     * @param properties    Property keys to delete.
     * @throws SecretManagementException If an error occurs while deleting the secrets.
     */
    public static void deleteSecrets(String publisherName, String authType, String... properties)
            throws SecretManagementException {

        String secretType = publisherName + SECRET_PROPERTIES_SUFFIX;
        for (String property : properties) {
            String secretName = buildSecretName(publisherName, authType, property);
            if (!isSecretExist(secretType, secretName)) {
                continue;
            }
            MoesifConfigurationDataHolder.getInstance().getSecretManager().deleteSecret(secretType, secretName);
        }
    }

    /**
     * Decrypt (resolve) a previously stored secret property.
     *
     * @param publisherName Name of the Moesif publisher (secret namespace).
     * @param authType      Authentication type (e.g., API_KEY).
     * @param property      The property key (e.g., moesifApiToken).
     * @return Plaintext secret value.
     * @throws SecretManagementException If the secret does not exist or decryption fails.
     */
    public static String decryptSecret(String publisherName, String authType, String property)
            throws SecretManagementException {

        String secretName = buildSecretName(publisherName, authType, property);
        String secretType = publisherName + SECRET_PROPERTIES_SUFFIX;
        ResolvedSecret resolvedSecret = MoesifConfigurationDataHolder.getInstance().getSecretResolveManager()
                .getResolvedSecret(secretType, secretName);
        return resolvedSecret.getResolvedSecretValue();
    }

    private static boolean isSecretTypeExist(String secretType) {

        try {
            MoesifConfigurationDataHolder.getInstance().getSecretManager().getSecretType(secretType);
            return true;
        } catch (SecretManagementException e) {
            return false;
        }
    }

    private static boolean isSecretExist(String secretType, String secretName) throws SecretManagementException {

        return MoesifConfigurationDataHolder.getInstance().getSecretManager().isSecretExist(secretType, secretName);
    }

    private static void addSecret(String secretType, String secretName, String value)
            throws SecretManagementException {

        Secret secret = new Secret();
        secret.setSecretName(secretName);
        secret.setSecretValue(value);
        MoesifConfigurationDataHolder.getInstance().getSecretManager().addSecret(secretType, secret);
    }

    private static void updateSecret(String secretType, String secretName, String value)
            throws SecretManagementException {

        ResolvedSecret resolvedSecret = MoesifConfigurationDataHolder.getInstance().getSecretResolveManager()
                .getResolvedSecret(secretType, secretName);
        if (!resolvedSecret.getResolvedSecretValue().equals(value)) {
            MoesifConfigurationDataHolder.getInstance().getSecretManager()
                    .updateSecretValue(secretType, secretName, value);
        }
    }

    private static String buildSecretName(String publisherName, String authType, String property) {

        return publisherName + ":" + authType + ":" + property;
    }

    private static String buildSecretReference(String secretType, String secretName)
            throws SecretManagementException {

        String secretTypeId = MoesifConfigurationDataHolder.getInstance().getSecretManager()
                .getSecretType(secretType).getId();
        return secretTypeId + ":" + secretName;
    }
}
