package com.manjano.bus.legacy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.manjano.bus.MainActivity
import com.manjano.bus.R
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// --- FrameLayout animations ---
fun FrameLayout.show() { isVisible = true }
fun FrameLayout.hide() { isVisible = false }
fun FrameLayout.fadeIn(duration: Long = 250) { animate().alpha(1f).setDuration(duration).start() }
fun FrameLayout.fadeOut(duration: Long = 150, onEnd: (() -> Unit)? = null) {
    animate().alpha(0f).setDuration(duration).withEndAction { onEnd?.invoke() }.start()
}

@OptIn(FlowPreview::class)
class ProfileSetupActivity : AppCompatActivity() {

    private val viewModel: ProfileSetupViewModel by viewModels()

    private lateinit var fullNameEditText: EditText
    private lateinit var saveProfileButton: Button
    private lateinit var progressOverlayContainer: FrameLayout
    private lateinit var progressOverlay: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_setup)

        initializeUI()
        setupAccessibility()
        setupTextWatcher()
        observeUiEvents()
        setupSaveButton()
    }

    private fun initializeUI() {
        fullNameEditText = findViewById(R.id.editTextFullName)
        saveProfileButton = findViewById(R.id.buttonSaveProfile)
        progressOverlayContainer = findViewById(R.id.progressOverlayContainer)
        progressOverlay = findViewById(R.id.progressOverlay)

        progressOverlayContainer.alpha = 0f
        progressOverlayContainer.hide()
        progressOverlayContainer.isClickable = true
        progressOverlayContainer.isFocusable = true
        progressOverlayContainer.isFocusableInTouchMode = true
    }

    private fun setupAccessibility() {
        saveProfileButton.contentDescription = getString(R.string.save_profile_button_desc)
        progressOverlay.contentDescription = getString(R.string.loading_indicator_desc)
        progressOverlayContainer.contentDescription = getString(R.string.blocking_overlay_desc)
        fullNameEditText.contentDescription = getString(R.string.full_name_input_desc)
    }

    private fun setupTextWatcher() {
        val textChanges = MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        fullNameEditText.doAfterTextChanged { text ->
            fullNameEditText.error = null
            textChanges.tryEmit(text?.toString()?.trim() ?: "")
        }

        lifecycleScope.launch {
            textChanges
                .debounce(300)
                .distinctUntilChanged()
                .collect { trimmedText ->
                    viewModel.setFullName(trimmedText)
                }
        }
    }

    private fun observeUiEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event -> handleUiEvent(event) }
            }
        }
    }

    private fun handleUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.UpdateLoadingState -> updateLoadingUI(event.isLoading)
            is UiEvent.ShowToast -> if (!isFinishing && !isDestroyed)
                Toast.makeText(this, event.message, event.duration).show()
            is UiEvent.SetEditTextError -> {
                fullNameEditText.error = event.errorMessage
                fullNameEditText.requestFocus()
            }
            is UiEvent.ClearEditTextError -> fullNameEditText.error = null
            is UiEvent.NavigateToMain -> navigateToMain()
            is UiEvent.FinishActivity -> finish()
        }
    }

    private fun setupSaveButton() {
        saveProfileButton.setOnClickListener {
            val name = fullNameEditText.text.toString().trim()
            if (name.isEmpty()) {
                fullNameEditText.error = getString(R.string.error_full_name_required)
                fullNameEditText.requestFocus()
            } else {
                viewModel.saveUserProfile() // ✅ uses internal private fullName
            }
        }
    }

    private fun updateLoadingUI(loading: Boolean) {
        saveProfileButton.apply {
            isEnabled = !loading
            text = if (loading) getString(R.string.saving) else getString(R.string.save_profile)
        }
        fullNameEditText.isEnabled = !loading

        if (loading) {
            progressOverlayContainer.show()
            progressOverlayContainer.fadeIn()
        } else {
            progressOverlayContainer.fadeOut { progressOverlayContainer.hide() }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }
}
