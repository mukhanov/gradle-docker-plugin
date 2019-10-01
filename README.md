Docker Gradle Plugin
====================

This is simple gradle plugin for building images with smallest increment layers size. 
It puts all transitive dependencies in one layer, and all frequently changed in the latest that makes size of each new image much smaller.



Docker Plugin
-------------
Apply the plugin using standard gradle convention:

````gradle
plugins {
	id "com.github.mukhanov.gradle-docker-plugin" version "1.2"
}

group = 'projectgroup'
name = 'projectname'

docker {
	mainClass "com.github.mukhanov.gradleplugin.Example"
}
````
Container will be built with default Dockerfile like this:
````Dockerfile
FROM mcr.microsoft.com/java/jre:11u3-zulu-alpine

ENV JAVA_OPTS "-Xmx1G -Xms128M"
ENV BASEDIR "/opt/app"
ENV LOGS_DIR "/var/log/app"

WORKDIR ${BASEDIR}

ENTRYPOINT java ${JAVA_OPTS} \
    -cp "dependencies/*:app/*" \
    -Xlog:gc*=info:file=${LOGS_DIR}/projectgroup.projectname-gc.log:time,tid,tags \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=${LOGS_DIR} \
    -Dbasedir=${BASEDIR} \
    com.github.mukhanov.gradleplugin.Example

ADD dependencies ./dependencies
ADD app ./app
````

And you can run it like this

````bash
docker run -it projectgroup.projectname
````

Or you can use custom Dockerfile and image name:

````gradle
plugins {
    id 'com.github.mukhanov.gradle-docker-plugin' version '<version>'
}

group = 'projectgroup'
name = 'projectname'

docker {
	dockerfile file("src/main/docker/Dockerfile") //here is your own Dockerfile
	imageName "myregistry.com/${project.name}"
	mainClass "com.github.mukhanov.gradleplugin.Example"
}
````

And run it like this

````bash
docker run -it myregistry.com/projectname
````

Parameters
-------------

| Name  | Default | Description | 
| ------------- | ------------- |------------- |
| dockerfile  | Using default template  |path to Dockerfile template  |
| imageName  | ${project.group}.${project.name}  |Generated image name |
| mainClass  | empty  |Name of the main class to use in Dockerfile |


License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
