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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.event.output.adapter.core.EventAdapterUtil;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapter;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterConfiguration;
import org.wso2.carbon.event.output.adapter.core.exception.ConnectionUnavailableException;
import org.wso2.carbon.event.output.adapter.core.exception.OutputEventAdapterException;
import org.wso2.carbon.event.output.adapter.core.exception.OutputEventAdapterRuntimeException;
import org.wso2.carbon.event.output.adapter.core.exception.TestConnectionNotSupportedException;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.secret.mgt.core.exception.SecretManagementException;

import java.net.URL;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.wso2.carbon.event.output.adapter.core.EventAdapterSecretProcessor.decryptCredential;
import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.ALWAYS_PUBLISH_CONFIG;
import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.ANALYTICS_ENABLED_FIELD;
import static org.wso2.carbon.identity.moesif.common.constant.MoesifCommonConstants.NOT_AVAILABLE;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_API_KEY_HEADER;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_API_KEY_VALUE;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_AUTH_TYPE;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_HEADERS;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_HTTP_CLIENT_METHOD;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_MESSAGE_URL;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_PROXY_HOST;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_PROXY_PORT;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ADAPTER_SECRET_PROVIDER;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.AUTH_TYPE_API_KEY;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.AUTH_TYPE_NONE;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.CONSTANT_HTTP_GET;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.CONSTANT_HTTP_PUT;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.CONTENT_TYPE_JSON;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.DEFAULT_SECRET_PROVIDER;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.ENTRY_SEPARATOR;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.HEADER_SEPARATOR;
import static org.wso2.carbon.identity.moesif.publisher.http.MoesifHTTPEventAdapterConstants.SECRET_PROPERTY_API_KEY_VALUE;

/**
 * Output event adapter that publishes identity events to the Moesif APIs.
 *
 * <p>This is a self-contained, Moesif-specific implementation (it intentionally does not extend the generic
 * {@code HTTPEventAdapter} from carbon-analytics-common) so that:
 * <ol>
 *   <li>Only API key based authentication is supported — the unused client-credential / bearer / basic auth
 *       paths of the generic adapter are dropped entirely.</li>
 *   <li>The published event message is deserialised exactly <b>once</b> per publish; the request body, the
 *       {@code User-Agent} header and the target URL are all derived from the same parsed object.</li>
 *   <li>The target URL can vary per event: when the event metaData carries a {@code urlSuffix} field
 *       (e.g. {@code actions}, {@code users}, {@code companies}), the suffix is appended to the configured
 *       base URL so the same publisher can target the Moesif Actions / Users / Companies APIs.</li>
 * </ol>
 *
 * <p>Body shape produced (matches the Moesif Actions API expectations):
 * <pre>
 *   {
 *     "actionName":  "...",   ← first-class Moesif field, flattened from event.metaData
 *     "userId":      "...",   ← first-class
 *     "companyId":   "...",   ← first-class
 *     "metadata":    { ... }, ← event.payloadData nested verbatim
 *     "request":     { "time": "...", "ipAddress": "..." }
 *   }
 * </pre>
 * Several metaData keys get special handling:
 * <ul>
 *   <li>{@code userAgent} — emitted only as the {@code User-Agent} HTTP header; never in the body.</li>
 *   <li>{@code ipAddress} — nested under {@code request.ipAddress} when meaningful; skipped when missing /
 *       NOT_AVAILABLE / empty so consumers don't see a placeholder where Moesif expects a real IP.</li>
 *   <li>{@code urlSuffix} — routing metadata only; consumed to build the target URL, never in the body.</li>
 *   <li>{@code userId} / {@code anonymous_id} / {@code anonymousId} — flattened to the root only when they
 *       carry a meaningful value; skipped when blank / NOT_AVAILABLE so Moesif never receives a placeholder
 *       identity that would create phantom users or break anonymous-to-user linking.</li>
 * </ul>
 */
public class MoesifHTTPEventAdapter implements OutputEventAdapter {

    private static final Log LOG = LogFactory.getLog(MoesifHTTPEventAdapter.class);
    private static final Gson GSON = new Gson();

    private static final String USER_AGENT_HEADER = "User-Agent";

    private static final String META_DATA_FIELD = "metaData";
    private static final String METADATA_FIELD = "metadata";
    private static final String USER_AGENT_FIELD = "userAgent";
    private static final String IP_ADDRESS_FIELD = "ipAddress";
    private static final String URL_SUFFIX_FIELD = "urlSuffix";

    /**
     * Identity fields that are emitted only when they carry a meaningful value. A blank / NOT_AVAILABLE
     * user identifier must be omitted entirely — publishing the placeholder would create phantom users on
     * the Moesif side and break anonymous-to-user linking.
     */
    private static final Set<String> OPTIONAL_IDENTITY_FIELDS = new HashSet<>(Arrays.asList("userId", "anonymous_id", "anonymousId"));
    private static final String EVENT_FIELD = "event";
    private static final String PAYLOAD_DATA_FIELD = "payloadData";
    private static final String REQUEST_FIELD = "request";
    private static final String TIME_FIELD = "time";

    private static final String URL_PATH_SEPARATOR = "/";

    /**
     * Allowed shape of a urlSuffix value. Restricting to path-safe characters guards against an event field
     * being able to rewrite the target authority / query (e.g. {@code ../}, {@code ?}, {@code #}).
     */
    private static final Pattern URL_SUFFIX_PATTERN = Pattern.compile("^[A-Za-z0-9/_-]+$");

    // Executor and connection pool shared by all Moesif HTTP adapter instances (one instance per tenant).
    private static ExecutorService executorService;
    private static HttpConnectionManager connectionManager;

    private final OutputEventAdapterConfiguration eventAdapterConfiguration;
    private final Map<String, String> globalProperties;
    private final String clientMethod;
    private final String provider;
    private final String proxyHost;
    private final String proxyPort;
    private int tenantId;
    private volatile HttpClient httpClient = null;

    public MoesifHTTPEventAdapter(OutputEventAdapterConfiguration eventAdapterConfiguration, Map<String, String> globalProperties) {

        this.eventAdapterConfiguration = eventAdapterConfiguration;
        this.globalProperties = globalProperties;
        this.clientMethod = eventAdapterConfiguration.getStaticProperties().get(ADAPTER_HTTP_CLIENT_METHOD);
        String configuredProvider = eventAdapterConfiguration.getStaticProperties().get(ADAPTER_SECRET_PROVIDER);
        this.provider = StringUtils.isNotBlank(configuredProvider) ? configuredProvider : DEFAULT_SECRET_PROVIDER;
        this.proxyHost = eventAdapterConfiguration.getStaticProperties().get(ADAPTER_PROXY_HOST);
        this.proxyPort = eventAdapterConfiguration.getStaticProperties().get(ADAPTER_PROXY_PORT);
    }

    @Override
    public void init() throws OutputEventAdapterException {

        tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
        initSharedResources(globalProperties != null ? globalProperties : new HashMap<>());
    }

    private synchronized static void initSharedResources(Map<String, String> globals) {

        if (executorService == null) {
            int minThread = getIntProperty(globals, MoesifHTTPEventAdapterConstants.ADAPTER_MIN_THREAD_POOL_SIZE_NAME, MoesifHTTPEventAdapterConstants.ADAPTER_MIN_THREAD_POOL_SIZE);
            int maxThread = getIntProperty(globals, MoesifHTTPEventAdapterConstants.ADAPTER_MAX_THREAD_POOL_SIZE_NAME, MoesifHTTPEventAdapterConstants.ADAPTER_MAX_THREAD_POOL_SIZE);
            long keepAliveTime = getIntProperty(globals, MoesifHTTPEventAdapterConstants.ADAPTER_KEEP_ALIVE_TIME_NAME, (int) MoesifHTTPEventAdapterConstants.DEFAULT_KEEP_ALIVE_TIME_IN_MILLIS);
            int jobQueueSize = getIntProperty(globals, MoesifHTTPEventAdapterConstants.ADAPTER_EXECUTOR_JOB_QUEUE_SIZE_NAME, MoesifHTTPEventAdapterConstants.ADAPTER_EXECUTOR_JOB_QUEUE_SIZE);

            executorService = new ThreadPoolExecutor(minThread, maxThread, keepAliveTime, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(jobQueueSize));

            int maxConnectionsPerHost = getIntProperty(globals, MoesifHTTPEventAdapterConstants.DEFAULT_MAX_CONNECTIONS_PER_HOST_NAME, MoesifHTTPEventAdapterConstants.DEFAULT_MAX_CONNECTIONS_PER_HOST);
            int maxTotalConnections = getIntProperty(globals, MoesifHTTPEventAdapterConstants.MAX_TOTAL_CONNECTIONS_NAME, MoesifHTTPEventAdapterConstants.DEFAULT_MAX_TOTAL_CONNECTIONS);

            connectionManager = new MultiThreadedHttpConnectionManager();
            connectionManager.getParams().setDefaultMaxConnectionsPerHost(maxConnectionsPerHost);
            connectionManager.getParams().setMaxTotalConnections(maxTotalConnections);
        }
    }

    private static int getIntProperty(Map<String, String> properties, String name, int defaultValue) {

        String value = properties.get(name);
        return StringUtils.isNotBlank(value) ? Integer.parseInt(value) : defaultValue;
    }

    @Override
    public void testConnect() throws TestConnectionNotSupportedException {

        throw new TestConnectionNotSupportedException("Test connection is not available");
    }

    @Override
    public void connect() {

        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = new HttpClient(connectionManager);
                }
            }
        }
    }

    @Override
    public void publish(Object message, Map<String, String> dynamicProperties) {

        // Deserialise the event message exactly once; URL, headers and body all derive from this object.
        JsonObject event = parseEvent(message);
        JsonObject metaData = getAsJsonObject(event, META_DATA_FIELD);

        /*
         * When the server-level AlwaysPublish switch is on, the per-org analytics decision carried in
         * the evet metaData drives whether this event should reach Moesif. A disabled org still publishes
         * (so the event router can forward it to Azure Event Hub) but we neither surface the indicator on the
         * wire nor load the Moesif collector key for it. When the switch is off the indicator is irrelevant
         * (direct-to-Moesif on-prem behaviour) and the key is always attached.
         */
        boolean analyticsEnabled = !isAlwaysPublishEnabled() ||
                getBooleanField(metaData, ANALYTICS_ENABLED_FIELD, true);

        String url = resolveUrl(dynamicProperties.get(ADAPTER_MESSAGE_URL), metaData);
        Map<String, String> headers = buildHeaders(metaData, dynamicProperties);
        String payload = buildBody(event);

        String apiKeyHeader = null;
        String apiKeyValue = null;
        String authType = eventAdapterConfiguration.getStaticProperties().get(ADAPTER_AUTH_TYPE);
        if (AUTH_TYPE_API_KEY.equalsIgnoreCase(authType)) {
            if (analyticsEnabled) {
                apiKeyHeader = eventAdapterConfiguration.getStaticProperties().get(ADAPTER_API_KEY_HEADER);
                apiKeyValue = resolveApiKey();
            }
        } else if (StringUtils.isNotBlank(authType) && !AUTH_TYPE_NONE.equalsIgnoreCase(authType)) {
            throw new OutputEventAdapterRuntimeException("The adapter " + eventAdapterConfiguration.getName() +
                    " supports only API key based authentication, but '" + authType + "' was configured.");
        }

        try {
            executorService.submit(new HTTPSender(url, payload, headers, apiKeyHeader, apiKeyValue));
        } catch (RejectedExecutionException e) {
            EventAdapterUtil.logAndDrop(eventAdapterConfiguration.getName(), message, "Job queue is full", e, LOG, tenantId);
        }
    }

    @Override
    public void disconnect() {
        // Not required.
    }

    @Override
    public void destroy() {
        // Not required.
    }

    @Override
    public boolean isPolled() {

        return false;
    }

    /**
     * Resolve the API key to be sent with the request. The key is read from the secret store under the
     * configured provider; the (encrypted) adapter static property acts as a fallback for configurations
     * that predate the secret store integration.
     */
    private String resolveApiKey() {

        try {
            return new String(decryptCredential(provider, AUTH_TYPE_API_KEY, SECRET_PROPERTY_API_KEY_VALUE));
        } catch (SecretManagementException e) {
            String staticApiKeyValue = eventAdapterConfiguration.getStaticProperties().get(ADAPTER_API_KEY_VALUE);
            if (StringUtils.isBlank(staticApiKeyValue)) {
                throw new ConnectionUnavailableException("The adapter " + eventAdapterConfiguration.getName() + " failed to connect to the server due to missing API key value");
            }
            return staticApiKeyValue;
        }
    }

    /**
     * Append the {@code urlSuffix} carried in the event metaData to the configured base URL. This lets a
     * single publisher target different Moesif APIs per event (e.g. {@code .../actions}, {@code .../users},
     * {@code .../companies}). When no valid suffix is present the base URL is used as-is.
     *
     * @param url      Base URL from the publisher configuration.
     * @param metaData Parsed event metaData (may be {@code null}).
     * @return Effective target URL for this event.
     */
    protected String resolveUrl(String url, JsonObject metaData) {

        String suffix = getStringField(metaData, URL_SUFFIX_FIELD);
        if (StringUtils.isBlank(suffix) || NOT_AVAILABLE.equals(suffix)) {
            return url;
        }
        suffix = StringUtils.strip(suffix, URL_PATH_SEPARATOR);
        if (StringUtils.isBlank(suffix) || !URL_SUFFIX_PATTERN.matcher(suffix).matches()) {
            LOG.warn("Ignoring invalid urlSuffix value received in event metaData; publishing to the base URL.");
            return url;
        }
        return StringUtils.removeEnd(url, URL_PATH_SEPARATOR) + URL_PATH_SEPARATOR + suffix;
    }

    /**
     * Build the HTTP request headers: the statically configured headers from the publisher configuration,
     * plus the {@code User-Agent} header when a user-agent string is present in the event metaData.
     *
     * @param metaData          Parsed event metaData (may be {@code null}).
     * @param dynamicProperties Dynamic properties from the event publisher configuration.
     * @return Mutable map of header name to header value.
     */
    protected Map<String, String> buildHeaders(JsonObject metaData, Map<String, String> dynamicProperties) {

        Map<String, String> headers = extractHeaders(dynamicProperties.get(ADAPTER_HEADERS));
        if (headers == null) {
            headers = new HashMap<>();
        }
        String userAgent = getStringField(metaData, USER_AGENT_FIELD);
        if (StringUtils.isNotBlank(userAgent)) {
            headers.put(USER_AGENT_HEADER, userAgent);
        }
        return headers;
    }

    /**
     * Build the request body from the parsed event: metaData fields are flattened to the root (so Moesif
     * sees them as first-class event properties), payloadData is nested under {@code "metadata"}, and a
     * {@code request.time} field is added from the current instant. See the class javadoc for the keys
     * that are intentionally excluded from the root.
     *
     * @param event Parsed {@code event} object of the published message (may be {@code null}).
     * @return JSON string body to be sent in the HTTP request.
     */
    protected String buildBody(JsonObject event) {

        JsonObject result = new JsonObject();
        JsonObject request = new JsonObject();
        request.addProperty(TIME_FIELD, Instant.now().toString());

        boolean alwaysPublish = isAlwaysPublishEnabled();
        if (event != null) {
            JsonObject metaData = getAsJsonObject(event, META_DATA_FIELD);
            if (metaData != null) {
                for (Map.Entry<String, JsonElement> entry : metaData.entrySet()) {
                    String key = entry.getKey();
                    JsonElement value = entry.getValue();
                    if (USER_AGENT_FIELD.equals(key) || URL_SUFFIX_FIELD.equals(key)) {
                        // userAgent goes on the HTTP header only; urlSuffix is routing metadata only.
                        continue;
                    }
                    if (ANALYTICS_ENABLED_FIELD.equals(key) && !alwaysPublish) {
                        // analyticsEnabled is a routing hint for the downstream event router; drop it from
                        // the wire when the router integration is off (direct-to-Moesif on-prem behaviour).
                        continue;
                    }
                    if (IP_ADDRESS_FIELD.equals(key)) {
                        if (isValid(value)) {
                            request.add(IP_ADDRESS_FIELD, value);
                        }
                        continue;
                    }
                    if (OPTIONAL_IDENTITY_FIELDS.contains(key) && !isValid(value)) {
                        // Omit blank / NOT_AVAILABLE user identifiers rather than shipping a placeholder.
                        continue;
                    }
                    result.add(key, value);
                }
            }
            JsonObject payloadData = getAsJsonObject(event, PAYLOAD_DATA_FIELD);
            if (payloadData != null) {
                result.add(METADATA_FIELD, payloadData);
            }
        }

        result.add(REQUEST_FIELD, request);
        return GSON.toJson(result);
    }

    /**
     * Parse the published message and return its {@code event} member. Returns {@code null} when the
     * message is not a JSON object or carries no event object; the publish flow then degrades gracefully
     * (base URL, no User-Agent header, body with {@code request.time} only).
     *
     * @param message Event message received from the event publisher framework.
     * @return Parsed event object, or {@code null}.
     */
    protected JsonObject parseEvent(Object message) {

        if (message == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(message.toString());
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            return getAsJsonObject(element.getAsJsonObject(), EVENT_FIELD);
        } catch (RuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to parse message payload as JSON object.", e);
            }
            return null;
        }
    }

    private static JsonObject getAsJsonObject(JsonObject parent, String field) {

        if (parent == null || !parent.has(field)) {
            return null;
        }
        JsonElement element = parent.get(field);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getStringField(JsonObject object, String field) {

        if (object == null || !object.has(field)) {
            return null;
        }
        JsonElement element = object.get(field);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return null;
    }

    /**
     * Reads a boolean field from the given JSON object, accepting either a JSON boolean or a string parseable
     * by {@link Boolean#parseBoolean}. Returns {@code defaultValue} when the field is absent or not coercible.
     */
    private static boolean getBooleanField(JsonObject object, String field, boolean defaultValue) {

        if (object == null || !object.has(field)) {
            return defaultValue;
        }
        JsonElement element = object.get(field);
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            if (element.getAsJsonPrimitive().isString()) {
                return Boolean.parseBoolean(element.getAsString());
            }
        }
        return defaultValue;
    }

    /**
     * Reads the server-level {@code Analytics.Moesif.AlwaysPublish} switch. When on, events are
     * always published to the configured provider (the event router) and the per-org {@code analyticsEnabled}
     * decision is surfaced so the router can gate Moesif forwarding; when off the publisher behaves as a
     * direct-to-Moesif adapter and drops the indicator.
     */
    private static boolean isAlwaysPublishEnabled() {

        try {
            return Boolean.parseBoolean(IdentityUtil.getProperty(ALWAYS_PUBLISH_CONFIG));
        } catch (RuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not read '" + ALWAYS_PUBLISH_CONFIG + "'; defaulting to false.", e);
            }
            return false;
        }
    }

    /**
     * Returns {@code true} when the given JSON element carries a non-blank, non-sentinel string value.
     * Used to decide whether to surface optional fields (currently only {@code ipAddress}) into the
     * output payload — we'd rather omit the field than ship a {@code "NOT_AVAILABLE"} placeholder that
     * the downstream consumer would have to filter back out.
     */
    private static boolean isValid(JsonElement value) {

        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return true;
        }
        String s = value.getAsString();
        return StringUtils.isNotBlank(s) && !NOT_AVAILABLE.equals(s);
    }

    private Map<String, String> extractHeaders(String headers) {

        if (StringUtils.isBlank(headers)) {
            return null;
        }
        String[] entries = headers.split(HEADER_SEPARATOR);
        Map<String, String> result = new HashMap<>();
        for (String header : entries) {
            try {
                String[] keyValue = header.split(ENTRY_SEPARATOR, 2);
                result.put(keyValue[0].trim(), keyValue[1].trim());
            } catch (Exception e) {
                LOG.warn("Header property '" + header + "' is not defined in the correct format.", e);
            }
        }
        return result;
    }

    /**
     * A job that sends a single, fully prepared HTTP request to the resolved target URL.
     */
    class HTTPSender implements Runnable {

        private final String url;
        private final String payload;
        private final Map<String, String> headers;
        private final String apiKeyHeader;
        private final String apiKeyValue;

        HTTPSender(String url, String payload, Map<String, String> headers, String apiKeyHeader, String apiKeyValue) {

            this.url = url;
            this.payload = payload;
            this.headers = headers;
            this.apiKeyHeader = apiKeyHeader;
            this.apiKeyValue = apiKeyValue;
        }

        @Override
        public void run() {

            UUID uuid = UUID.randomUUID();
            HttpMethodBase method = null;
            try {
                if (CONSTANT_HTTP_PUT.equalsIgnoreCase(clientMethod)) {
                    method = new PutMethod(url);
                } else if (CONSTANT_HTTP_GET.equalsIgnoreCase(clientMethod)) {
                    method = new GetMethod(url);
                } else {
                    method = new PostMethod(url);
                }

                // The URL may differ per event (urlSuffix), so the host configuration is built per request
                // instead of being cached from the first published event.
                URL targetUrl = new URL(url);
                HostConfiguration hostConfiguration = new HostConfiguration();
                hostConfiguration.setHost(targetUrl.getHost(), targetUrl.getPort(), targetUrl.getProtocol());
                if (StringUtils.isNotBlank(proxyHost) && StringUtils.isNotBlank(proxyPort)) {
                    hostConfiguration.setProxy(proxyHost, Integer.parseInt(proxyPort));
                }

                if (method instanceof EntityEnclosingMethod) {
                    ((EntityEnclosingMethod) method).setRequestEntity(new StringRequestEntity(payload, CONTENT_TYPE_JSON, "UTF-8"));
                }

                if (headers != null) {
                    for (Map.Entry<String, String> header : headers.entrySet()) {
                        method.setRequestHeader(header.getKey(), header.getValue());
                    }
                }
                if (StringUtils.isNotBlank(apiKeyHeader) && apiKeyValue != null) {
                    method.setRequestHeader(apiKeyHeader, apiKeyValue);
                }

                int responseCode = httpClient.executeMethod(hostConfiguration, method);
                if (responseCode / 100 == 2) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Id: " + uuid + "] Successfully published to the endpoint: " + url + ". Received HTTP response code: " + responseCode);
                    }
                } else {
                    LOG.error("[Id: " + uuid + "] Error while publishing to the endpoint: " + url + ". Received HTTP response code: " + responseCode + ". Response body: " + method.getResponseBodyAsString());
                }
            } catch (UnknownHostException e) {
                EventAdapterUtil.logAndDrop(eventAdapterConfiguration.getName(), payload, "Cannot connect to " + url, e, LOG, tenantId);
            } catch (Throwable e) {
                EventAdapterUtil.logAndDrop(eventAdapterConfiguration.getName(), payload, null, e, LOG, tenantId);
            } finally {
                if (method != null) {
                    method.releaseConnection();
                }
            }
        }
    }
}
