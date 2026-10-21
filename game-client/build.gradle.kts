plugins {
    application
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":render-api"))
    implementation(project(":engine-core"))
    implementation(project(":ui-core"))
    // Runtime-only on purpose: the game client compiles against the renderer API,
    // not against OpenGL. A future Vulkan backend can implement the same API.
    runtimeOnly(project(":renderer-opengl"))
    runtimeOnly(project(":renderer-software"))

}

application {
    mainClass.set("dev.itzsiden.skyvoxel.client.ClientMain")
}
