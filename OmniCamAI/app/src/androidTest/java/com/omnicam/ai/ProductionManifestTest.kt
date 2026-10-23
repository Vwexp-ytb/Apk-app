package com.omnicam.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionManifestTest {
    @Test
    fun cameraPermissionIsDeclaredAndCleartextTrafficIsDisabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue("Camera permission must be declared", requested.contains(Manifest.permission.CAMERA))
        // ML Kit may merge INTERNET into the final manifest for model delivery.
        // The app itself explicitly disables cleartext HTTP traffic.
        val appInfo = context.applicationInfo
        assertFalse(
            "Cleartext HTTP traffic must be disabled",
            appInfo.flags and android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0
        )
    }

    @Test
    fun packageAndVersionMetadataArePresent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(context.packageName)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        assertTrue(versionCode > 0L)
        assertFalse(packageInfo.versionName.isNullOrBlank())
    }
}
