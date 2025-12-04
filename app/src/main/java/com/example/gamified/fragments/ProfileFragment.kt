package com.example.gamified.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.gamified.LoginActivity
import com.example.gamified.R
import com.example.gamified.databinding.FragmentProfileBinding
import com.example.gamified.viewmodels.ProfileViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import androidx.core.content.edit

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        auth = Firebase.auth
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe user data changes
        observeUserData()

        // Load user data
        loadUserData()

        setupClickListeners()
    }

    private fun observeUserData() {
        viewModel.userData.observe(viewLifecycleOwner) { user ->
            user?.let {
                updateUI(user)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showLoading()
            } else {
                hideLoading()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModel.loadUserData(currentUser.uid)
        } else {
            // User not logged in, navigate to login
            navigateToLogin()
        }
    }

    private fun updateUI(user: Map<String, Any>) {
        // Display user information
        binding.userEmail.text = user["email"]?.toString() ?: "No Email"

        // Display user's name with fallbacks
        val name = user["name"]?.toString()?.takeIf { it.isNotBlank() }
            ?: user["displayName"]?.toString()?.takeIf { it.isNotBlank() }
            ?: user["username"]?.toString()
            ?: "No Name"
        
        binding.userName.text = name

        // Load profile image using Glide
        user["profileImageUrl"]?.toString()?.let { imageUrl ->
            if (imageUrl.isNotBlank()) {
                Glide.with(this)
                    .load(imageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile_placeholder) // Add a placeholder in your drawable
                    .error(R.drawable.ic_person) // Add error placeholder
                    .into(binding.profileImage)
            }
        }

        // Display join date if available
        user["createdAt"]?.let { createdAt ->
            val date = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(createdAt.toString().toLong()))
            binding.memberSince.text = "Member since $date"
        } ?: run {
            binding.memberSince.text = "Member information not available"
        }

        // Display statistics if available
        user["points"]?.toString()?.let { points ->
            binding.points.text = "Points: $points"
            binding.points.visibility = View.VISIBLE
        }

        user["level"]?.toString()?.let { level ->
            binding.level.text = "Level: $level"
            binding.level.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        // Edit Profile
        binding.root.findViewById<View>(R.id.edit_profile).setOnClickListener {
            showEditProfileDialog()
        }

        // Change Password
        binding.changePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // Help & Support
        binding.helpSupport.setOnClickListener {
            showHelpSupportDialog()
        }

        // About
        binding.about.setOnClickListener {
            showAboutDialog()
        }

        // Logout
        binding.logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        // Refresh user data (optional)
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadUserData()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.userInfoContainer.visibility = View.GONE
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.userInfoContainer.visibility = View.VISIBLE
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.are_you_sure_you_want_to_logout))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                performLogout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performLogout() {
        showLoading()

        // Sign out from Firebase
        auth.signOut()

        // Update login status to false in shared preferences
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("logged_in", false)
        }

        // Navigate to LoginActivity and clear the back stack
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.currentPassword)
        val newPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.newPassword)
        val confirmPasswordInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.confirmNewPassword)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.change_password)
            .setView(dialogView)
            .setPositiveButton(R.string.save, null) // We'll override this later
            .setNegativeButton(R.string.cancel, null)
            .create()
            
        dialog.setOnShowListener {
            val positiveButton = (it as androidx.appcompat.app.AlertDialog).getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener { _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()
                
                when {
                    currentPassword.isBlank() -> {
                        currentPasswordInput.error = "Current password is required"
                        return@setOnClickListener
                    }
                    newPassword.isBlank() -> {
                        newPasswordInput.error = "New password is required"
                        return@setOnClickListener
                    }
                    newPassword.length < 8 -> {
                        newPasswordInput.error = "Password must be at least 8 characters"
                        return@setOnClickListener
                    }
                    newPassword != confirmPassword -> {
                        confirmPasswordInput.error = "Passwords do not match"
                        return@setOnClickListener
                    }
                    else -> {
                        changePassword(currentPassword, newPassword) { success, message ->
                            if (success) {
                                dialog.dismiss()
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            } else {
                                currentPasswordInput.error = message
                            }
                        }
                    }
                }
            }
        }
        
        dialog.show()
    }

    private fun changePassword(
        currentPassword: String,
        newPassword: String,
        callback: (Boolean, String) -> Unit
    ) {
        val user = auth.currentUser
        val email = user?.email
        
        if (user == null || email.isNullOrEmpty()) {
            callback(false, "User not authenticated")
            return
        }
        
        showLoading()
        
        try {
            Log.d("PasswordChange", "Starting password change process for user: ${user.uid}")
            
            // Create credential with current email and password
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
            
            // Step 1: Re-authenticate the user
            user.reauthenticate(credential)
                .addOnCompleteListener(requireActivity()) { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        Log.d("PasswordChange", "Re-authentication successful")
                        
                        // Step 2: Update the password
                        user.updatePassword(newPassword)
                            .addOnCompleteListener(requireActivity()) { updateTask ->
                                hideLoading()
                                
                                if (updateTask.isSuccessful) {
                                    Log.d("PasswordChange", "Password updated successfully")
                                    // Show success message
                                    callback(true, "Password updated successfully!")
                                    
                                    // Sign out the user
                                    auth.signOut()
                                    
                                    // Navigate to login screen after a short delay
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        navigateToLogin()
                                    }, 1000)
                                    
                                } else {
                                    val error = updateTask.exception?.message ?: "Failed to update password"
                                    Log.e("PasswordChange", "Password update failed: $error")
                                    callback(false, "Failed to update password: $error")
                                }
                            }
                    } else {
                        hideLoading()
                        val error = reauthTask.exception?.message ?: "Incorrect current password"
                        Log.e("PasswordChange", "Re-authentication failed: $error")
                        callback(false, "Authentication failed: $error")
                    }
                }
        } catch (e: Exception) {
            hideLoading()
            val error = e.message ?: "An unknown error occurred"
            Log.e("PasswordChange", "Exception during password change: $error")
            callback(false, "Error: $error")
        }
    }

    private fun showHelpSupportDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.help_support)
            .setMessage(R.string.contact_support)
            .setPositiveButton(R.string.email) { dialog, _ ->
                // Open email client
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("support@gamified.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Gamified App Support")
                }
                startActivity(Intent.createChooser(emailIntent, "Send email..."))
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAboutDialog() {
        val versionName = try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName
        } catch (e: Exception) {
            "1.0.0"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about)
            .setMessage("Gamified App v$versionName\n\nA gamified learning platform that makes education fun!")
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditProfileDialog() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.userData.value?.let { user ->
            val dialogBinding = com.example.gamified.databinding.DialogEditProfileBinding.inflate(layoutInflater)

            // Pre-fill current values
            dialogBinding.etDisplayName.setText(user["displayName"]?.toString() ?: "")
            dialogBinding.etUsername.setText(user["username"]?.toString() ?: "")
            dialogBinding.etBio.setText(user["bio"]?.toString() ?: "")

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit_profile)
                .setView(dialogBinding.root)
                .setPositiveButton(R.string.save) { dialog, _ ->
                    val displayName = dialogBinding.etDisplayName.text.toString().trim()
                    val username = dialogBinding.etUsername.text.toString().trim()
                    val bio = dialogBinding.etBio.text.toString().trim()

                    if (displayName.isNotEmpty() || username.isNotEmpty() || bio.isNotEmpty()) {
                        updateProfile(displayName, username, bio)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun updateProfile(displayName: String, username: String, bio: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModel.updateUserProfile(currentUser.uid, displayName, username, bio)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh user data when fragment resumes
        loadUserData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = ProfileFragment()
    }
}