package com.manjano.bus.legacy

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.manjano.bus.MainActivity

class OtpVerificationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If this activity handled OTP logic, you'd verify it here

        // For now, simulate success and jump to Compose MainActivity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
