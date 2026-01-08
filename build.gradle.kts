plugins {
    `java-library`
}

group = "top.ryuu64.contract"
version = "0.5.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("com.github.Ryuu-64:java-method-contract-annotations:0.5.0")
    // https://mvnrepository.com/artifact/com.google.auto.service/auto-service-annotations
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    // https://mvnrepository.com/artifact/com.google.auto.service/auto-service
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
}
