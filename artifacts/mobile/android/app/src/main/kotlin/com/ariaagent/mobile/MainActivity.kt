package com.ariaagent.mobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity — stub kept alive for Phase 1 of the native migration.
 *
 * The launcher has been transferred to ComposeMainActivity. This class
 * remains in the manifest as a non-launcher entry so it can be removed
 * cleanly in Phase 8 (mass deletion) without requiring a separate
 * manifest edit at that time.
 *
 * DO NOT add logic here. DO NOT set it as the launcher.
 * It will be DELETED in Phase 8 of migration.md.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
