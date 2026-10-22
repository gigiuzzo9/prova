package com.simplylauncher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apri direttamente la HomeActivity
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
