package com.example.gamified

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    // Simple but strict email regex and strong password policy
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PASSWORD_REGEX = Regex(
        pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etEmail: TextInputEditText = findViewById(R.id.etSignupEmail)
        val etPassword: TextInputEditText = findViewById(R.id.etSignupPassword)
        val etConfirm: TextInputEditText = findViewById(R.id.etSignupConfirm)
        val tilEmail: TextInputLayout = findViewById(R.id.tilSignupEmail)
        val tilPassword: TextInputLayout = findViewById(R.id.tilSignupPassword)
        val tilConfirm: TextInputLayout = findViewById(R.id.tilSignupConfirm)
        val btnCreate: MaterialButton = findViewById(R.id.btnCreateAccount)
        val linkLogin: LinearLayout = findViewById(R.id.linkLogin)

        btnCreate.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val pass = etPassword.text?.toString()?.trim().orEmpty()
            val confirm = etConfirm.text?.toString()?.trim().orEmpty()

            var ok = true
            if (!EMAIL_REGEX.matches(email)) {
                tilEmail.error = "Enter a valid email (e.g. user@example.com)"
                ok = false
            } else tilEmail.error = null

            if (!PASSWORD_REGEX.matches(pass)) {
                tilPassword.error = "Password must be 8+ chars with upper, lower, digit, special"
                ok = false
            } else tilPassword.error = null

            if (confirm != pass) {
                tilConfirm.error = "Passwords do not match"
                ok = false
            } else tilConfirm.error = null

            if (!ok) return@setOnClickListener

            // Disable button during sign-up
            btnCreate.isEnabled = false
            btnCreate.text = "Creating..."

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val uid = user?.uid
                        if (uid == null) {
                            restoreButton(btnCreate)
                            Toast.makeText(this, "Unexpected error: missing user id", Toast.LENGTH_LONG).show()
                            return@addOnCompleteListener
                        }

                        val data = hashMapOf(
                            "email" to email,
                            "createdAt" to System.currentTimeMillis()
                        )
                        db.collection("users").document(uid)
                            .set(data)
                            .addOnCompleteListener { t ->
                                restoreButton(btnCreate)
                                if (t.isSuccessful) {
                                    // Mark session as logged in
                                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("logged_in", true)
                                        .apply()
                                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, MainActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this, "Failed to save user: ${t.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        restoreButton(btnCreate)
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        linkLogin.setOnClickListener { finish() }

        // Clear errors on input
        etEmail.addTextChangedListener { tilEmail.error = null }
        etPassword.addTextChangedListener { tilPassword.error = null }
        etConfirm.addTextChangedListener { tilConfirm.error = null }
    }
    private fun restoreButton(btn: MaterialButton) {
        btn.isEnabled = true
        btn.text = "Create Account"
    }
}
