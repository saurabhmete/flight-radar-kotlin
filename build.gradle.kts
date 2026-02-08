
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "org.ssm.flightradar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"
val logbackVersion = "1.5.6"
val kmongoVersion = "5.1.0"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation-jvm:$ktorVersion")

    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-jvm:$ktorVersion")

    implementation("org.litote.kmongo:kmongo-coroutine:$kmongoVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("software.amazon.awssdk:ssm:2.25.30")


    testImplementation(kotlin("test"))
}

application {
    mainClass = "org.ssm.flightradar.ApplicationKt"
}

tasks.register<JavaExec>("runArrivalJob") {
    group = "application"
    description = "Runs the arrival batch job once (uses only yesterday's OpenSky data)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.ssm.flightradar.ArrivalJobMainKt")
    args("arrival-job")
}

tasks.test {
    useJUnitPlatform()
}
