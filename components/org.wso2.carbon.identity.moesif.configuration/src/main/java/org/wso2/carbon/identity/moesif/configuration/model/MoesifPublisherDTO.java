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

package org.wso2.carbon.identity.moesif.configuration.model;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO representing a Moesif publisher configuration.
 */
public class MoesifPublisherDTO {

    private String name;
    private String providerURL;
    private String secretProvider;
    private String authType;
    private String streamName;
    private String streamVersion;
    private String inlineBody;

    private Map<String, Boolean> publisherTypes = new HashMap<>();

    private Map<String, String> properties = new HashMap<>();

    public String getName() {

        return name;
    }

    public void setName(String name) {

        this.name = name;
    }

    public String getProviderURL() {

        return providerURL;
    }

    public void setProviderURL(String providerURL) {

        this.providerURL = providerURL;
    }

    public String getSecretProvider() {

        return secretProvider;
    }

    public void setSecretProvider(String secretProvider) {

        this.secretProvider = secretProvider;
    }

    public String getAuthType() {

        return authType;
    }

    public void setAuthType(String authType) {

        this.authType = authType;
    }

    public String getStreamName() {

        return streamName;
    }

    public void setStreamName(String streamName) {

        this.streamName = streamName;
    }

    public String getStreamVersion() {

        return streamVersion;
    }

    public void setStreamVersion(String streamVersion) {

        this.streamVersion = streamVersion;
    }

    public String getInlineBody() {

        return inlineBody;
    }

    public void setInlineBody(String inlineBody) {

        this.inlineBody = inlineBody;
    }

    public Map<String, String> getProperties() {

        return properties;
    }

    public void setProperties(Map<String, String> properties) {

        this.properties = properties;
    }

    public Map<String, Boolean> getPublisherTypes() {

        return publisherTypes;
    }

    public void setPublisherTypes(Map<String, Boolean> publisherTypes) {

        this.publisherTypes = publisherTypes;
    }
}
