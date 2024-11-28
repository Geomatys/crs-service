plugins {
    alias(libs.plugins.geomatys.boot.convention)
}

dependencies {
    implementation(platform(libs.geomatys.backend.bom))

    implementation("com.geomatys.backend.spring.starters:geomatys-web-starter")
    implementation("com.geomatys.backend.spring.starters:geomatys-tracing-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // For nullable annotations.
    compileOnly("org.jspecify:jspecify:1.0.0")

    implementation("org.apache.sis.core:sis-referencing:1.5-SNAPSHOT")
    implementation("org.apache.sis.non-free:sis-embedded-data:1.3")
    implementation("org.apache.sis.non-free:sis-epsg:1.3")
    implementation("org.apache.derby:derby:10.15.2.0")
    implementation("org.apache.derby:derbytools:10.15.2.0")
    implementation("org.opengis:geoapi-conformance:3.0.2")

    // For client
    implementation("org.graalvm.js:js:24.1.1")
    implementation("org.graalvm.js:js-scriptengine:24.1.1")

    // For GIGS tests
    //implementation("org.iogp:gigs:1.0-SNAPSHOT")

    // For Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    implementation("com.github.therapi:therapi-runtime-javadoc:0.15.0")
    annotationProcessor("com.github.therapi:therapi-runtime-javadoc-scribe:0.13.0")
}
