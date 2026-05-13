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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.core.util.IdentityUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Utility class for generating Moesif event publisher XML configurations.
 * Creates event publisher XML that binds data streams to the HTTP output event adapter.
 */
public class MoesifPublisherUtils {

    private static final Log LOG = LogFactory.getLog(MoesifPublisherUtils.class);

    private static final String ROOT_ELEMENT = "eventPublisher";
    private static final String PUBLISHER_NAME = "name";
    private static final String STATISTICS_KEY = "statistics";
    private static final String TRACE_KEY = "trace";
    private static final String XMLNS_KEY = "xmlns";
    private static final String XMLNS_VALUE = "http://wso2.org/carbon/eventpublisher";
    private static final String ENABLE = "enable";
    private static final String DISABLE = "disable";
    private static final String FROM = "from";
    private static final String STREAM_NAME = "streamName";
    private static final String STREAM_VERSION = "version";
    private static final String MAPPING = "mapping";
    private static final String CUSTOM_MAPPING_KEY = "customMapping";
    private static final String MAPPING_TYPE_KEY = "type";
    private static final String JSON = "json";
    private static final String INLINE = "inline";
    private static final String TO = "to";
    private static final String ADAPTER_TYPE_KEY = "eventAdapterType";
    private static final String ADAPTER_TYPE_MOESIF_HTTP = "moesif-http";
    private static final String ADAPTER_PROPERTY = "property";
    private static final String ADAPTER_PROPERTY_NAME = "name";

    private static final String HTTP_URL_PROPERTY = "http.url";
    private static final String HTTP_SECRET_PROVIDER_PROPERTY = "http.secret.provider";
    private static final String HTTP_HEADERS_PROPERTY = "http.headers";
    private static final String HTTP_AUTH_TYPE_PROPERTY = "http.authType";
    private static final String HTTP_API_KEY_HEADER_PROPERTY = "http.apiKeyHeader";
    private static final String HTTP_API_KEY_VALUE_PROPERTY = "http.apiKeyValue";
    private static final String CLIENT_HTTP_METHOD_PROPERTY = "http.client.method";
    private static final String CONSTANT_HTTP_POST = "HttpPost";

    private static final String API_KEY_HEADER = "apiKeyHeader";
    private static final String API_KEY_VALUE = "apiKeyValue";
    private static final String HEADERS = "headers";

    private MoesifPublisherUtils() {

    }

    /**
     * Generate a Moesif event publisher XML input stream that binds a data stream to the HTTP adapter.
     *
     * @param publisherName Publisher name.
     * @param streamName    Event stream name.
     * @param streamVersion Event stream version.
     * @param providerURL   Moesif API endpoint URL.
     * @param secretProvider Secret provider identifier for secret management.
     * @param authType      Authentication type (e.g., API_KEY).
     * @param inlineBody    Inline body template for the mapping.
     * @param properties    Additional properties including auth credentials and dynamic headers.
     * @return Input stream of the generated event publisher XML.
     * @throws ParserConfigurationException If a parser configuration error occurs.
     * @throws TransformerException         If an XML transformation error occurs.
     */
    public static InputStream generateMoesifPublisher(String publisherName, String streamName, String streamVersion,
                                                       String providerURL, String secretProvider, String authType,
                                                       String inlineBody, Map<String, String> properties)
            throws ParserConfigurationException, TransformerException {

        DocumentBuilderFactory documentFactory = IdentityUtil.getSecuredDocumentBuilderFactory();
        DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
        Document document = documentBuilder.newDocument();

        Element root = document.createElement(ROOT_ELEMENT);
        document.appendChild(root);

        setAttribute(document, root, PUBLISHER_NAME, publisherName);
        setAttribute(document, root, STATISTICS_KEY, DISABLE);
        setAttribute(document, root, TRACE_KEY, DISABLE);
        setAttribute(document, root, XMLNS_KEY, XMLNS_VALUE);

        Element from = document.createElement(FROM);
        root.appendChild(from);
        setAttribute(document, from, STREAM_NAME, streamName);
        setAttribute(document, from, STREAM_VERSION, streamVersion);

        Element mapping = document.createElement(MAPPING);
        root.appendChild(mapping);
        setAttribute(document, mapping, CUSTOM_MAPPING_KEY, DISABLE);
        setAttribute(document, mapping, MAPPING_TYPE_KEY, JSON);
        Element inline = document.createElement(INLINE);
        inline.appendChild(document.createTextNode(inlineBody));
        mapping.appendChild(inline);

        addToElement(providerURL, secretProvider, authType, properties, document, root);

        DOMSource xmlSource = new DOMSource(document);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Result outputTarget = new StreamResult(outputStream);
        Transformer transformer = IdentityUtil.getSecuredTransformerFactory().newTransformer();
        transformer.transform(xmlSource, outputTarget);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private static void addToElement(String providerURL, String secretProvider, String authType,
                                      Map<String, String> properties, Document document, Element root) {

        Element to = document.createElement(TO);
        root.appendChild(to);
        setAttribute(document, to, ADAPTER_TYPE_KEY, ADAPTER_TYPE_MOESIF_HTTP);

        Map<String, String> adapterProperties = new HashMap<>();
        adapterProperties.put(HTTP_SECRET_PROVIDER_PROPERTY, secretProvider);
        adapterProperties.put(HTTP_URL_PROPERTY,
                StringUtils.isNotBlank(providerURL) ? providerURL : StringUtils.EMPTY);
        adapterProperties.put(CLIENT_HTTP_METHOD_PROPERTY, CONSTANT_HTTP_POST);

        if (StringUtils.isNotEmpty(authType)) {
            adapterProperties.put(HTTP_AUTH_TYPE_PROPERTY, authType);
        }
        addIfPresent(properties, API_KEY_HEADER, HTTP_API_KEY_HEADER_PROPERTY, adapterProperties);
        addIfPresent(properties, API_KEY_VALUE, HTTP_API_KEY_VALUE_PROPERTY, adapterProperties);

        if (StringUtils.isNotEmpty(properties.get(HEADERS))) {
            adapterProperties.put(HTTP_HEADERS_PROPERTY, properties.get(HEADERS));
        }

        for (Map.Entry<String, String> property : adapterProperties.entrySet()) {
            Element adapterProperty = document.createElement(ADAPTER_PROPERTY);
            setAttribute(document, adapterProperty, ADAPTER_PROPERTY_NAME, property.getKey());
            adapterProperty.appendChild(document.createTextNode(property.getValue()));
            to.appendChild(adapterProperty);
        }
    }

    private static void addIfPresent(Map<String, String> source, String sourceKey,
                                      String targetKey, Map<String, String> target) {

        String value = source.get(sourceKey);
        if (StringUtils.isNotEmpty(value)) {
            target.put(targetKey, value);
        }
    }

    private static void setAttribute(Document document, Element element, String name, String value) {

        Attr attr = document.createAttribute(name);
        attr.setValue(value);
        element.setAttributeNode(attr);
    }
}
