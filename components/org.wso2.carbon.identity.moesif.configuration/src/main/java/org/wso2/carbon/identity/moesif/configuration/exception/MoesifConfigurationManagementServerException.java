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

package org.wso2.carbon.identity.moesif.configuration.exception;

/**
 * Server-side exception for Moesif configuration management operations.
 */
public class MoesifConfigurationManagementServerException extends MoesifConfigurationManagementException {

    public MoesifConfigurationManagementServerException(String message, Throwable cause) {

        super(message, cause);
    }

    public MoesifConfigurationManagementServerException(String errorCode, String message, String description) {

        super(errorCode, message, description);
    }

    public MoesifConfigurationManagementServerException(String errorCode, String message, String description,
                                                        Throwable cause) {

        super(errorCode, message, description, cause);
    }
}
