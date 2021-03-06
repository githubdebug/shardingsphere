/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.orchestration.core.common.yaml.swapper;

import org.apache.shardingsphere.infra.yaml.swapper.YamlSwapper;
import org.apache.shardingsphere.orchestration.core.common.yaml.config.YamlOrchestrationConfiguration;
import org.apache.shardingsphere.orchestration.repository.api.config.OrchestrationCenterConfiguration;
import org.apache.shardingsphere.orchestration.repository.api.config.OrchestrationConfiguration;

/**
 * Orchestration configuration YAML swapper.
 */
public final class OrchestrationConfigurationYamlSwapper implements YamlSwapper<YamlOrchestrationConfiguration, OrchestrationConfiguration> {
    
    private final OrchestrationCenterConfigurationYamlSwapper orchestrationCenterConfigurationSwapper = new OrchestrationCenterConfigurationYamlSwapper();
    
    @Override
    public YamlOrchestrationConfiguration swapToYamlConfiguration(final OrchestrationConfiguration data) {
        YamlOrchestrationConfiguration result = new YamlOrchestrationConfiguration();
        result.setNamespace(data.getNamespace());
        result.setRegistryCenter(orchestrationCenterConfigurationSwapper.swapToYamlConfiguration(data.getRegistryCenterConfiguration()));
        if (data.getAdditionalConfigCenterConfiguration().isPresent()) {
            result.setAdditionalConfigCenter(orchestrationCenterConfigurationSwapper.swapToYamlConfiguration(data.getAdditionalConfigCenterConfiguration().get()));
        }
        return result;
    }
    
    @Override
    public OrchestrationConfiguration swapToObject(final YamlOrchestrationConfiguration yamlConfig) {
        OrchestrationCenterConfiguration registryCenter = orchestrationCenterConfigurationSwapper.swapToObject(yamlConfig.getRegistryCenter());
        if (null == yamlConfig.getAdditionalConfigCenter()) {
            return new OrchestrationConfiguration(yamlConfig.getNamespace(), registryCenter, yamlConfig.isOverwrite());
        }
        OrchestrationCenterConfiguration additionalConfigCenter = orchestrationCenterConfigurationSwapper.swapToObject(yamlConfig.getAdditionalConfigCenter());
        return new OrchestrationConfiguration(yamlConfig.getNamespace(), registryCenter, additionalConfigCenter, yamlConfig.isOverwrite());
    }
}
