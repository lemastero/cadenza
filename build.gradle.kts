import com.palantir.gradle.graal.ExtractGraalTask;
import com.palantir.gradle.graal.NativeImageTask;
import net.ltgt.gradle.errorprone.*;

group = project.properties["group"].toString()
version = project.properties["version"].toString()

buildscript {
  repositories {
    gradlePluginPortal()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("http://palantir.bintray.com/releases") }
  }

  dependencies {
    classpath("com.palantir.baseline:gradle-baseline-java:2.24.0")
    classpath("gradle.plugin.org.inferred:gradle-processors:2.1.0")
    classpath("${project.group}:gradle:${project.version}")
  }
}


allprojects {
  repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("http://palantir.bintray.com/releases") }
  }
  apply(plugin = "java")
  apply(plugin = "org.inferred.processors")
  apply(plugin = "com.palantir.baseline-versions")
  apply(plugin = "com.palantir.baseline-idea")

  java {
    sourceCompatibility = JavaVersion.VERSION_12
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  dependencies {
    annotationProcessor("${project.group}:gradle:${project.version}")
  }
  tasks.withType<JavaCompile> {
    options.compilerArgs = listOf("--release","8")
  }
}

plugins {
  application
  idea
  id("org.sonarqube") version "2.7.1"
  id("com.palantir.baseline-config") version "2.24.0"
  id("com.palantir.graal") version "0.6.0"
}

dependencies {
  runtime(project(":language"))
  runtime(project(":launcher"))
}

graal {
  graalVersion("19.2.0.1")
  mainClass("cadenza.launcher.Launcher")
  outputName("cadenza-native")
  option("--language:cadenza")
}

val os = System.getProperty("os.name")
val graalToolingDir = tasks.getByName<ExtractGraalTask>("extractGraalTooling").getOutputDirectory().get().getAsFile().toString()
var graalHome = if (os == "Mac OS X") "$graalToolingDir/Contents/Home" else graalToolingDir
val graalBinDir = if (os == "Linux") graalHome else "$graalHome/bin"

subprojects {
  version = project.properties["version"]
}

sonarqube {
  properties {
    property("sonar.projectKey","ekmett_cadenza")
    property("sonar.sourceEncoding","UTF-8")
  }
}


project(":language") {
  apply(plugin = "antlr")
  apply(plugin = "java-library")
  apply(plugin = "com.palantir.baseline-error-prone")
  val antlrRuntime by configurations.creating
  dependencies {
    compile("com.palantir.safe-logging:preconditions:1.11.0")
    testCompile("com.palantir.safe-logging:preconditions-assertj:1.11.0")
    annotationProcessor("org.graalvm.truffle:truffle-api:19.2.0.1")
    annotationProcessor("org.graalvm.truffle:truffle-dsl-processor:19.2.0.1")
    "antlr"("org.antlr:antlr4:4.7.2")
    "antlrRuntime"("org.antlr:antlr4-runtime:4.7.2")
    implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
    implementation("org.graalvm.sdk:graal-sdk:19.2.0.1")
    implementation("org.antlr:antlr4-runtime:4.7.2")
    testImplementation("org.testng:testng:6.14.3")
    implementation("com.palantir.safe-logging:safe-logging")
    implementation("com.palantir.safe-logging:preconditions")
  }
  tasks.getByName<Jar>("jar") {
    baseName = "cadenza-language"
  }
  tasks.withType<JavaCompile> {
    options.errorprone {
      check("SwitchStatementDefaultCase", CheckSeverity.OFF)
    }
  }
  tasks.withType<AntlrTask> {
    arguments.addAll(listOf("-package", "cadenza.syntax", "-no-listener", "-visitor"))
  }
}

project(":launcher") {
  dependencies {
    implementation(project(":language"))
    implementation("org.graalvm.truffle:truffle-api:19.2.0.1")
    implementation("org.graalvm.sdk:launcher-common:19.2.0.1")
  }
  tasks.getByName<Jar>("jar") {
    baseName = "cadenza-launcher"
  }
}

project(":component") {
  apply(plugin = "java")
  tasks.withType<ProcessResources> {
    from("native-image.properties") {
      expand(project.properties)
    }
    rename("native-image.properties","jre/languages/cadenza/native-image.properties")
  }
  val jar = tasks.getByName<Jar>("jar") {
    baseName = "cadenza-component"
    from("../LICENSE.txt") { rename("LICENSE.txt","LICENSE_CADENZA") }
    from("../LICENSE.txt") { rename("LICENSE.txt","jre/languages/cadenza/LICENSE.txt") }
    from(tasks.getByPath(":language:jar")) {
      rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
    }
    from(tasks.getByPath(":launcher:jar")) {
      rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
    }
    from(tasks.getByPath(":startScripts")) {
      rename("(.*)","jre/languages/cadenza/bin/$1")
    }
    from(project(":language").configurations.getByName("antlrRuntime")) {
      rename("(.*).jar","jre/languages/cadenza/lib/\$1.jar")
    }
    manifest {
      attributes["Bundle-Name"] = "Cadenza"
      attributes["Bundle-Description"] = "The cadenza language"
      attributes["Bundle-DocURL"] = "https://github.com/ekmett/cadenza"
      attributes["Bundle-Symbolic-Name"] = "cadenza"
      attributes["Bundle-Version"] = project.version.toString()
      attributes["Bundle-RequireCapability"] = "org.graalvm;filter:=\"(&(graalvm_version=19.2.0)(os_arch=amd64))\""
      attributes["x-GraalVM-Polyglot-Part"] = "True"
    }
  }
  // register the component
  tasks.register("register", Exec::class) {
    dependsOn(":extractGraalTooling", jar)
    description = "Register the language with graal"
    commandLine = listOf(
      "$graalBinDir/gu",
      "install",
      "-f",
      "-L",
      jar.archiveFile.get().getAsFile().getPath()
    )
  }
}

tasks.getByName<NativeImageTask>("nativeImage") {
  dependsOn(":component:register")
}

application {
  mainClassName = "cadenza.launcher.Launcher"
  applicationDefaultJvmArgs = listOf(
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+EnableJVMCI",
    "-Dtruffle.class.path.append=" + project("language").tasks.getByName<Jar>("jar").archiveFile.get().getAsFile().getPath()
  )
}

tasks.getByName<JavaExec>("run") {
  dependsOn(":extractGraalTooling",":language:jar",":launcher:jar")
  executable = "$graalBinDir/java"
}
