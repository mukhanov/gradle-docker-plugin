package com.github.mukhanov.gradle.docker.plugin

import org.apache.tools.ant.filters.ReplaceTokens

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSet
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.stream.Collectors

class DockerPlugin : Plugin<Project> {

	companion object {
		private const val DEFAULT_DOCKERFILE_PATH = "/Dockerfile.default"
		private val LABEL_VALID_PATTERN = Pattern.compile("^[a-z0-9.-]*$")!!
	}

	override fun apply(project: Project) {

		val ext = project.extensions.create(
				"docker",
				DockerPluginExtension::class.java,
				project
		) as DockerPluginExtension

		if (project.configurations.findByName("docker") == null) {
			project.configurations.create("docker")
		}

		project.afterEvaluate {

			val dockerDir = "${project.buildDir}/docker"
			val projectDependencies = getProjectDependencies(project)
			val classPath = getClassPath(project)

			val clean = project.tasks.create("dockerClean", Delete::class.java)
			clean.group = "Docker"
			clean.description = "Cleans Docker build directory."
			clean.delete(dockerDir)

			val copyDeps = project.tasks.create("dockerPrepareCopyExternalDependencies", Copy::class.java)
			copyDeps.group = "Docker"
			copyDeps.description = "Copy external dependencies into docker folder"
			copyDeps.dependsOn(clean)
			copyDeps.destinationDir = File(dockerDir)
			copyDeps.with(project.copySpec {
				it.from(classPath)
				it.exclude(projectDependencies)
				it.exclude { f -> f.isDirectory || !f.name.endsWith(".jar") }
				it.into("dependencies")
			})

			val copyProjectDeps = project.tasks.create("dockerPrepareCopyProjectDependencies", Copy::class.java)
			copyProjectDeps.group = "Docker"
			copyProjectDeps.description = "Copy project dependencies into docker folder"
			copyProjectDeps.dependsOn(copyDeps)
			copyProjectDeps.destinationDir = File(dockerDir)
			copyProjectDeps.with(project.copySpec {
				it.from(classPath)
				it.include(projectDependencies)
				it.into("app")
			})

			val copyProjectJar = project.tasks.create("dockerPrepareCopyProjectJar", Copy::class.java)
			copyProjectJar.group = "Docker"
			copyProjectJar.description = "Copy project jar into docker folder"
			copyProjectJar.dependsOn(copyProjectDeps)
			copyProjectJar.destinationDir = File(dockerDir)
			copyProjectJar.with(project.copySpec {
				it.from("${project.buildDir}/libs")
				it.include("*.jar")
				it.into("app")
			})

			val copyDockerfile = project.tasks.create("dockerPrepareCopyDockerfile", Copy::class.java)
			copyDockerfile.group = "Docker"
			copyDockerfile.description = "Copy Dockerfile into docker folder"
			copyDockerfile.dependsOn(copyProjectJar)
			copyDockerfile.destinationDir = File(dockerDir)

			val tokenMap = mutableMapOf(
					"project.name" to project.name,
					"project.group" to project.group
			)
			if (ext.mainClass != null) {
				tokenMap["mainClass"] = ext.mainClass
			}

			copyDockerfile.with(project.copySpec {
				it.from(resolveDockerfile(ext))
				it.filter(
						mapOf(
								"tokens" to tokenMap
						),
						ReplaceTokens::class.java
				)
				it.rename { "Dockerfile" }
				it.into(".")
			})

			val copyDockerResources = project.tasks.create("dockerPrepareCopyDockerResources", Copy::class.java)
			copyDockerResources.group = "Docker"
			copyDockerResources.description = "Copy docker resources into docker folder"
			copyDockerResources.dependsOn(copyDockerfile)
			copyDockerResources.destinationDir = File(dockerDir)
			copyDockerResources.with(project.copySpec {
				it.from("src/main/docker/")
				it.exclude { f -> f.name == "Dockerfile" }
				it.into(".")
			})

			val prepare = project.tasks.create("dockerPrepare", Copy::class.java)
			prepare.group = "Docker"
			prepare.description = "Prepares Docker build directory."
			prepare.setDependsOn(setOf(copyDockerResources))
			prepare.destinationDir = File(dockerDir)
			prepare.with(
					ext.copySpec
			)

			val exec = project.tasks.create("docker", Exec::class.java)
			exec.group = "Docker"
			exec.description = "Builds Docker image"
			exec.setDependsOn(setOf(prepare))
			exec.workingDir = File(dockerDir)
			exec.commandLine = buildCommandLine(ext)
			exec.standardOutput = System.out
			exec.errorOutput = System.err

			if (ext.tags.isNotEmpty()) {
				ext.tags.forEach { tag ->
					val tagTask = project.tasks.create("dockerTag_$tag", Exec::class.java)
					tagTask.group = "Docker"
					tagTask.description = "Tag Docker image as $tag"
					tagTask.setDependsOn(setOf(exec))
					tagTask.workingDir = File(dockerDir)
					tagTask.commandLine = listOf("docker", "tag", ext.imageName, tag)
					tagTask.standardOutput = System.out
					tagTask.errorOutput = System.err

					val pushTagTask = project.tasks.create("dockerPushTag_$tag", Exec::class.java)
					pushTagTask.group = "Docker"
					pushTagTask.description = "Push the Docker image with tag $tag"
					pushTagTask.setDependsOn(setOf(exec))
					pushTagTask.workingDir = File(dockerDir)
					pushTagTask.commandLine = listOf("docker", "push", tag)
					pushTagTask.standardOutput = System.out
					pushTagTask.errorOutput = System.err

				}
			}

			val push = project.tasks.create("dockerPush", Exec::class.java)
			push.group = "Docker"
			push.description = "Pushes Docker image"
			push.setDependsOn(setOf(exec))
			push.workingDir = File(dockerDir)
			push.commandLine = listOf("docker", "push", ext.imageName!!)
			push.standardOutput = System.out
			push.errorOutput = System.err

		}
	}

	private fun getClassPath(project: Project): FileCollection {

		val convention = project.convention.getPlugin(JavaPluginConvention::class.java)

		val mainSourceSet = convention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)

		return mainSourceSet.runtimeClasspath
	}

	private fun getProjectDependencies(project: Project): Set<String> {
		return project.configurations.stream()
				.flatMap { x -> x.dependencies.stream() }
				.filter { it is DefaultProjectDependency }
				.map { d -> "${d.name}-${d.version}.jar" }
				.collect(Collectors.toSet())
	}

	private fun buildCommandLine(ext: DockerPluginExtension): List<String> {

		val commandLine = mutableListOf("docker", "build")

		if (ext.buildArgs.isNotEmpty()) {
			ext.buildArgs.forEach { (t, u) ->
				commandLine.addAll(listOf("--build-arg", "$t=$u"))
			}
		}

		if (ext.labels.isNotEmpty()) {

			ext.labels.forEach { (t, u) ->

				if (!LABEL_VALID_PATTERN.matcher(t).matches()) {
					throw  GradleException("Illegal label value: ['$t']. Must be '${LABEL_VALID_PATTERN.pattern()}'")
				}

				commandLine.addAll(listOf("--label", "$t=$u"))
			}
		}

		commandLine.addAll(listOf("-t", ext.imageName!!, "."))

		return commandLine
	}

	private fun resolveDockerfile(ext: DockerPluginExtension): File {
		return if (ext.dockerfile != null) {
			ext.dockerfile!!
		} else {
			loadDefaultDockerfile()
		}
	}

	private fun loadDefaultDockerfile(): File {
		try {
			InputStreamReader(javaClass.getResourceAsStream(DEFAULT_DOCKERFILE_PATH)).use { reader ->

				val dockerfile = File.createTempFile("Dockerfile", "tmp")

				FileWriter(dockerfile).use { writer ->

					val buf = CharArray(4096)
					var count = reader.read(buf)

					while (count != -1) {
						writer.write(buf, 0, count)
						count = reader.read(buf)
					}
				}

				dockerfile.deleteOnExit()

				return dockerfile
			}
		} catch (ex: IOException) {
			throw GradleException("Failed to read '$DEFAULT_DOCKERFILE_PATH'", ex)
		}

	}
}

