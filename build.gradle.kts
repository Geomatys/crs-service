import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    alias(libs.plugins.geomatys.boot.convention)
    id("org.asciidoctor.jvm.convert") version "4.0.2"
    `java-test-fixtures`
}

sourceSets {
    create("docker") {
        resources {
            srcDir("src/docker")
        }
    }
}

dependencies {
    implementation(platform(libs.geomatys.backend.bom))

    implementation("com.geomatys.backend.spring.starters:geomatys-web-starter")
    implementation("com.geomatys.backend.spring.starters:geomatys-tracing-starter")
    implementation("org.springframework.boot:spring-boot-starter-security")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // For nullable annotations.
    compileOnly("org.jspecify:jspecify:1.0.0")

    // Referencing engine
    implementation("org.apache.sis.core:sis-referencing:1.5.0-ALPHA-1")
    implementation("org.apache.sis.non-free:sis-embedded-data:1.3")
    implementation("org.apache.sis.non-free:sis-epsg:1.3")
    implementation("org.apache.derby:derby")
    implementation("org.apache.derby:derbytools")
    implementation("org.opengis:geoapi-conformance:3.0.2")

    // For client
    testFixturesImplementation(platform(libs.geomatys.backend.bom))
    testFixturesImplementation("org.springframework:spring-core")
    testFixturesApi("org.apache.sis.core:sis-referencing:1.5.0-ALPHA-1")
    testFixturesImplementation("org.graalvm.js:js:24.1.1")
    testFixturesImplementation("org.graalvm.js:js-scriptengine:24.1.1")
    testFixturesImplementation("org.python:jython-slim:2.7.4")

    // For GIGS tests
    testImplementation("org.iogp:gigs:1.0-GEOMATYS-ALPHA-1")

    // For Swagger UI
    implementation(libs.springdoc.starter)
    implementation(libs.therapi.runtime)
    annotationProcessor(libs.therapi.processor)
}

tasks.withType<AsciidoctorTask> {
    baseDirFollowsSourceDir()
}

tasks.withType<BootJar> {
    // Generate Jar file without version, to make Containerfile configuration invariant.
    archiveVersion = ""
    // Force Gradle to put Containerfile close to the app jar. It allows to make Containerfile configuration easier
    doLast {
        copy { from(sourceSets["docker"].resources).into(destinationDirectory.asFile) }
    }
}

tasks.withType<BootBuildImage> {
    imageName = requireNotNull(project.properties["spring-boot.build-image.imageName"]).toString()
    createdDate = "now"
    docker { bindHostToBuilder = true }
}

// Unit tests exclude GIGS tests
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("gigs")
    }
}

// GIGS tests
val gigsTestTask = tasks.register<Test>("gigs") {
    // Allow GIGS test task to fail. GIGS
    // Conformance tests give an insight about the conversion engine state,
    // but we do not want it to block build.
    // Strict conformance requirement will be added later, when the project will reach sufficient maturity.
    ignoreFailures = true
    description = "Runs GIGS tests."
    group = "verification"
    useJUnitPlatform() {
        includeTags("gigs")
    }
}

// tag::include-gigs-report[]
// If requested, GIGS test report can be embedded in deployed server.
// Disabled by default as report production can take time.
val includeGigsReport = project.properties["spring-boot.include-gigs-report"]?.toString()?.toBoolean() ?: false
if (includeGigsReport) {
    fun gigsReportDir() = gigsTestTask.get().outputs.files.filter { spec -> spec.path.contains("reports") }
    // Hack classpath of bootRun task to include GIGS test reports.
    tasks.withType<BootRun> {
         inputs.files(gigsReportDir())
         classpath(project.layout.buildDirectory.dir("reports"))
         args("--spring.web.resources.static-locations=classpath:/static,classpath:/")
    }

    // Copy GIGS report in Spring Boot jar
    // Directly copy in /static resource directory, which is the default location for static resource serving
    tasks.withType<BootJar> {
        bootInf {
            from(gigsReportDir())
            into("classes/static/tests/gigs")
        }
    }
}
// end::include-gigs-report[]

fun Project.getTaggedImageName() : String {
    val imageVersion = version.let { if (it == "unspecified" || it.toString().endsWith(".x")) "latest" else it }
    val imageName = requireNotNull(properties["spring-boot.build-image.imageName"]).toString()
    return "$imageName:$imageVersion"
}

val dockerBuildTask = tasks.register<Exec>("dockerBuild") {
    val bootJarTask = tasks.withType<BootJar>().first()
    inputs.files(bootJarTask)
    val ctxDir = bootJarTask.destinationDirectory.asFile.get()
    // NOTE: use absolute path to container file, for compatibility purpose with Github workflows
    val containerFile = ctxDir.resolve("Containerfile")
    val imageTag = project.getTaggedImageName()
    commandLine("docker", "build",  "-f", "$containerFile", "-t", imageTag, "$ctxDir")
}

tasks.register<Exec>("dockerPush") {
    dependsOn(dockerBuildTask)
    val imageTag = project.getTaggedImageName()
    commandLine("docker", "push", imageTag)
}
