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

package org.wso2.carbon.identity.moesif.publisher.http;

import org.wso2.carbon.event.output.adapter.core.MessageType;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapter;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterConfiguration;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterFactory;
import org.wso2.carbon.event.output.adapter.core.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_API_KEY_HEADER;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_API_KEY_VALUE;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_AUTH_TYPE;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_HEADERS;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_HTTP_CLIENT_METHOD;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_MESSAGE_URL;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_PROXY_HOST;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_PROXY_PORT;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_SECRET_PROVIDER;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_TYPE_MOESIF_HTTP;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.AUTH_TYPE_API_KEY;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.AUTH_TYPE_NONE;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.CONSTANT_HTTP_POST;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.CONSTANT_HTTP_PUT;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.DEFAULT_SECRET_PROVIDER;

/**
 * Factory for creating {@link MoesifHTTPEventAdapter} instances.
 *
 * <p>Event publisher XML configurations that specify
 * {@code <to eventAdapterType="moesif-http">} will use this factory. Only the properties the Moesif
 * adapter actually consumes are declared: API key authentication, target URL, custom headers, HTTP
 * client method and an optional proxy.
 */
public class MoesifHTTPEventAdapterFactory extends OutputEventAdapterFactory {

    @Override
    public String getType() {

        return ADAPTER_TYPE_MOESIF_HTTP;
    }

    @Override
    public List<String> getSupportedMessageFormats() {

        List<String> supportedMessageFormats = new ArrayList<>();
        supportedMessageFormats.add(MessageType.JSON);
        return supportedMessageFormats;
    }

    @Override
    public List<Property> getStaticPropertyList() {

        List<Property> staticPropertyList = new ArrayList<>();

        Property secretProvider = new Property(ADAPTER_SECRET_PROVIDER);
        secretProvider.setDisplayName(ADAPTER_SECRET_PROVIDER);
        secretProvider.setHint("Provider identifier for secret management. Defaults to "
                + DEFAULT_SECRET_PROVIDER + " if not set.");
        secretProvider.setRequired(false);
        secretProvider.setDefaultValue(DEFAULT_SECRET_PROVIDER);

        Property proxyHost = new Property(ADAPTER_PROXY_HOST);
        proxyHost.setDisplayName(ADAPTER_PROXY_HOST);
        proxyHost.setRequired(false);

        Property proxyPort = new Property(ADAPTER_PROXY_PORT);
        proxyPort.setDisplayName(ADAPTER_PROXY_PORT);
        proxyPort.setRequired(false);

        Property clientMethod = new Property(ADAPTER_HTTP_CLIENT_METHOD);
        clientMethod.setDisplayName(ADAPTER_HTTP_CLIENT_METHOD);
        clientMethod.setRequired(true);
        clientMethod.setOptions(new String[]{CONSTANT_HTTP_POST, CONSTANT_HTTP_PUT});
        clientMethod.setDefaultValue(CONSTANT_HTTP_POST);

        Property authType = new Property(ADAPTER_AUTH_TYPE);
        authType.setDisplayName(ADAPTER_AUTH_TYPE);
        authType.setRequired(false);
        authType.setOptions(new String[]{AUTH_TYPE_API_KEY, AUTH_TYPE_NONE});
        authType.setDefaultValue(AUTH_TYPE_API_KEY);

        Property apiKeyHeader = new Property(ADAPTER_API_KEY_HEADER);
        apiKeyHeader.setDisplayName(ADAPTER_API_KEY_HEADER);
        apiKeyHeader.setRequired(false);

        Property apiKeyValue = new Property(ADAPTER_API_KEY_VALUE);
        apiKeyValue.setDisplayName(ADAPTER_API_KEY_VALUE);
        apiKeyValue.setRequired(false);
        apiKeyValue.setSecured(true);
        apiKeyValue.setEncrypted(true);

        staticPropertyList.add(secretProvider);
        staticPropertyList.add(proxyHost);
        staticPropertyList.add(proxyPort);
        staticPropertyList.add(clientMethod);
        staticPropertyList.add(authType);
        staticPropertyList.add(apiKeyHeader);
        staticPropertyList.add(apiKeyValue);

        return staticPropertyList;
    }

    @Override
    public List<Property> getDynamicPropertyList() {

        List<Property> dynamicPropertyList = new ArrayList<>();

        Property url = new Property(ADAPTER_MESSAGE_URL);
        url.setDisplayName(ADAPTER_MESSAGE_URL);
        url.setHint("Base URL of the Moesif API. A per-event 'urlSuffix' metaData field is appended to it.");
        url.setRequired(true);

        Property headers = new Property(ADAPTER_HEADERS);
        headers.setDisplayName(ADAPTER_HEADERS);
        headers.setHint("Custom headers in the form 'Name1:Value1,Name2:Value2'.");
        headers.setRequired(false);

        dynamicPropertyList.add(url);
        dynamicPropertyList.add(headers);

        return dynamicPropertyList;
    }

    @Override
    public String getUsageTips() {

        return null;
    }

    @Override
    public OutputEventAdapter createEventAdapter(OutputEventAdapterConfiguration eventAdapterConfiguration,
                                                 Map<String, String> globalProperties) {

        return new MoesifHTTPEventAdapter(eventAdapterConfiguration, globalProperties);
    }
}
