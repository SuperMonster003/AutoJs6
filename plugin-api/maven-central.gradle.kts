// Publishing of the plugin contract libraries to Maven Central (MCP roadmap D58), applied by common-plugin-api and
// plugin-tool-api after they set mavenArtifactId, mavenName, mavenDescription and mavenVersion in extra. Publications go
// to the staging repository build/maven-central-staging of the root project; mavenCentralBundle zips one of them for the
// upload to the Central Portal. Signing is configured only when a key is given (docs/dev/plugin-api-maven-central.md).
//
// zh-CN: 插件契约库发布到 Maven Central 的配置, 由 common-plugin-api 与 plugin-tool-api 在 extra 中设置坐标与说明后应用.
// 发布先进入根项目的 build/maven-central-staging 暂存库, mavenCentralBundle 将其中一个库打包以上传到 Central Portal; 只有提供了密钥时才签名.

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

apply(plugin = "maven-publish")
apply(plugin = "signing")

val mavenGroup = "io.github.supermonster003"
val mavenArtifactId = extra["mavenArtifactId"] as String
val mavenVersion = extra["mavenVersion"] as String
val mavenName = extra["mavenName"] as String
val mavenDescription = extra["mavenDescription"] as String
val stagingRepository = rootProject.layout.buildDirectory.dir("maven-central-staging")

group = mavenGroup
version = mavenVersion

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = mavenGroup
                artifactId = mavenArtifactId
                version = mavenVersion
                pom {
                    name.set(mavenName)
                    description.set(mavenDescription)
                    url.set("https://github.com/SuperMonster003/AutoJs6")
                    licenses {
                        license {
                            name.set("Mozilla Public License 2.0")
                            url.set("https://www.mozilla.org/en-US/MPL/2.0/")
                        }
                    }
                    developers {
                        developer {
                            id.set("SuperMonster003")
                            name.set("SuperMonster003")
                            url.set("https://github.com/SuperMonster003")
                        }
                    }
                    scm {
                        url.set("https://github.com/SuperMonster003/AutoJs6")
                        connection.set("scm:git:https://github.com/SuperMonster003/AutoJs6.git")
                        developerConnection.set("scm:git:ssh://git@github.com/SuperMonster003/AutoJs6.git")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "mavenCentralStaging"
                url = uri(stagingRepository)
            }
        }
    }

    // In-memory ASCII-armored key (signingInMemoryKey / signingInMemoryKeyPassword), the gpg command
    // (signing.gnupg.keyName) or a key ring file (signing.keyId / signing.password / signing.secretKeyRingFile).
    val inMemoryKey = findProperty("signingInMemoryKey") as String?
    val useGpg = findProperty("signing.gnupg.keyName") != null
    if (inMemoryKey != null || useGpg || findProperty("signing.keyId") != null) {
        configure<SigningExtension> {
            when {
                inMemoryKey != null -> useInMemoryPgpKeys(inMemoryKey, findProperty("signingInMemoryKeyPassword") as String?)
                useGpg -> useGpgCmd()
            }
            sign(the<PublishingExtension>().publications["release"])
        }
    }
}

tasks.register<Zip>("mavenCentralBundle") {
    group = "publishing"
    description = "Zips $mavenArtifactId $mavenVersion of the staging repository for the Central Portal."
    dependsOn("publishReleasePublicationToMavenCentralStagingRepository")
    from(stagingRepository) {
        include("${mavenGroup.replace('.', '/')}/$mavenArtifactId/$mavenVersion/**")
    }
    archiveFileName.set("$mavenArtifactId-$mavenVersion-bundle.zip")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("maven-central-bundles"))
}
