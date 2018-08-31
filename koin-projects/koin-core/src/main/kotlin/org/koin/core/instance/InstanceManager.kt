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
package org.koin.core.instance

import org.koin.core.Koin
import org.koin.core.bean.BeanRegistry
import org.koin.core.parameter.ParameterDefinition
import org.koin.core.path.PathRegistry
import org.koin.core.stack.ResolutionStack
import org.koin.core.time.measureDuration
import org.koin.dsl.definition.BeanDefinition
import kotlin.reflect.KClass

/**
 * Instance Resolver
 *
 * @author Arnaud Giuliani
 */
class InstanceManager(
    val beanRegistry: BeanRegistry,
    val instanceFactory: InstanceFactory,
    val pathRegistry: PathRegistry
) {

    private val resolutionStack = ResolutionStack()

    /**
     * resolve instance from InstanceRequest
     */
    fun <T : Any> resolve(request: InstanceRequest, filterFunction: DefinitionFilter? = null): T {

        val definitions = (if (filterFunction != null) {
            beanRegistry.definitions.filter(filterFunction)
        } else beanRegistry.definitions)

        return request.run {
            val search = when {
                name.isNotEmpty() -> {
                    { beanRegistry.searchByNameAndClass(definitions, name, clazz) }
                }
                else -> {
                    { beanRegistry.searchByClass(definitions, clazz) }
                }
            }
            proceedResolution(module, clazz, parameters, search)
        }
    }

    /**
     * Resolve a dependency for its bean definition
     * @param module - module path
     * @param clazz - Class
     * @param parameters - Parameters
     * @param definitionResolver - function to find bean definitions
     */
    fun <T : Any> proceedResolution(
        module: String? = null,
        clazz: KClass<*>,
        parameters: ParameterDefinition,
        definitionResolver: () -> List<BeanDefinition<*>>
    ): T = synchronized(this) {

        var resultInstance: T? = null
        val logIndent: String = resolutionStack.indent()
        val duration = measureDuration {
            try {
                val beanDefinition: BeanDefinition<T> =
                    beanRegistry.retrieveDefinition(
                        clazz,
                        if (module != null) pathRegistry.getPath(module) else null,
                        definitionResolver,
                        resolutionStack.last()
                    )

                val logPath =
                    if ("${beanDefinition.path}".isEmpty()) "" else "@ ${beanDefinition.path}"
                val startChar = if (resolutionStack.isEmpty()) "+" else "+"

                Koin.logger.info("$logIndent$startChar-- '$clazz' $logPath") // @ [$beanDefinition]")
                Koin.logger.debug("$logIndent|-- [$beanDefinition]")

                resolutionStack.resolve(beanDefinition) {
                    val (instance, created) = instanceFactory.retrieveInstance<T>(
                        beanDefinition,
                        parameters
                    )

                    Koin.logger.debug("$logIndent|-- $instance")
                    // Log creation
                    if (created) {
                        Koin.logger.info("$logIndent\\-- (*) Created")
                    }
                    resultInstance = instance
                }
            } catch (e: Exception) {
                resolutionStack.clear()
                Koin.logger.err("Error while resolving instance for class '$clazz' - error: $e ")
                throw e
            }
        }

        Koin.logger.debug("$logIndent!-- [$clazz] resolved in $duration ms")

        return if (resultInstance != null) resultInstance!! else error("Could not create instance for $clazz")
    }

    /**
     * Create instances at start - tagged eager
     * @param defaultParameters
     */
    fun createEagerInstances(defaultParameters: ParameterDefinition) {
        val definitions = beanRegistry.definitions.filter { it.isEager }

        if (definitions.isNotEmpty()) {
            Koin.logger.info("Creating instances ...")
            createInstances(definitions, defaultParameters)
        }
    }

    /**
     * Create instances for given definition list & params
     * @param definitions
     * @param params
     */
    private fun createInstances(
        definitions: Collection<BeanDefinition<*>>,
        params: ParameterDefinition
    ) {
        definitions.forEach { def ->
            proceedResolution(
                def.path.toString(),
                def.clazz,
                params
            ) { listOf(def) }
        }
    }

    /**
     * Dry Run - run each definition
     */
    fun dryRun(defaultParameters: ParameterDefinition) {
        createInstances(beanRegistry.definitions, defaultParameters)
    }

    /**
     * Drop all instances for path context
     * @param path
     */
    fun release(path: String) {
        Koin.logger.info("release module '$path'")

        val paths = pathRegistry.getAllPathsFrom(path)
        val definitions: List<BeanDefinition<*>> =
            beanRegistry.getDefinitionsInPaths(paths)

        instanceFactory.release(definitions)
    }

    /**
     * Close res
     */
    fun close() {
        Koin.logger.debug("[Close] Closing instance resolver")
        resolutionStack.clear()
        instanceFactory.clear()
        beanRegistry.clear()
    }
}

typealias DefinitionFilter = (BeanDefinition<*>) -> Boolean