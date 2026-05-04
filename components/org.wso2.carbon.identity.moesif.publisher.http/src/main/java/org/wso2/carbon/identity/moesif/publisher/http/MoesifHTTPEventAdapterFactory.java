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

import org.wso2.carbon.event.output.adapter.core.OutputEventAdapterConfiguration;
import org.wso2.carbon.event.output.adapter.core.OutputEventAdapter;
import org.wso2.carbon.event.output.adapter.http.HTTPEventAdapterFactory;

import java.util.Map;

/**
 * Factory for creating {@link MoesifHTTPEventAdapter} instances.
 *
 * <p>This factory extends {@link HTTPEventAdapterFactory} and overrides the adapter type identifier to
 * {@value #ADAPTER_TYPE_MOESIF_HTTP}. Event publisher XML configurations that specify
 * {@code <eventAdapterType>moesif-http</eventAdapterType>} will use this factory.
 */
public class MoesifHTTPEventAdapterFactory extends HTTPEventAdapterFactory {

    public static final String ADAPTER_TYPE_MOESIF_HTTP = "moesif-http";

    @Override
    public String getType() {

        return ADAPTER_TYPE_MOESIF_HTTP;
    }

    @Override
    public OutputEventAdapter createEventAdapter(OutputEventAdapterConfiguration eventAdapterConfiguration,
                                                  Map<String, String> globalProperties) {

        return new MoesifHTTPEventAdapter(eventAdapterConfiguration, globalProperties);
    }
}
