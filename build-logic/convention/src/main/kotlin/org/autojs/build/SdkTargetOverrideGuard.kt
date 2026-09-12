package org.autojs.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Rejects distribution tasks even when their outputs are already up to date. */
@DisableCachingByDefault(because = "A debug target override must never produce a distribution artifact")
abstract class SdkTargetOverrideGuard : DefaultTask() {
    @get:Input
    abstract val targetSdk: Property<Int>

    @TaskAction
    fun reject() {
        throw GradleException(
            "${Versions.SDK_TARGET_OVERRIDE_PROPERTY}=${targetSdk.get()} is for debug verification only. " +
                "Remove the override before running release, Runtime Kit, or APK template distribution tasks.",
        )
    }
}
