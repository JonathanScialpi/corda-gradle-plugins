package net.corda.plugins

import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject

/**
 * Creates docker-compose file and image definitions based on the configuration of this task in the gradle configuration DSL.
 *
 * See documentation for examples.
 */
@Suppress("unused")
open class Dockerform @Inject constructor(objects: ObjectFactory) : Baseform(objects) {
    private companion object {
        const val nodeJarName = "corda.jar"
        private val defaultDirectory: Path = Paths.get("build", "docker")

        private const val dockerComposeFileVersion = "3"

        private val yamlOptions = DumperOptions().apply {
            indent = 2
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        }
        private val yaml = Yaml(yamlOptions)
    }

    init {
        description = "Creates a docker-compose file and image definitions for a deployment of Corda Nodes."
    }

    private val directoryPath: Path = project.projectDir.toPath().resolve(directory)

    @get:InputFile
    val dockerComposePath: Path = directoryPath.resolve("docker-compose.yml")

    /**
     * This task action will create and install the nodes based on the node configurations added.
     */
    @TaskAction
    fun build() {
        project.logger.lifecycle("Running Cordform task")
        initializeConfiguration()
        nodes.forEach(Node::installDockerConfig)
        installCordaJar()
        nodes.forEach(Node::installDrivers)
        bootstrapNetwork()
        nodes.forEach(Node::installCordapps)
        nodes.forEach(Node::buildDocker)


        // Transform nodes path the absolute ones
        val services = nodes.map { it.containerName to mapOf(
                "build" to directoryPath.resolve(it.nodeDir.name).toAbsolutePath().toString(),
                "ports" to listOf(it.rpcPort)) }.toMap()


        val dockerComposeObject = mapOf(
                "version" to dockerComposeFileVersion,
                "services" to services)

        val dockerComposeContent = yaml.dump(dockerComposeObject)

        Files.write(dockerComposePath, dockerComposeContent.toByteArray(StandardCharsets.UTF_8))
    }
}
