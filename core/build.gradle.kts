plugins { kotlin("jvm") }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { testImplementation(kotlin("test-junit5")); testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3") }
tasks.test { useJUnitPlatform() }
