package org.autojs.autojs.network.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseAssetPickerTest {

    private fun asset(suffix: String) = GitHubReleaseAsset(
        name = "autojs6-v6.7.0-$suffix.apk",
        size = 1L,
        browserDownloadUrl = "https://example.invalid/$suffix.apk",
    )

    private val releaseAssets = listOf(
        asset("arm64-v8a-62db1ff8"),
        asset("armeabi-67502694"),
        asset("armeabi-v7a-df0b70ee"),
        asset("universal-047ae62e"),
        asset("x86-086c273b"),
        asset("x86_64-3d410022"),
    )

    @Test
    fun `picks the asset of the most preferred ABI`() {
        val picked = GitHubReleaseAssetPicker.pick(releaseAssets, listOf("arm64-v8a", "armeabi-v7a", "universal"))!!
        assertEquals("autojs6-v6.7.0-arm64-v8a-62db1ff8.apk", picked.asset.name)
        assertEquals("arm64-v8a", picked.abi)
    }

    @Test
    fun `falls back to the next ABI and not to a later lower-priority asset`() {
        // The release has no arm64 build; armeabi-v7a (index 1) must win over universal (index 2)
        // even though the universal asset comes later in the list.
        val assets = listOf(asset("armeabi-v7a-df0b70ee"), asset("universal-047ae62e"), asset("x86-086c273b"))
        val picked = GitHubReleaseAssetPicker.pick(assets, listOf("arm64-v8a", "armeabi-v7a", "universal"))!!
        assertEquals("autojs6-v6.7.0-armeabi-v7a-df0b70ee.apk", picked.asset.name)
        assertEquals("armeabi-v7a", picked.abi)
    }

    @Test
    fun `matches ABIs as whole words so x86 does not match x86_64 and armeabi does not match armeabi-v7a`() {
        val x86 = GitHubReleaseAssetPicker.pick(listOf(asset("x86_64-3d410022"), asset("universal-047ae62e")), listOf("x86", "universal"))!!
        assertEquals("autojs6-v6.7.0-universal-047ae62e.apk", x86.asset.name)
        assertEquals("universal", x86.abi)

        val armeabi = GitHubReleaseAssetPicker.pick(releaseAssets, listOf("armeabi", "universal"))!!
        assertEquals("autojs6-v6.7.0-armeabi-67502694.apk", armeabi.asset.name)
    }

    @Test
    fun `keeps the earlier asset when two assets match the same ABI`() {
        val assets = listOf(asset("universal-1"), asset("universal-2"))
        assertEquals("autojs6-v6.7.0-universal-1.apk", GitHubReleaseAssetPicker.pick(assets, listOf("universal"))!!.asset.name)
    }

    @Test
    fun `returns null when nothing matches or the priority list is empty`() {
        assertNull(GitHubReleaseAssetPicker.pick(releaseAssets, listOf("mips", "riscv64")))
        assertNull(GitHubReleaseAssetPicker.pick(releaseAssets, emptyList()))
        assertNull(GitHubReleaseAssetPicker.pick(emptyList(), listOf("arm64-v8a")))
        assertNull(GitHubReleaseAssetPicker.pick(releaseAssets, listOf("")))
    }
}
