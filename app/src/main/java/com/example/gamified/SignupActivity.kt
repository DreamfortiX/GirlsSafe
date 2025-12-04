package com.example.gamified

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Patterns
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.bumptech.glide.Glide
import com.example.gamified.R
import com.example.gamified.databinding.ActivitySignupBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import androidx.core.content.edit

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var storageRef: StorageReference
    private var selectedImageUri: Uri? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var profileImageView: ImageView
    // Simple but strict email regex and strong password policy
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PASSWORD_REGEX = Regex(
        pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$"
    )
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImagePickerDialog()
        } else {
            Toast.makeText(
                this,
                "Permission is required to select profile pictures",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .into(profileImageView)
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri?.let { uri ->
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .into(profileImageView)
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        storageRef = storage.reference
        
        // Initialize views
        progressBar = findViewById(R.id.profileImageProgress)
        profileImageView = findViewById(R.id.profileImageView)
        
        // Set up profile image click listener
        findViewById<CardView>(R.id.profileImageContainer).setOnClickListener {
            checkPermissionAndPickImage()
        }
        
        // Set up upload button click listener
        findViewById<View>(R.id.btnUploadPhoto).setOnClickListener {
            checkPermissionAndPickImage()
        }

        val etEmail: TextInputEditText = findViewById(R.id.etSignupEmail)
        val etName : TextInputEditText = findViewById(R.id.etSignupName)
        val etPassword: TextInputEditText = findViewById(R.id.etSignupPassword)
        val etConfirm: TextInputEditText = findViewById(R.id.etSignupConfirm)
        val tilEmail: TextInputLayout = findViewById(R.id.tilSignupEmail)
        val tilPassword: TextInputLayout = findViewById(R.id.tilSignupPassword)
        val tilConfirm: TextInputLayout = findViewById(R.id.tilSignupConfirm)
        val tilName: TextInputLayout = findViewById(R.id.tilSignupName)
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

                        // Upload profile image if selected
                        selectedImageUri?.let { imageUri ->
                            uploadProfileImage(uid, imageUri) { imageUrl ->
                                saveUserData(uid, email, imageUrl, btnCreate)
                            }
                        } ?: run {
                            // No image selected, save user without profile image
                            saveUserData(uid, email, null, btnCreate)
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
    private fun checkPermissionAndPickImage() {
        when {
            // For Android 13+ (API 33+)
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED -> {
                showImagePickerDialog()
            }
            // For Android 10-12
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                showImagePickerDialog()
            }
            // If we should show explanation
            shouldShowRequestPermissionRationale(android.Manifest.permission.READ_MEDIA_IMAGES) ||
            shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                showPermissionExplanationDialog()
            }
            // Request the permission
            else -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf<CharSequence>("Take Photo", "Choose from Gallery", "Cancel")
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Add Photo")
        builder.setItems(options) { dialog, item ->
            when (options[item].toString()) {
                "Take Photo" -> {
                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    selectedImageUri = createImageFile()
                    selectedImageUri?.let { uri ->
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        takePictureLauncher.launch(takePictureIntent)
                    }
                }
                "Choose from Gallery" -> {
                    val pickIntent = Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    )
                    pickIntent.type = "image/*"
                    pickImageLauncher.launch(pickIntent)
                }
            }
            dialog.dismiss()
        }
        builder.show()
    }

    private fun createImageFile(): Uri? {
        val storageDir = getExternalFilesDir("profile_images")
        val file = createTempFile(
            "IMG_",
            ".jpg",
            storageDir
        )
        return Uri.fromFile(file)
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Needed")
            .setMessage("This app needs access to your photos to let you choose a profile picture.")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uploadProfileImage(userId: String, imageUri: Uri, onComplete: (String?) -> Unit) {
        progressBar.visibility = View.VISIBLE
        val imageRef = storageRef.child("profile_images/${userId}.jpg")
        
        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    progressBar.visibility = View.GONE
                    onComplete(uri.toString())
                }.addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to get image URL: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete(null)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete(null)
            }
    }

    private fun saveUserData(userId: String, email: String, imageUrl: String?, btn: MaterialButton) {
        val name = findViewById<TextInputEditText>(R.id.etSignupName).text?.toString()?.trim()
        val userData = hashMapOf(
            "email" to email,
            "name" to name,
            "createdAt" to System.currentTimeMillis(),
            "profileImageUrl" to imageUrl
        )

        db.collection("users").document(userId)
            .set(userData)
            .addOnCompleteListener { task ->
                restoreButton(btn)
                if (task.isSuccessful) {
                    // Mark session as logged in
                    getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit {
                            putBoolean("logged_in", true)
                        }
                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Failed to save user: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun restoreButton(btn: MaterialButton) {
        btn.isEnabled = true
        btn.text = "Create Account"
    }
}
