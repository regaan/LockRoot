package com.regaan.lockroot

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.regaan.lockroot.crypto.AuthenticationFailedException
import com.regaan.lockroot.crypto.CryptoException
import com.regaan.lockroot.security.ClipboardGuard
import com.regaan.lockroot.ui.SecurityIllustrationView
import com.regaan.lockroot.vault.PasswordGenerator
import com.regaan.lockroot.vault.Vault
import com.regaan.lockroot.vault.VaultEntry
import com.regaan.lockroot.vault.VaultRepository
import com.regaan.lockroot.vault.VaultStorage
import java.util.Arrays

class MainActivity : Activity() {
    private lateinit var repository: VaultRepository
    private lateinit var clipboardGuard: ClipboardGuard
    private val passwordGenerator = PasswordGenerator()
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityLock = Runnable {
        if (!externalActivityOpen && repository.unlockedVault != null) {
            lockVault("Locked after inactivity.")
        }
    }

    private var externalActivityOpen = false
    private var renderLockedOnResume = false
    private var pendingExportPassword: CharArray? = null
    private var pendingImportUri: Uri? = null
    private var activeScrollView: ScrollView? = null
    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var failedUnlockAttempts = 0
    private var unlockBlockedUntilMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        repository = VaultRepository(VaultStorage(this))
        clipboardGuard = ClipboardGuard(this)

        if (repository.hasVault()) {
            showUnlockScreen()
        } else {
            showSetupScreen()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!externalActivityOpen && repository.unlockedVault != null) {
            lockVaultForBackground()
        }
    }

    override fun onDestroy() {
        cancelInactivityLock()
        cleanupScreenListener()
        wipePendingExportPassword()
        pendingImportUri = null
        activeScrollView = null

        if (::repository.isInitialized) {
            repository.lock()
        }
        if (::clipboardGuard.isInitialized) {
            clipboardGuard.destroy()
        }

        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (renderLockedOnResume) {
            renderLockedOnResume = false
            showUnlockScreen("Locked")
        } else if (repository.unlockedVault != null) {
            scheduleInactivityLock()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (repository.unlockedVault != null && !externalActivityOpen) {
            scheduleInactivityLock()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        externalActivityOpen = false

        if (resultCode != RESULT_OK) {
            wipePendingExportPassword()
            if (repository.unlockedVault != null) scheduleInactivityLock()
            return
        }

        when (requestCode) {
            REQUEST_EXPORT -> handleExportResult(data?.data)
            REQUEST_IMPORT -> {
                pendingImportUri = data?.data
                showImportPasswordDialog()
            }
        }
        if (repository.unlockedVault != null) scheduleInactivityLock()
    }

    private fun showSetupScreen() {
        val content = screen()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(illustration(SecurityIllustrationView.Mode.SAFE, responsiveHeroHeightDp()))
        pageTitle(content, "Set up your vault", "Create a master password to encrypt and protect your data.")

        val password = passwordInput("Master password")
        val confirm = passwordInput("Confirm password")
        val termsAccepted = CheckBox(this).apply {
            text = "I have read and agree to the Terms and Conditions and Privacy Policy."
            textSize = 13f
            setTextColor(color(R.color.app_text))
            setPadding(0, dp(4), 0, dp(8))
        }
        content.addView(card().apply {
            addView(fieldRow(R.drawable.ic_lock, "MASTER PASSWORD", password))
            addView(passwordMeter(password))
            addView(checkList())
            addView(divider())
            addView(fieldRow(R.drawable.ic_lock, "CONFIRM PASSWORD", confirm))
            addView(statusLine("Passwords must match before creating the vault."))
        })

        content.addView(infoCard("Important", "Your master password is the only way to access your vault. There is no recovery."))
        content.addView(card().apply {
            addView(termsAccepted)
            addView(horizontal().apply {
                addView(secondaryButton("Terms") {
                    showLegalTextDialog("Terms and Conditions", TERMS_TEXT)
                })
                addView(secondaryButton("Privacy") {
                    showLegalTextDialog("Privacy Policy", PRIVACY_TEXT)
                })
            })
        })
        content.addView(primaryButton("Create Vault") {
            val first = password.text.toString().toCharArray()
            val second = confirm.text.toString().toCharArray()
            try {
                if (!termsAccepted.isChecked) {
                    toast("Accept Terms and Privacy Policy first.")
                    return@primaryButton
                }
                if (first.size < 12) {
                    toast("Use at least 12 characters.")
                    return@primaryButton
                }
                if (!first.contentEquals(second)) {
                    toast("Passwords do not match.")
                    return@primaryButton
                }
                repository.create(first)
                wipePasswordField(password)
                wipePasswordField(confirm)
                scheduleInactivityLock()
                showVaultScreen()
            } catch (error: Exception) {
                toast(error.safeMessage())
            } finally {
                Arrays.fill(first, '\u0000')
                Arrays.fill(second, '\u0000')
            }
        })
    }

    private fun showUnlockScreen(message: String? = null) {
        val content = screen()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(illustration(SecurityIllustrationView.Mode.LOCK, responsiveHeroHeightDp()))
        pageTitle(content, "Unlock vault", "Enter your master password to continue.")
        message?.let { content.addView(statusLine(it)) }

        val password = passwordInput("Master password")
        password.imeOptions = EditorInfo.IME_ACTION_DONE
        content.addView(card().apply {
            addView(fieldRow(R.drawable.ic_lock, "MASTER PASSWORD", password))
            addView(primaryButton("Unlock") {
                unlockWithPassword(password)
            })
        })
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                unlockWithPassword(password)
                true
            } else {
                false
            }
        }
    }

    private fun showVaultScreen(query: String = "") {
        val vault = repository.unlockedVault ?: return showUnlockScreen()
        val content = screen()
        content.addView(homeHeader(vault))

        val search = input("Search your entries...", query)
        content.addView(searchBox(search))
        content.addView(actionGrid(search))

        val filtered = vault.entries
            .filter { entry -> entry.matches(query) }
            .sortedBy { it.title.lowercase() }

        if (filtered.isEmpty()) {
            content.addView(emptyState(vault.entries.isEmpty()))
        } else {
            content.addView(sectionLabel("Entries"))
            filtered.forEach { entry -> content.addView(entryCard(entry)) }
        }

        content.addView(bottomNav("home"))
    }

    private fun showEntryEditor(entryId: String?) {
        val vault = repository.unlockedVault ?: return showUnlockScreen()
        val existing = entryId?.let { id -> vault.entries.firstOrNull { it.id == id } }

        val title = input("e.g. My Google Account", existing?.title.orEmpty())
        val website = input("e.g. google.com", existing?.website.orEmpty())
        val username = input("Enter username", existing?.username.orEmpty())
        val password = passwordInput("Enter password", existing?.password.orEmpty())
        val notes = input("Add a note...", existing?.notes.orEmpty(), lines = 3)
        val tags = input("Add tags", existing?.tags?.joinToString(", ").orEmpty())

        fun saveEntry() {
            val cleanTitle = title.text.toString().trim()
            if (cleanTitle.isEmpty()) {
                toast("Title is required.")
                return
            }

            val updated = existing ?: VaultEntry(
                title = cleanTitle,
                website = "",
                username = "",
                password = "",
                notes = "",
            ).also { vault.entries.add(it) }

            updated.title = cleanTitle
            updated.website = website.text.toString().trim()
            updated.username = username.text.toString().trim()
            updated.password = password.text.toString()
            updated.notes = notes.text.toString()
            updated.tags = tags.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            try {
                repository.save()
                showVaultScreen()
            } catch (error: Exception) {
                toast(error.safeMessage())
            }
        }

        val content = screen()
        content.addView(editorTopBar(if (existing == null) "Add Entry" else "Edit Entry", ::saveEntry))
        content.addView(card().apply {
            addView(fieldRow(R.drawable.ic_label, "TITLE", title))
            addView(divider())
            addView(fieldRow(R.drawable.ic_globe, "WEBSITE", website))
        })
        content.addView(card().apply {
            addView(fieldRow(R.drawable.ic_person, "USERNAME", username))
            addView(divider())
            addView(fieldRow(R.drawable.ic_lock, "PASSWORD", password, secondaryButton("Generate") {
                showPasswordGeneratorDialog("Use") { generated ->
                    password.setText(generated)
                    password.setSelection(password.text.length)
                }
            }))
            addView(passwordMeter(password))
        })
        content.addView(card().apply {
            addView(fieldRow(R.drawable.ic_note, "NOTES", notes))
            addView(divider())
            addView(fieldRow(R.drawable.ic_label, "TAGS", tags))
        })
        content.addView(infoCard("Your data is always protected", "Entries are encrypted locally before they are written to disk."))
        content.addView(primaryButton("Save Entry") { saveEntry() })

        if (existing != null) {
            content.addView(dangerButton("Delete Entry") {
                AlertDialog.Builder(this)
                    .setTitle("Delete entry")
                    .setMessage("Delete ${existing.title}?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        vault.entries.removeAll { it.id == existing.id }
                        try {
                            repository.save()
                            showVaultScreen()
                        } catch (error: Exception) {
                            toast(error.safeMessage())
                        }
                    }
                    .show()
            })
        }
    }

    private fun showExportPasswordDialog() {
        val wrapper = dialogBody()
        val password = passwordInput("Export password")
        val confirm = passwordInput("Confirm export password")
        wrapper.addView(fieldRow(R.drawable.ic_key, "EXPORT PASSWORD", password))
        wrapper.addView(fieldRow(R.drawable.ic_key, "CONFIRM PASSWORD", confirm))

        AlertDialog.Builder(this)
            .setTitle("Encrypted export")
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                val first = password.text.toString().toCharArray()
                val second = confirm.text.toString().toCharArray()
                try {
                    if (first.size < 12) {
                        toast("Use at least 12 characters.")
                        return@setPositiveButton
                    }
                    if (!first.contentEquals(second)) {
                        toast("Passwords do not match.")
                        return@setPositiveButton
                    }
                    pendingExportPassword = first.copyOf()
                    openExportDocument()
                } finally {
                    Arrays.fill(first, '\u0000')
                    Arrays.fill(second, '\u0000')
                    wipePasswordField(password)
                    wipePasswordField(confirm)
                }
            }
            .show()
    }

    private fun showChangeMasterPasswordDialog() {
        val wrapper = dialogBody()
        val current = passwordInput("Current master password")
        val next = passwordInput("New master password")
        val confirm = passwordInput("Confirm new master password")
        wrapper.addView(fieldRow(R.drawable.ic_lock, "CURRENT PASSWORD", current))
        wrapper.addView(fieldRow(R.drawable.ic_key, "NEW PASSWORD", next))
        wrapper.addView(fieldRow(R.drawable.ic_key, "CONFIRM PASSWORD", confirm))

        AlertDialog.Builder(this)
            .setTitle("Change master password")
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Change") { _, _ ->
                val currentPassword = current.text.toString().toCharArray()
                val newPassword = next.text.toString().toCharArray()
                val confirmation = confirm.text.toString().toCharArray()
                try {
                    if (newPassword.size < 12) {
                        toast("Use at least 12 characters.")
                        return@setPositiveButton
                    }
                    if (!newPassword.contentEquals(confirmation)) {
                        toast("Passwords do not match.")
                        return@setPositiveButton
                    }
                    repository.changeMasterPassword(currentPassword, newPassword)
                    toast("Master password changed.")
                    showVaultScreen()
                } catch (_: AuthenticationFailedException) {
                    toast("Current password is wrong.")
                } catch (error: Exception) {
                    toast(error.safeMessage())
                } finally {
                    Arrays.fill(currentPassword, '\u0000')
                    Arrays.fill(newPassword, '\u0000')
                    Arrays.fill(confirmation, '\u0000')
                    wipePasswordField(current)
                    wipePasswordField(next)
                    wipePasswordField(confirm)
                }
            }
            .show()
    }

    private fun showSettingsScreen() {
        val vault = repository.unlockedVault ?: return showUnlockScreen()
        val content = screen()
        content.addView(simpleTopBar("Settings") { showVaultScreen() })
        content.addView(infoCard("Lockroot settings", "Manage local vault security, encrypted backups, and sensitive data controls."))

        content.addView(sectionLabel("Vault"))
        content.addView(card().apply {
            addView(settingsRow(R.drawable.ic_shield, "Security center", "Review encryption and device protections") {
                showSecurityCenterScreen(returnToSettings = true)
            })
            addView(divider())
            addView(settingsRow(R.drawable.ic_key, "Master password", "Change the password that unlocks this vault") {
                showChangeMasterPasswordDialog()
            })
            addView(divider())
            addView(settingsRow(R.drawable.ic_lock, "Lock vault", "Close the current unlocked session") {
                lockVault("Locked")
            })
        })

        content.addView(sectionLabel("Backup"))
        content.addView(card().apply {
            addView(settingsRow(R.drawable.ic_download, "Export encrypted vault", "Create a password-protected export file") {
                showExportPasswordDialog()
            })
            addView(divider())
            addView(settingsRow(R.drawable.ic_upload, "Import encrypted vault", "Decrypt and merge or replace from an export") {
                openImportDocument()
            })
        })

        content.addView(sectionLabel("Information"))
        content.addView(card().apply {
            addView(settingsRow(R.drawable.ic_info, "About Lockroot", "Creator profile, project purpose, and contact") {
                showAboutScreen()
            })
            addView(divider())
            addView(settingsRow(R.drawable.ic_shield, "Privacy Policy", "How local vault data is handled") {
                showPrivacyScreen()
            })
            addView(divider())
            addView(settingsRow(R.drawable.ic_note, "Terms and Conditions", "Use rules, no recovery, and backups") {
                showTermsScreen()
            })
        })

        content.addView(sectionLabel("Danger Zone"))
        content.addView(card().apply {
            addView(settingsRow(
                R.drawable.ic_trash,
                "Clear all entries",
                resources.getQuantityString(R.plurals.entry_count, vault.entries.size, vault.entries.size) +
                    " will be deleted from this vault",
                titleColor = color(R.color.app_danger),
                iconTint = color(R.color.app_danger),
            ) {
                showClearAllEntriesDialog()
            })
        })

        content.addView(bottomNav("settings"))
    }

    private fun showSecurityCenterScreen(returnToSettings: Boolean = false) {
        val content = screen()
        content.addView(simpleTopBar("Security center") {
            if (returnToSettings) showSettingsScreen() else showVaultScreen()
        })
        content.addView(card().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(horizontal().apply {
                addView(iconBubble(R.drawable.ic_shield, true))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = "Protected vault"
                        textSize = 20f
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(color(R.color.app_primary_dark))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "Local-only encryption with no recovery backdoor."
                        textSize = 13f
                        setTextColor(color(R.color.app_muted))
                        setPadding(0, dp(4), 0, 0)
                    })
                })
            })
        })

        content.addView(sectionLabel("Crypto"))
        content.addView(card().apply {
            addView(securityRow(R.drawable.ic_key, "Argon2id key derivation", "The master password is hardened before it becomes an encryption key."))
            addView(divider())
            addView(securityRow(R.drawable.ic_shield, "AES-256-GCM", "Wrong passwords and modified vault files fail authentication."))
            addView(divider())
            addView(securityRow(R.drawable.ic_download, "Encrypted export/import", "Export files use a separate password and a separate key."))
        })

        content.addView(sectionLabel("Device Protection"))
        content.addView(card().apply {
            addView(securityRow(R.drawable.ic_globe, "No internet permission", "Lockroot is built as a local-only password manager."))
            addView(divider())
            addView(securityRow(R.drawable.ic_lock, "Screenshot blocking", "Sensitive screens are protected with Android secure window flags."))
            addView(divider())
            addView(securityRow(R.drawable.ic_close, "Clipboard auto-clear", "Copied usernames and passwords are cleared after a short delay."))
        })

        content.addView(sectionLabel("Recovery"))
        content.addView(card().apply {
            addView(securityRow(R.drawable.ic_info, "No password recovery", "If the master password is lost, the vault cannot be decrypted."))
        })

        content.addView(bottomNav("settings"))
    }

    private fun showAboutScreen() {
        val content = screen()
        content.addView(simpleTopBar("About Lockroot") { showSettingsScreen() })
        content.addView(brandLogo())
        content.addView(card().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(horizontal().apply {
                addView(iconBubble(R.drawable.ic_shield, true))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = "Lockroot"
                        textSize = 24f
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(color(R.color.app_primary_dark))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = "Offline password manager by Regaan"
                        textSize = 14f
                        setTextColor(color(R.color.app_muted))
                        setPadding(0, dp(4), 0, 0)
                    })
                })
            })
        })

        content.addView(sectionLabel("Creator"))
        content.addView(documentCard("Regaan", ABOUT_CREATOR_TEXT))
        content.addView(sectionLabel("Security Work"))
        content.addView(documentCard("Research profile", ABOUT_RESEARCH_TEXT))
        content.addView(sectionLabel("Project"))
        content.addView(documentCard("Lockroot mission", ABOUT_PROJECT_TEXT))
        content.addView(bottomNav("settings"))
    }

    private fun showPrivacyScreen() {
        val content = screen()
        content.addView(simpleTopBar("Privacy Policy") { showSettingsScreen() })
        content.addView(documentCard("Summary", PRIVACY_SUMMARY_TEXT))
        content.addView(documentCard("Data stored on device", PRIVACY_LOCAL_DATA_TEXT))
        content.addView(documentCard("Clipboard and exports", PRIVACY_EXPORT_TEXT))
        content.addView(documentCard("No recovery", PRIVACY_RECOVERY_TEXT))
        content.addView(bottomNav("settings"))
    }

    private fun showTermsScreen() {
        val content = screen()
        content.addView(simpleTopBar("Terms and Conditions") { showSettingsScreen() })
        content.addView(documentCard("Acceptance", TERMS_ACCEPTANCE_TEXT))
        content.addView(documentCard("User responsibilities", TERMS_RESPONSIBILITY_TEXT))
        content.addView(documentCard("Security limits", TERMS_SECURITY_LIMITS_TEXT))
        content.addView(documentCard("No warranty", TERMS_WARRANTY_TEXT))
        content.addView(bottomNav("settings"))
    }

    private fun showLegalTextDialog(title: String, body: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showClearAllEntriesDialog() {
        val vault = repository.unlockedVault ?: return showUnlockScreen()
        if (vault.entries.isEmpty()) {
            toast("No entries to clear.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clear all entries?")
            .setMessage("This deletes every saved password entry in this vault. Encrypted exports are the only backup.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete all") { _, _ ->
                try {
                    vault.entries.clear()
                    repository.save()
                    toast("All entries cleared.")
                    showVaultScreen()
                } catch (error: Exception) {
                    toast(error.safeMessage())
                }
            }
            .show()
    }

    private fun openExportDocument() {
        externalActivityOpen = true
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "lockroot-export.lpexport")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun handleExportResult(uri: Uri?) {
        val exportPassword = pendingExportPassword ?: return
        try {
            if (uri == null) return
            val exportBytes = repository.exportUnlocked(exportPassword)
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(exportBytes)
                output.flush()
            } ?: error("Could not open export file.")
            toast("Encrypted export saved.")
            showVaultScreen()
        } catch (error: Exception) {
            toast(error.safeMessage())
        } finally {
            wipePendingExportPassword()
        }
    }

    private fun openImportDocument() {
        externalActivityOpen = true
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_IMPORT)
    }

    private fun showImportPasswordDialog() {
        val uri = pendingImportUri ?: return
        val password = passwordInput("Export password")
        val wrapper = dialogBody().apply {
            addView(fieldRow(R.drawable.ic_key, "EXPORT PASSWORD", password))
        }

        AlertDialog.Builder(this)
            .setTitle("Import encrypted vault")
            .setView(wrapper)
            .setNegativeButton("Cancel") { _, _ -> pendingImportUri = null }
            .setPositiveButton("Decrypt") { _, _ ->
                val typed = password.text.toString().toCharArray()
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read selected file.")
                    val imported = repository.decryptExport(bytes, typed)
                    showImportPreviewScreen(imported)
                } catch (_: AuthenticationFailedException) {
                    toast("Import password is wrong or file was modified.")
                } catch (error: Exception) {
                    toast(error.safeMessage())
                } finally {
                    Arrays.fill(typed, '\u0000')
                    wipePasswordField(password)
                    pendingImportUri = null
                }
            }
            .show()
    }

    private fun showImportPreviewScreen(imported: Vault) {
        repository.unlockedVault ?: return showUnlockScreen()
        val content = screen()
        content.addView(simpleTopBar("Preview import") { showSettingsScreen() })
        content.addView(infoCard(
            "Encrypted export decrypted",
            resources.getQuantityString(R.plurals.entry_count, imported.entries.size, imported.entries.size) +
                " found. Review before merging or replacing your current vault.",
        ))

        if (imported.entries.isEmpty()) {
            content.addView(infoCard("No entries in export", "This export decrypted successfully but does not contain saved entries."))
        } else {
            content.addView(sectionLabel("Entries in export"))
            imported.entries.sortedBy { it.title.lowercase() }.forEach { entry ->
                content.addView(importPreviewCard(entry))
            }
        }

        content.addView(primaryButton("Merge Into Vault") {
            mergeImportedVault(imported)
        })
        content.addView(dangerButton("Replace Current Vault") {
            showReplaceImportConfirmation(imported)
        })
    }

    private fun showReplaceImportConfirmation(imported: Vault) {
        AlertDialog.Builder(this)
            .setTitle("Replace current vault?")
            .setMessage("This removes current entries and saves the imported vault instead.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Replace") { _, _ ->
                repository.replaceUnlocked(imported.copyMutable())
                saveAfterImport()
            }
            .show()
    }

    private fun mergeImportedVault(imported: Vault) {
        repository.mergeUnlocked(imported)
        saveAfterImport()
    }

    private fun saveAfterImport() {
        try {
            repository.save()
            toast("Import saved.")
            showVaultScreen()
        } catch (error: Exception) {
            toast(error.safeMessage())
        }
    }

    private fun showPasswordGeneratorDialog(primaryLabel: String, onPrimary: (String) -> Unit) {
        val wrapper = dialogBody()
        val lengthLabel = TextView(this).apply {
            text = "Length: 24"
            textSize = 14f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(color(R.color.app_text))
            setPadding(0, dp(8), 0, dp(4))
        }
        val lengthSlider = SeekBar(this).apply {
            max = PASSWORD_GENERATOR_MAX_LENGTH - PASSWORD_GENERATOR_MIN_LENGTH
            progress = 24 - PASSWORD_GENERATOR_MIN_LENGTH
        }
        val lowercase = generatorCheckBox("Lowercase", true)
        val uppercase = generatorCheckBox("Uppercase", true)
        val numbers = generatorCheckBox("Numbers", true)
        val symbols = generatorCheckBox("Symbols", true)
        val generatedText = TextView(this).apply {
            textSize = 17f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(color(R.color.app_text))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedDrawable(color(R.color.app_field), dp(14), color(R.color.app_border), 1)
        }
        val status = TextView(this).apply {
            textSize = 12f
            setTextColor(color(R.color.app_muted))
            setPadding(0, dp(8), 0, 0)
        }
        val characterNote = TextView(this).apply {
            text = "Ambiguous characters like 0, O, 1, I, and l are avoided."
            textSize = 12f
            setTextColor(color(R.color.app_muted))
            setPadding(0, dp(2), 0, dp(8))
        }
        val regenerate = Button(this).apply {
            text = "Regenerate"
            isAllCaps = false
            setTextColor(color(R.color.app_primary))
            background = roundedDrawable(color(R.color.app_primary_soft), dp(12))
            backgroundTintList = null
        }

        var currentPassword = ""

        fun selectedLength(): Int = lengthSlider.progress + PASSWORD_GENERATOR_MIN_LENGTH

        fun generateSelected(): String? {
            return try {
                val generated = passwordGenerator.generate(
                    length = selectedLength(),
                    lowercase = lowercase.isChecked,
                    uppercase = uppercase.isChecked,
                    numbers = numbers.isChecked,
                    symbols = symbols.isChecked,
                )
                currentPassword = generated
                generatedText.text = generated
                status.text = evaluatePasswordStrength(generated).label
                status.setTextColor(evaluatePasswordStrength(generated).color)
                generated
            } catch (error: IllegalArgumentException) {
                currentPassword = ""
                generatedText.text = "Select at least one character group."
                status.text = error.message ?: "Generator options are invalid."
                status.setTextColor(color(R.color.app_danger))
                null
            }
        }

        fun refreshGenerated() {
            lengthLabel.text = "Length: ${selectedLength()}"
            generateSelected()
        }

        lengthSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = refreshGenerated()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        listOf(lowercase, uppercase, numbers, symbols).forEach { option ->
            option.setOnCheckedChangeListener { _, _ -> refreshGenerated() }
        }
        regenerate.setOnClickListener { refreshGenerated() }

        wrapper.addView(lengthLabel)
        wrapper.addView(lengthSlider)
        wrapper.addView(card().apply {
            addView(horizontal().apply {
                addView(lowercase)
                addView(uppercase)
            })
            addView(horizontal().apply {
                addView(numbers)
                addView(symbols)
            })
        })
        wrapper.addView(characterNote)
        wrapper.addView(generatedText)
        wrapper.addView(status)
        wrapper.addView(regenerate)
        refreshGenerated()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Password generator")
            .setView(wrapper)
            .setNegativeButton("Close", null)
            .setPositiveButton(primaryLabel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val generated = currentPassword.ifBlank { generateSelected().orEmpty() }
                if (generated.isBlank()) {
                    toast("Choose at least one character group.")
                    return@setOnClickListener
                }
                onPrimary(generated)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun generatorCheckBox(label: String, checked: Boolean): CheckBox =
        CheckBox(this).apply {
            text = label
            isChecked = checked
            textSize = 13f
            setTextColor(color(R.color.app_text))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

    private fun homeHeader(vault: Vault): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                colors = intArrayOf(color(R.color.app_header_start), color(R.color.app_header_end))
                orientation = GradientDrawable.Orientation.TL_BR
                setStroke(dp(1), color(R.color.app_border))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(4), 0, dp(12)) }
            addView(horizontal().apply {
                addView(iconBubble(R.drawable.ic_lock, true))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = getString(R.string.app_name)
                        textSize = when {
                            isCompactWidth() -> 24f
                            isTabletWidth() -> 30f
                            else -> 28f
                        }
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(color(R.color.app_primary_dark))
                        letterSpacing = -0.02f
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = getString(R.string.app_tagline)
                        textSize = 14f
                        setTextColor(color(R.color.app_muted))
                        setPadding(0, dp(2), 0, 0)
                    })
                })
                addView(pillButton("Lock", R.drawable.ic_lock) { lockVault("Locked") })
            })
            addView(entryCountBadge(vault))
        }

    private fun searchBox(search: EditText): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(color(R.color.app_surface), dp(16), color(R.color.app_border), 1)
            elevation = dp(1).toFloat()
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(4), 0, dp(14)) }
            addView(iconImage(R.drawable.ic_search, 20, color(R.color.app_muted)).apply {
                setPadding(0, 0, dp(10), 0)
            })
            addView(search.apply {
                background = null
                setHint("Search your entries...")
                minHeight = dp(44)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(iconImage(R.drawable.ic_filter, 20, color(R.color.app_muted)).apply {
                setPadding(dp(8), 0, 0, 0)
            })
        }

    private data class ActionSpec(
        val iconRes: Int,
        val title: String,
        val subtitle: String,
        val action: () -> Unit,
    )

    private fun actionGrid(search: EditText): View {
        val actions = listOf(
            ActionSpec(R.drawable.ic_search, "Search", "Find entries") { showVaultScreen(search.text.toString()) },
            ActionSpec(R.drawable.ic_add_circle, "Add", "Add new entry") { showEntryEditor(null) },
            ActionSpec(R.drawable.ic_sparkle, "Generate", "Generate password") {
                showPasswordGeneratorDialog("Copy") { generated ->
                    clipboardGuard.copySecret(generated)
                    toast("Copied.")
                }
            },
            ActionSpec(R.drawable.ic_download, "Export", "Export entries") { showExportPasswordDialog() },
            ActionSpec(R.drawable.ic_upload, "Import", "Import entries") { openImportDocument() },
            ActionSpec(R.drawable.ic_key, "Master", "Master password") { showChangeMasterPasswordDialog() },
            ActionSpec(R.drawable.ic_shield, "Security", "Security center") { showSecurityCenterScreen() },
        )
        val columns = responsiveActionColumns()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            actions.chunked(columns).forEach { rowActions ->
                addView(horizontal().apply {
                    rowActions.forEach { action ->
                        addView(actionTile(action.iconRes, action.title, action.subtitle, action.action))
                    }
                    repeat(columns - rowActions.size) {
                        addView(View(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(0, dp(responsiveActionTileHeightDp()), 1f)
                                .apply { setMargins(dp(4), dp(4), dp(4), dp(8)) }
                        })
                    }
                })
            }
        }
    }

    private fun actionTile(iconRes: Int, title: String, subtitle: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(14))
            background = roundedDrawable(color(R.color.app_surface), dp(16), color(R.color.app_border), 1)
            elevation = dp(1).toFloat()
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(responsiveActionTileHeightDp()),
                1f,
            ).apply { setMargins(dp(4), dp(4), dp(4), dp(8)) }
            addView(iconImage(iconRes, responsiveActionIconSizeDp(), color(R.color.app_primary)))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = if (isCompactWidth()) 14f else 15f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_text))
                setPadding(0, dp(10), 0, dp(2))
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = if (isCompactWidth()) 11f else 12f
                setTextColor(color(R.color.app_muted))
            })
        }

    private fun emptyState(firstRun: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(36), 0, dp(28))
            addView(illustration(SecurityIllustrationView.Mode.EMPTY, 140))
            addView(TextView(this@MainActivity).apply {
                text = if (firstRun) "No entries yet" else "No matching entries"
                textSize = 20f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_text))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = if (firstRun) "Add your first entry to get started." else "Try a different search."
                textSize = 14f
                setTextColor(color(R.color.app_muted))
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        }

    private fun entryCard(entry: VaultEntry): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = roundedDrawable(color(R.color.app_surface), dp(18), color(R.color.app_border), 1)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(4), 0, dp(8)) }
            addView(horizontal().apply {
                addView(iconBubble(entry.title.firstOrNull()?.uppercaseChar()?.toString() ?: "K", false))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = entry.title
                        textSize = 17f
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(color(R.color.app_text))
                    })
                    val detail = listOf(entry.username, entry.website)
                        .filter { it.isNotBlank() }
                        .joinToString("  \u00B7  ")
                    if (detail.isNotBlank()) {
                        addView(TextView(this@MainActivity).apply {
                            text = detail
                            textSize = 13f
                            setTextColor(color(R.color.app_muted))
                            setPadding(0, dp(2), 0, 0)
                        })
                    }
                })
            })
            addView(horizontal().apply {
                setPadding(0, dp(12), 0, 0)
                addView(secondaryButton("Copy user") {
                    clipboardGuard.copySecret(entry.username)
                    toast("Copied.")
                })
                addView(secondaryButton("Copy pass") {
                    clipboardGuard.copySecret(entry.password)
                    toast("Copied.")
                })
            })
            addView(horizontal().apply {
                setPadding(0, dp(8), 0, 0)
                addView(secondaryButton("Reveal") { showPasswordRevealDialog(entry) })
                addView(secondaryButton("Edit") { showEntryEditor(entry.id) })
            })
        }

    private fun showPasswordRevealDialog(entry: VaultEntry) {
        val wrapper = dialogBody()
        wrapper.addView(TextView(this).apply {
            text = entry.password.ifBlank { "No password saved." }
            textSize = 18f
            typeface = Typeface.MONOSPACE
            setTextColor(color(R.color.app_text))
            setPadding(0, dp(8), 0, dp(8))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })

        AlertDialog.Builder(this)
            .setTitle("${entry.title} password")
            .setView(wrapper)
            .setNegativeButton("Close", null)
            .setPositiveButton("Copy") { _, _ ->
                clipboardGuard.copySecret(entry.password)
                toast("Copied.")
            }
            .show()
    }

    private fun importPreviewCard(entry: VaultEntry): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedDrawable(color(R.color.app_surface), dp(18), color(R.color.app_border), 1)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(4), 0, dp(8)) }
            addView(horizontal().apply {
                addView(iconBubble(entry.title.firstOrNull()?.uppercaseChar()?.toString() ?: "L", false))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = entry.title.ifBlank { "Untitled entry" }
                        textSize = 16f
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(color(R.color.app_text))
                    })
                    val detail = listOf(entry.username, entry.website)
                        .filter { it.isNotBlank() }
                        .joinToString("  \u00B7  ")
                    addView(TextView(this@MainActivity).apply {
                        text = detail.ifBlank { "No username or website" }
                        textSize = 13f
                        setTextColor(color(R.color.app_muted))
                        setPadding(0, dp(2), 0, 0)
                    })
                })
            })
        }

    private fun editorTopBar(title: String, save: () -> Unit): View =
        horizontal().apply {
            setPadding(0, dp(6), 0, dp(20))
            addView(circleAction(R.drawable.ic_close, false) { showVaultScreen() })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 24f
                gravity = Gravity.CENTER
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_text))
                letterSpacing = -0.01f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(circleAction(R.drawable.ic_check, true) { save() })
        }

    private fun simpleTopBar(title: String, back: () -> Unit): View =
        horizontal().apply {
            setPadding(0, dp(6), 0, dp(18))
            addView(circleAction(R.drawable.ic_arrow_back, false) { back() })
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 24f
                gravity = Gravity.CENTER
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_text))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            })
        }

    private fun settingsRow(
        iconRes: Int,
        title: String,
        subtitle: String,
        titleColor: Int? = null,
        iconTint: Int? = null,
        action: () -> Unit,
    ): View =
        horizontal().apply {
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { action() }
            addView(tintedIconBubble(iconRes, iconTint ?: color(R.color.app_primary_dark)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15f
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(titleColor ?: color(R.color.app_text))
                })
                addView(TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = 13f
                    setTextColor(color(R.color.app_muted))
                    setPadding(0, dp(3), 0, 0)
                })
            })
        }

    private fun securityRow(iconRes: Int, title: String, body: String): View =
        horizontal().apply {
            setPadding(0, dp(8), 0, dp(8))
            addView(tintedIconBubble(iconRes, color(R.color.app_primary_dark)))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15f
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(color(R.color.app_text))
                })
                addView(TextView(this@MainActivity).apply {
                    text = body
                    textSize = 13f
                    setTextColor(color(R.color.app_muted))
                    setPadding(0, dp(3), 0, 0)
                })
            })
        }

    private fun fieldRow(iconRes: Int, label: String, field: EditText, trailing: View? = null): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(iconBubble(iconRes, false))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 12f
                    letterSpacing = 0.08f
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(color(R.color.app_muted))
                    setPadding(0, 0, 0, dp(5))
                })
                addView(field)
            })
            trailing?.let { addView(it) }
        }

    private data class PasswordStrength(
        val bars: Int,
        val label: String,
        val color: Int,
    )

    private fun passwordMeter(field: EditText): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(62), dp(6), 0, dp(8))
            val bars = mutableListOf<View>()
            repeat(4) { index ->
                addView(View(this@MainActivity).apply {
                    background = roundedDrawable(color(R.color.app_border), dp(4))
                    layoutParams = LinearLayout.LayoutParams(0, dp(5), 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    }
                    bars.add(this)
                })
            }
            val label = TextView(this@MainActivity).apply {
                text = "Enter password"
                textSize = 12f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_muted))
                setPadding(dp(10), 0, 0, 0)
            }
            addView(label)

            fun renderStrength() {
                val strength = evaluatePasswordStrength(field.text.toString())
                bars.forEachIndexed { index, bar ->
                    bar.background = roundedDrawable(
                        if (index < strength.bars) strength.color else color(R.color.app_border),
                        dp(4),
                    )
                }
                label.text = strength.label
                label.setTextColor(strength.color)
            }

            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = renderStrength()
            })
            renderStrength()
        }

    private fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0, "Enter password", color(R.color.app_muted))
        }

        val weakColor = color(R.color.app_danger)
        val fairColor = color(R.color.app_fair)
        val goodColor = color(R.color.app_accent_green)
        val strongColor = color(R.color.app_primary)
        val lower = password.lowercase()
        val uniqueChars = password.toSet().size
        val classes = listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() },
        ).count { it }

        val repeatedRun = Regex("(.)\\1{3,}").containsMatchIn(password)
        val mostlyRepeated = uniqueChars <= 2 || uniqueChars * 3 <= password.length
        val commonPattern = listOf(
            "password",
            "qwerty",
            "123456",
            "111111",
            "abcdef",
            "letmein",
            "admin",
            "iloveyou",
        ).any { lower.contains(it) }

        if (password.length < 8 || repeatedRun || mostlyRepeated || commonPattern) {
            return PasswordStrength(1, "Weak password", weakColor)
        }

        var score = 0
        if (password.length >= 10) score += 1
        if (password.length >= 14) score += 1
        if (password.length >= 18) score += 1
        score += classes
        if (uniqueChars >= password.length / 2) score += 1

        return when {
            score <= 3 -> PasswordStrength(2, "Fair password", fairColor)
            score <= 5 -> PasswordStrength(3, "Good password", goodColor)
            else -> PasswordStrength(4, "Strong password", strongColor)
        }
    }

    private fun checkList(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(62), dp(4), 0, dp(8))
            listOf("At least 12 characters", "Mixed letters", "Number or symbol").forEach { item ->
                addView(horizontal().apply {
                    setPadding(0, dp(3), 0, dp(3))
                    addView(iconImage(R.drawable.ic_check_small, 16, color(R.color.app_primary)).apply {
                        setPadding(0, 0, dp(8), 0)
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = item
                        textSize = 13f
                        setTextColor(color(R.color.app_primary))
                    })
                })
            }
        }

    private fun bottomNav(active: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = roundedDrawable(color(R.color.app_surface), dp(20), color(R.color.app_border), 1)
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(18), 0, 0) }
            addView(navItem(R.drawable.ic_home, "Home", active == "home") { showVaultScreen() })
            addView(navItem(R.drawable.ic_settings, "Settings", active == "settings") { showSettingsScreen() })
        }

    private fun navItem(iconRes: Int, label: String, active: Boolean, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            if (active) {
                background = roundedDrawable(color(R.color.app_primary_soft), dp(16))
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val tint = color(if (active) R.color.app_primary else R.color.app_muted)
            addView(iconImage(iconRes, 22, tint).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.CENTER_HORIZONTAL }
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(tint)
                setTypeface(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
                setPadding(0, dp(2), 0, 0)
            })
        }

    private fun pageTitle(content: LinearLayout, title: String, subtitle: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = if (isCompactWidth()) 23f else 26f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(color(R.color.app_text))
            letterSpacing = -0.02f
        })
        content.addView(TextView(this).apply {
            text = subtitle
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.app_muted))
            setPadding(dp(24), dp(8), dp(24), dp(20))
        })
    }

    private fun infoCard(title: String, body: String): View =
        card().apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(horizontal().apply {
                addView(iconBubble(R.drawable.ic_info, false))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = title
                        textSize = 15f
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(color(R.color.app_text))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = body
                        textSize = 13f
                        setTextColor(color(R.color.app_muted))
                        setPadding(0, dp(4), 0, 0)
                    })
                })
            })
        }

    private fun documentCard(title: String, body: String): View =
        card().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_text))
            })
            addView(TextView(this@MainActivity).apply {
                text = body
                textSize = 13f
                setLineSpacing(dp(2).toFloat(), 1.0f)
                setTextColor(color(R.color.app_muted))
                setPadding(0, dp(8), 0, 0)
            })
        }

    private fun brandLogo(): View =
        ImageView(this).apply {
            setImageResource(R.drawable.lockroot_logo_full)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (isCompactWidth()) 170 else if (isTabletWidth()) 240 else 220),
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }

    private fun sectionLabel(text: String): View =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            letterSpacing = 0.08f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(color(R.color.app_muted))
            setPadding(dp(4), dp(18), 0, dp(6))
        }

    private fun statusLine(text: String): View =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(color(R.color.app_primary))
            setPadding(dp(72), dp(6), 0, dp(4))
        }

    private fun input(hint: String, value: String = "", lines: Int = 1): EditText =
        EditText(this).apply {
            setHint(hint)
            setText(value)
            minLines = lines
            maxLines = if (lines > 1) 5 else 1
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            inputType = if (lines > 1) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else {
                InputType.TYPE_CLASS_TEXT
            }
            setSingleLine(lines == 1)
            imeOptions = if (lines > 1) EditorInfo.IME_ACTION_NONE else EditorInfo.IME_ACTION_NEXT
            setOnClickListener { showKeyboard(this) }
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showKeyboard(this)
            }
            background = roundedDrawable(color(R.color.app_field), dp(14), color(R.color.app_border), 1)
            backgroundTintList = null
            minHeight = dp(if (lines > 1) 96 else 46)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            textSize = 16f
            setTextColor(color(R.color.app_text))
            setHintTextColor(color(R.color.app_hint))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun passwordInput(hint: String, value: String = ""): EditText =
        input(hint, value).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }

    private fun showKeyboard(field: EditText) {
        field.post {
            field.requestFocus()
            getSystemService(InputMethodManager::class.java)
                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            field.postDelayed({ scrollFocusedAreaAboveKeyboard(field) }, 260)
        }
    }

    private fun primaryButton(label: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(56)
            background = roundedDrawable(color(R.color.app_primary), dp(18))
            elevation = dp(3).toFloat()
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(14), 0, dp(10)) }
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 16f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_white))
            })
        }

    private fun secondaryButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            minHeight = dp(38)
            textSize = 13f
            setTextColor(color(R.color.app_primary))
            background = roundedDrawable(color(R.color.app_primary_soft), dp(12))
            backgroundTintList = null
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { setMargins(dp(3), 0, dp(3), 0) }
        }

    private fun dangerButton(label: String, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(56)
            background = roundedDrawable(color(R.color.app_danger), dp(18))
            elevation = dp(3).toFloat()
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(14), 0, dp(10)) }
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 16f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_white))
            })
        }

    private fun pillButton(label: String, iconRes: Int, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedDrawable(color(R.color.app_primary), dp(24))
            elevation = dp(2).toFloat()
            setPadding(dp(14), dp(10), dp(16), dp(10))
            setOnClickListener { action() }
            addView(iconImage(iconRes, 16, color(R.color.app_white)).apply {
                setPadding(0, 0, dp(6), 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(color(R.color.app_white))
            })
        }

    private fun circleAction(iconRes: Int, primary: Boolean, action: () -> Unit): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedDrawable(
                if (primary) color(R.color.app_primary) else color(R.color.app_surface),
                dp(28),
                if (primary) null else color(R.color.app_border),
                1,
            )
            elevation = dp(2).toFloat()
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            val tint = if (primary) color(R.color.app_white) else color(R.color.app_text)
            addView(iconImage(iconRes, 20, tint))
        }

    private fun iconBubble(label: String, primary: Boolean): View =
        TextView(this).apply {
            text = label.take(4)
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(if (primary) color(R.color.app_white) else color(R.color.app_primary_dark))
            background = roundedDrawable(if (primary) color(R.color.app_primary) else color(R.color.app_primary_soft), dp(24))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                setMargins(0, 0, dp(12), 0)
            }
        }

    private fun iconBubble(drawableRes: Int, primary: Boolean): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedDrawable(if (primary) color(R.color.app_primary) else color(R.color.app_primary_soft), dp(24))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                setMargins(0, 0, dp(12), 0)
            }
            val tint = if (primary) color(R.color.app_white) else color(R.color.app_primary_dark)
            addView(iconImage(drawableRes, 22, tint))
        }

    private fun tintedIconBubble(drawableRes: Int, tint: Int): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedDrawable(color(R.color.app_primary_soft), dp(22))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                setMargins(0, 0, dp(12), 0)
            }
            addView(iconImage(drawableRes, 21, tint))
        }

    private fun iconImage(drawableRes: Int, sizeDp: Int, tintColor: Int): ImageView =
        ImageView(this).apply {
            setImageResource(drawableRes)
            setColorFilter(tintColor)
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    private fun entryCountBadge(vault: Vault): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(color(R.color.app_field), dp(12), color(R.color.app_border), 1)
            setPadding(dp(12), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(14), 0, 0) }
            addView(iconImage(R.drawable.ic_folder, 16, color(R.color.app_muted)).apply {
                setPadding(0, 0, dp(8), 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = resources.getQuantityString(R.plurals.entry_count, vault.entries.size, vault.entries.size)
                textSize = 13f
                setTextColor(color(R.color.app_muted))
            })
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedDrawable(color(R.color.app_surface), dp(20), color(R.color.app_border), 1)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, dp(6), 0, dp(10)) }
        }

    private fun horizontal(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

    private fun divider(): View =
        View(this).apply {
            background = roundedDrawable(color(R.color.app_divider), dp(1))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1,
            ).apply { setMargins(dp(62), dp(8), 0, dp(8)) }
        }

    private fun illustration(mode: SecurityIllustrationView.Mode, heightDp: Int): View =
        SecurityIllustrationView(this, mode).apply {
            layoutParams = LinearLayout.LayoutParams(dp(responsiveHeroWidthDp()), dp(heightDp)).apply {
                setMargins(0, dp(10), 0, dp(4))
            }
        }

    private fun dialogBody(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
        }

    private fun screen(): LinearLayout {
        cleanupScreenListener()
        val scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.app_bg))
            isFillViewport = true
            clipToPadding = false
            isFocusableInTouchMode = true
            setOnClickListener {
                hideKeyboard()
                requestFocus()
            }
        }
        activeScrollView = scroll
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = responsiveContentPaddingDp()
            setPadding(dp(horizontalPadding), dp(20), dp(horizontalPadding), dp(28))
            isFocusableInTouchMode = true
            setOnClickListener {
                hideKeyboard()
                requestFocus()
            }
            layoutParams = FrameLayout.LayoutParams(
                responsiveContentWidthPx(),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
            )
        }
        scroll.addView(content)
        setContentView(scroll)
        installKeyboardPadding(scroll)
        return content
    }

    private fun cleanupScreenListener() {
        activeScrollView?.viewTreeObserver?.let { observer ->
            keyboardLayoutListener?.let { listener ->
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
            }
        }
        keyboardLayoutListener = null
    }

    private fun screenWidthDp(): Int {
        val configured = resources.configuration.screenWidthDp
        if (configured > 0) return configured
        return (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
    }

    private fun isCompactWidth(): Boolean = screenWidthDp() < 360

    private fun isTabletWidth(): Boolean = screenWidthDp() >= 600

    private fun responsiveContentPaddingDp(): Int = when {
        isCompactWidth() -> 12
        isTabletWidth() -> 22
        else -> 16
    }

    private fun responsiveContentWidthPx(): Int {
        val widthDp = screenWidthDp()
        if (widthDp < 600) return FrameLayout.LayoutParams.MATCH_PARENT
        val sideGutterDp = 48
        val targetDp = (widthDp - sideGutterDp).coerceIn(520, 720)
        return dp(targetDp)
    }

    private fun responsiveActionColumns(): Int = when {
        screenWidthDp() < 360 -> 2
        screenWidthDp() < 600 -> 3
        else -> 4
    }

    private fun responsiveActionTileHeightDp(): Int = when {
        isCompactWidth() -> 108
        isTabletWidth() -> 126
        else -> 116
    }

    private fun responsiveActionIconSizeDp(): Int = if (isCompactWidth()) 24 else 26

    private fun responsiveHeroHeightDp(): Int = when {
        isCompactWidth() -> 145
        isTabletWidth() -> 210
        else -> 185
    }

    private fun responsiveHeroWidthDp(): Int = when {
        isCompactWidth() -> 180
        isTabletWidth() -> 240
        else -> 220
    }

    private fun installKeyboardPadding(scroll: ScrollView) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val visibleFrame = Rect()
            scroll.getWindowVisibleDisplayFrame(visibleFrame)
            val keyboardHeight = (scroll.rootView.height - visibleFrame.bottom).coerceAtLeast(0)
            val bottomPadding = if (keyboardHeight > dp(120)) keyboardHeight + dp(28) else dp(28)
            if (scroll.paddingBottom != bottomPadding) {
                scroll.setPadding(scroll.paddingLeft, scroll.paddingTop, scroll.paddingRight, bottomPadding)
            }

            val focused = currentFocus as? EditText
            if (keyboardHeight > dp(120) && focused != null) {
                scrollFocusedAreaAboveKeyboard(focused)
            }
        }
        keyboardLayoutListener = listener
        scroll.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun scrollFocusedAreaAboveKeyboard(field: EditText) {
        val scroll = activeScrollView ?: return
        val rect = Rect()
        field.getDrawingRect(rect)
        scroll.offsetDescendantRectToMyCoords(field, rect)
        val target = rect.bottom - scroll.height + scroll.paddingBottom + dp(96)
        if (target > 0) {
            scroll.smoothScrollTo(0, target)
        }
    }

    private fun hideKeyboard() {
        val focused = currentFocus ?: activeScrollView ?: return
        getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    private fun unlockWithPassword(password: EditText) {
        val remainingBlockMillis = unlockBlockedUntilMillis - SystemClock.elapsedRealtime()
        if (remainingBlockMillis > 0) {
            toast("Try again in ${(remainingBlockMillis / 1000L).coerceAtLeast(1L)} seconds.")
            return
        }

        val typed = password.text.toString().toCharArray()
        try {
            repository.unlock(typed)
            failedUnlockAttempts = 0
            unlockBlockedUntilMillis = 0L
            hideKeyboard()
            wipePasswordField(password)
            scheduleInactivityLock()
            showVaultScreen()
        } catch (_: AuthenticationFailedException) {
            failedUnlockAttempts += 1
            val delayMillis = unlockBackoffMillis(failedUnlockAttempts)
            if (delayMillis > 0) {
                unlockBlockedUntilMillis = SystemClock.elapsedRealtime() + delayMillis
                toast("Wrong password. Try again in ${delayMillis / 1000L} seconds.")
            } else {
                toast("Wrong password or corrupted vault.")
            }
        } catch (error: Exception) {
            toast(error.safeMessage())
        } finally {
            Arrays.fill(typed, '\u0000')
            if (repository.unlockedVault == null) {
                wipePasswordField(password)
            }
        }
    }

    private fun unlockBackoffMillis(attempts: Int): Long =
        when {
            attempts < UNLOCK_BACKOFF_START_ATTEMPT -> 0L
            attempts >= UNLOCK_BACKOFF_START_ATTEMPT + 4 -> UNLOCK_BACKOFF_MAX_MILLIS
            else -> (1L shl (attempts - UNLOCK_BACKOFF_START_ATTEMPT)) * UNLOCK_BACKOFF_BASE_MILLIS
        }.coerceAtMost(UNLOCK_BACKOFF_MAX_MILLIS)

    private fun VaultEntry.matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return listOf(title, website, username, notes)
            .plus(tags)
            .any { it.lowercase().contains(needle) }
    }

    private fun Throwable.safeMessage(): String = when (this) {
        is CryptoException -> message ?: "Crypto operation failed."
        else -> message ?: "Operation failed."
    }

    private fun wipePendingExportPassword() {
        pendingExportPassword?.let { Arrays.fill(it, '\u0000') }
        pendingExportPassword = null
    }

    private fun wipePasswordField(field: EditText) {
        val editable = field.text ?: return
        if (editable.isNotEmpty()) {
            editable.replace(0, editable.length, CharArray(editable.length) { '\u0000' }.concatToString())
        }
        editable.clear()
        field.setText("")
    }

    private fun scheduleInactivityLock() {
        inactivityHandler.removeCallbacks(inactivityLock)
        inactivityHandler.postDelayed(inactivityLock, INACTIVITY_LOCK_MILLIS)
    }

    private fun cancelInactivityLock() {
        inactivityHandler.removeCallbacks(inactivityLock)
    }

    private fun lockVault(message: String) {
        cancelInactivityLock()
        repository.lock()
        clipboardGuard.clearIfAppOwned()
        showUnlockScreen(message)
    }

    private fun lockVaultForBackground() {
        cancelInactivityLock()
        repository.lock()
        clipboardGuard.clearIfAppOwned()
        renderLockedOnResume = true
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun color(resourceId: Int): Int = getColor(resourceId)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedDrawable(
        fillColor: Int,
        radius: Int,
        strokeColor: Int? = null,
        strokeDp: Int = 0,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            if (strokeColor != null && strokeDp > 0) {
                setStroke(dp(strokeDp), strokeColor)
            }
        }

    companion object {
        private const val REQUEST_EXPORT = 101
        private const val REQUEST_IMPORT = 102
        private const val INACTIVITY_LOCK_MILLIS = 60_000L
        private const val UNLOCK_BACKOFF_START_ATTEMPT = 3
        private const val UNLOCK_BACKOFF_BASE_MILLIS = 2_000L
        private const val UNLOCK_BACKOFF_MAX_MILLIS = 30_000L
        private const val PASSWORD_GENERATOR_MIN_LENGTH = 12
        private const val PASSWORD_GENERATOR_MAX_LENGTH = 64

        private val ABOUT_CREATOR_TEXT = """
            Regaan
            Security Researcher | Offensive Engineer | Full-Stack Developer
            Chennai, India

            Email: regaan48@gmail.com
            LinkedIn: linkedin.com/in/regaan
            GitHub: github.com/regaan
            Website: rothackers.com
        """.trimIndent()

        private val ABOUT_RESEARCH_TEXT = """
            Security researcher specializing in offensive security, protocol fuzzing, and security tooling.

            Focus areas:
            - WebSocket security, protocol fuzzing, WAF bypass, XSS, SQLi, SSRF, OAST, and red teaming
            - Go, C/C++, Python, Linux internals, Windows internals, syscalls, TCP/IP, WebSockets, LLVM, and bytecode VMs
            - Docker, Redis, PostgreSQL, Node.js, Next.js, Playwright, Burp Suite, and git forensics
            - LLM red teaming, prompt evolution, genetic algorithms, and local LLM quantization

            Projects include WSHawk, Basilisk, ProtoCrash, PoCSmith, ROT C2, RedLang, and Keikaku.
        """.trimIndent()

        private val ABOUT_PROJECT_TEXT = """
            Lockroot is a local-only password manager built around a simple promise: the vault is encrypted on the device and there is no recovery backdoor.

            The vault uses Argon2id key derivation, AES-256-GCM authenticated encryption, encrypted export/import, screenshot blocking, clipboard auto-clear, and automatic app locking.
        """.trimIndent()

        private val PRIVACY_SUMMARY_TEXT = """
            Lockroot is designed as an offline, local-only password manager.

            The app does not include internet permission, analytics, ads, telemetry, cloud sync, remote config, or account login.
        """.trimIndent()

        private val PRIVACY_LOCAL_DATA_TEXT = """
            Vault entries are stored on your device inside an encrypted vault file.

            Entry titles, websites, usernames, passwords, notes, and tags are encrypted before they are written to disk. Lockroot does not store your master password.
        """.trimIndent()

        private val PRIVACY_EXPORT_TEXT = """
            Exports are encrypted with a separate export password. Import requires the same export password, and a wrong password fails authentication.

            When you copy a username or password, Lockroot places it in the Android clipboard and tries to clear app-owned clipboard content after a short delay.
        """.trimIndent()

        private val PRIVACY_RECOVERY_TEXT = """
            Lockroot has no password recovery system.

            If you lose the master password, the encrypted vault cannot be decrypted by the app developer or by the app itself.
        """.trimIndent()

        private val TERMS_ACCEPTANCE_TEXT = """
            By creating a Lockroot vault, you agree to these Terms and Conditions and the Privacy Policy.

            If you do not agree, do not create a vault.
        """.trimIndent()

        private val TERMS_RESPONSIBILITY_TEXT = """
            You are responsible for choosing a strong master password, remembering it, and keeping encrypted backups if you need them.

            Lockroot cannot recover lost passwords, decrypt locked vaults, or restore deleted entries without a valid backup.
        """.trimIndent()

        private val TERMS_SECURITY_LIMITS_TEXT = """
            Lockroot protects vault data at rest, but it cannot protect you from every device-level risk.

            A compromised phone, malicious keyboard, screen reader, modified APK, rooted device, or someone who sees your master password can still put your secrets at risk.
        """.trimIndent()

        private val TERMS_WARRANTY_TEXT = """
            Lockroot is provided as local security software without a guarantee that it is suitable for every situation.

            Use it lawfully, keep backups, test exports before depending on them, and understand that losing the master password means losing access to the vault.
        """.trimIndent()

        private val PRIVACY_TEXT = listOf(
            PRIVACY_SUMMARY_TEXT,
            PRIVACY_LOCAL_DATA_TEXT,
            PRIVACY_EXPORT_TEXT,
            PRIVACY_RECOVERY_TEXT,
        ).joinToString("\n\n")

        private val TERMS_TEXT = listOf(
            TERMS_ACCEPTANCE_TEXT,
            TERMS_RESPONSIBILITY_TEXT,
            TERMS_SECURITY_LIMITS_TEXT,
            TERMS_WARRANTY_TEXT,
        ).joinToString("\n\n")
    }
}
