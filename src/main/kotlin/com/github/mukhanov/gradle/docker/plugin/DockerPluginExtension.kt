package com.github.mukhanov.gradle.docker.plugin

import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import java.io.File

/**
 * @author mukhanov@gmail.com
 */
open class DockerPluginExtension(project: Project) {
    var imageName: String? = "${project.group}.${project.name}"
    var mainClass: String? = null
    var tags: Set<String> = setOf()
    var labels: Map<String, String> = mapOf()
    var buildArgs: Map<String, String> = mapOf()
    var dockerfile: File? = null
    var copySpec: CopySpec = project.copySpec()
}
