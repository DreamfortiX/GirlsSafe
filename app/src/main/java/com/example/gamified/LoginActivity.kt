package com.example.gamified

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FacebookAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import androidx.activity.result.contract.ActivityResultContracts

class LoginActivity : AppCompatActivity() {
    // Simple but strict email regex and strong password policy
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PASSWORD_REGEX = Regex(
        pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$"
    )
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener(this) { signInTask ->
                        if (signInTask.isSuccessful) {
                            getSharedPreferences("app_prefs", MODE_PRIVATE)
                                .edit().putBoolean("logged_in", true).apply()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "Google sign-in failed: ${signInTask.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Google ID token not found", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google sign-in canceled", Toast.LENGTH_SHORT).show()
            Log.e("LoginActivity", "Google sign-in error", e)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Facebook Callback Manager
        callbackManager = CallbackManager.Factory.create()

        val etEmail: TextInputEditText = findViewById(R.id.etEmail)
        val etPassword: TextInputEditText = findViewById(R.id.etPassword)
        val tilEmail: TextInputLayout = findViewById(R.id.tilEmail)
        val tilPassword: TextInputLayout = findViewById(R.id.tilPassword)
        val btnLogin: MaterialButton = findViewById(R.id.btnLogin)
        val linkSignup: LinearLayout = findViewById(R.id.linkSignup)
        val btnGoogle: ImageView = findViewById(R.id.btnGoogle)
        val btnFacebook: ImageView = findViewById(R.id.btnFacebook)
        val tvForgot: TextView = findViewById(R.id.tvForgot)

        btnLogin.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val pass = etPassword.text?.toString()?.trim().orEmpty()

            var ok = true
            if (!EMAIL_REGEX.matches(email)) {
                tilEmail.error = "Enter a valid email (e.g. user@example.com)"
                ok = false
            } else tilEmail.error = null

            if (!PASSWORD_REGEX.matches(pass)) {
                tilPassword.error = "Password must be 8+ chars with upper, lower, digit, special"
                ok = false
            } else tilPassword.error = null

            if (!ok) return@setOnClickListener

            // Disable to prevent double taps
            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this) { task ->
                    // Restore button state
                    btnLogin.isEnabled = true
                    btnLogin.text = "Login"

                    if (task.isSuccessful) {
                        // Mark session as logged in
                        getSharedPreferences("app_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("logged_in", true)
                            .apply()
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Forgot Password flow (dialog)
        tvForgot.setOnClickListener {
            val inputLayout = TextInputLayout(this).apply {
                isErrorEnabled = true
                setPadding(24, 8, 24, 0)
            }
            val input = TextInputEditText(inputLayout.context).apply {
                hint = "Email"
                inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                setText(etEmail.text)
            }
            inputLayout.addView(input)

            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle("Reset password")
                .setMessage("Enter your account email to receive a reset link.")
                .setView(inputLayout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", null)
                .create()

            dialog.setOnShowListener {
                val btnSend = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                btnSend.setOnClickListener {
                    val email = input.text?.toString()?.trim().orEmpty()
                    if (!EMAIL_REGEX.matches(email)) {
                        inputLayout.error = "Enter a valid email (e.g. user@example.com)"
                        input.requestFocus()
                        return@setOnClickListener
                    } else inputLayout.error = null

                    btnSend.isEnabled = false
                    auth.sendPasswordResetEmail(email)
                        .addOnCompleteListener(this) { task ->
                            btnSend.isEnabled = true
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Password reset email sent", Toast.LENGTH_LONG).show()
                                dialog.dismiss()
                            } else {
                                Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            }

            dialog.show()
        }

        linkSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        btnGoogle.setOnClickListener {
            val intent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(intent)
        }
        btnFacebook.setOnClickListener {
            LoginManager.getInstance().logInWithReadPermissions(this, listOf("email", "public_profile"))
            LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val token = result.accessToken
                    val credential = FacebookAuthProvider.getCredential(token.token)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener(this@LoginActivity) { task ->
                            if (task.isSuccessful) {
                                getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    .edit().putBoolean("logged_in", true).apply()
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, "Facebook login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }

                override fun onCancel() {
                    Toast.makeText(this@LoginActivity, "Facebook login canceled", Toast.LENGTH_SHORT).show()
                }

                override fun onError(error: FacebookException) {
                    Toast.makeText(this@LoginActivity, "Facebook error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }

        // Clear errors on text change
        etEmail.addTextChangedListener { tilEmail.error = null }
        etPassword.addTextChangedListener { tilPassword.error = null }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Forward to Facebook SDK
        if (::callbackManager.isInitialized) {
            callbackManager.onActivityResult(requestCode, resultCode, data)
        }
    }
}
