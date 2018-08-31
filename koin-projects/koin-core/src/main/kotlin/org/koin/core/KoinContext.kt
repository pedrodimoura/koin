/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.koin.core

import org.koin.core.Koin.Companion.logger
import org.koin.core.instance.DefinitionFilter
import org.koin.core.instance.InstanceManager
import org.koin.core.instance.InstanceRequest
import org.koin.core.parameter.ParameterDefinition
import org.koin.core.parameter.emptyParameterDefinition
import org.koin.core.property.PropertyRegistry
import org.koin.error.MissingPropertyException
import org.koin.standalone.StandAloneKoinContext
import kotlin.reflect.KClass


/**
 * Koin Application ModuleDefinition
 * ModuleDefinition from where you can get beans defined in modules
 *
 * @author - Arnaud GIULIANI
 * @author - Laurent Baresse
 */
class KoinContext(
    val instanceManager: InstanceManager,
    val propertyResolver: PropertyRegistry
) : StandAloneKoinContext {

    val contextCallback: ArrayList<ModuleCallback> = arrayListOf()

    /**
     * Retrieve an instance from its name/class
     * @param name
     * @param module
     * @param parameters
     */
    inline fun <reified T : Any> get(
        name: String = "",
        module: String? = null,
        noinline parameters: ParameterDefinition = emptyParameterDefinition()
    ): T = instanceManager.resolve(
        InstanceRequest(
            name = name,
            module = module,
            clazz = T::class,
            parameters = parameters
        )
    )

    /**
     * Retrieve an instance from its name/class
     *
     * @param name
     * @param clazz
     * @param module
     * @param parameters
     * @param filter
     */
    fun <T: Any> get(
        name: String = "",
        clazz: KClass<*>,
        module: String? = null,
        parameters: ParameterDefinition = emptyParameterDefinition(),
        filter: DefinitionFilter? = null
    ): T = instanceManager.resolve(
        InstanceRequest(
            name = name,
            module = module,
            clazz = clazz,
            parameters = parameters
        ),
        filter
    )

    //TODO deplacer release()

    //partie module
    //partie scope

    /**
     * Drop all instances for path context
     * @param path
     */
    fun release(path: String) {
        instanceManager.release(path)
        contextCallback.forEach { it.onRelease(path) }
    }

    /**
     * Retrieve a property by its key
     * can throw MissingPropertyException if the property is not found
     * @param key
     * @throws MissingPropertyException if key is not found
     */
    inline fun <reified T> getProperty(key: String): T = propertyResolver.getProperty(key)

    /**
     * Retrieve a property by its key or return provided default value
     * @param key - property key
     * @param defaultValue - default value if property is not found
     */
    inline fun <reified T> getProperty(key: String, defaultValue: T): T =
        propertyResolver.getProperty(key, defaultValue)

    /**
     * Set a property
     */
    fun setProperty(key: String, value: Any) = propertyResolver.add(key, value)

    /**
     * Close all resources
     */
    fun close() {
        logger.info("[Close] Closing Koin context")
        instanceManager.close()
        propertyResolver.clear()
    }
}