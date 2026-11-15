package com.nexus.app

import android.app.Activity
import android.os.Bundle

class EditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(NexusCanvas(this))
    }
}
