package org.autojs.autojs.external.fileprovider

import org.autojs.autojs6.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFileProviderAuthorityTest {

    /**
     * Every build declares its FileProvider as `<applicationId>.fileprovider` (app, inrt and the isolated
     * `-PcomposeUiSpike=true` host alike). The authority used in code must be that one: a Uri built for another
     * application's provider cannot be granted (an earlier hard-coded value made the spike host fail with a
     * SecurityException when it handed a script to the 3-Block Compose designer).
     */
    @Test
    fun authorityFollowsTheApplicationIdOfThisBuild() {
        assertEquals("${BuildConfig.APPLICATION_ID}.fileprovider", AppFileProvider.AUTHORITY)
        assertEquals(BuildConfig.FILE_PROVIDER_AUTHORITY, AppFileProvider.AUTHORITY)
    }
}
