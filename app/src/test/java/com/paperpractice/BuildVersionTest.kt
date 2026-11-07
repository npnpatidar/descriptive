package com.paperpractice

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Home footer prints [BuildConfig.VERSION_NAME], so a blank or unparseable
 * value would ship a useless label. The version itself is chosen by hand in
 * `app/build.gradle.kts`; this only guards the shape the footer renders.
 */
class BuildVersionTest {

    @Test
    fun `the footer shows a real release version`() {
        assertTrue(
            "versionName \"${BuildConfig.VERSION_NAME}\" is not X.Y.Z",
            BuildConfig.VERSION_NAME.matches(Regex("""\d+\.\d+\.\d+"""))
        )
        assertTrue("versionCode must be positive", BuildConfig.VERSION_CODE > 0)
    }
}
