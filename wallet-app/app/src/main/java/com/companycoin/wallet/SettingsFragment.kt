package com.companycoin.wallet

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.companycoin.wallet.crypto.ApiException
import com.companycoin.wallet.crypto.AuthApi
import com.companycoin.wallet.crypto.CoCoChainClient
import com.companycoin.wallet.crypto.CoinType
import com.companycoin.wallet.crypto.MultiWalletManager
import com.companycoin.wallet.crypto.SecureSession
import com.companycoin.wallet.crypto.ServerConfig
import com.companycoin.wallet.crypto.WalletSync
import com.companycoin.wallet.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val wm = (activity as MainActivity).walletManager

        updateAccountUi()
        binding.btnAccountSignin.setOnClickListener { signInAndRestore(wm) }
        binding.btnAccountBackup.setOnClickListener { backUpWallet(wm) }
        binding.btnAccountSignout.setOnClickListener {
            SecureSession.clear(requireContext())
            updateAccountUi()
            Toast.makeText(context, "Signed out of CoCo account", Toast.LENGTH_SHORT).show()
        }
        binding.btnAccountRegister.setOnClickListener { showRegisterDialog() }
        binding.btnDiagnostics.setOnClickListener { showDiagnostics(wm) }

        binding.btnBackupSeed.setOnClickListener {
            val walletId = wm.getActiveWalletId()
            if (walletId == null) { Toast.makeText(context, "No wallet", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            WalletAuth.authenticate(this, "Confirm Identity", "View wallet keys") {
                    val mnemonic = wm.getMnemonic()
                    val seedHex = wm.getSeedHex()
                    val secret = mnemonic ?: seedHex?.let { "Raw Ed25519 seed (hex):\n$it" } ?: run {
                        Toast.makeText(context, "No keys available for this wallet", Toast.LENGTH_SHORT).show()
                        return@authenticate
                    }
                    val addrs = CoinType.entries.joinToString("\n") { c ->
                        "${c.symbol}: ${wm.getAddress(c) ?: "—"}"
                    }

                    val seedView = android.widget.TextView(requireContext()).apply {
                        text = secret
                        textSize = 14f
                        typeface = android.graphics.Typeface.MONOSPACE
                        setTextColor(0xFFF5C918.toInt())
                        setPadding(16, 16, 16, 16)
                        setBackgroundColor(0xFF1A1A1A.toInt())
                    }

                    val layout = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(android.widget.TextView(requireContext()).apply {
                            text = if (mnemonic != null) "Seed Phrase (write down & store safely):" else "Wallet keys (store safely):"
                            textSize = 14f; setTextColor(0xFF888888.toInt())
                        })
                        addView(seedView)
                        addView(android.widget.TextView(requireContext()).apply {
                            text = "\nDerived Addresses:\n$addrs"
                            textSize = 12f; setTextColor(0xFF888888.toInt())
                        })
                    }

                    AlertDialog.Builder(requireContext())
                        .setTitle("Wallet Keys")
                        .setView(layout)
                        .setPositiveButton("Copy & Close") { _, _ ->
                            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("seed", secret))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }.show()
            }
        }

        binding.btnClearWallet.setOnClickListener {
            val walletId = wm.getActiveWalletId()
            if (walletId == null) { Toast.makeText(context, "No wallet", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Wallet")
                .setMessage("This will permanently delete '${wm.getWalletProfile(walletId)?.name ?: "this wallet"}' and all keys. Only do this if you have your seed phrase backed up.\n\nThis cannot be undone!")
                .setPositiveButton("Delete") { _, _ ->
                    wm.deleteWallet(walletId)
                    Toast.makeText(context, "Wallet deleted", Toast.LENGTH_LONG).show()
                    activity?.recreate()
                }
                .setNegativeButton("Cancel", null).show()
        }

        var suppressTestnetSwitch = true
        binding.switchTestnet.setOnCheckedChangeListener { _, isChecked ->
            if (suppressTestnetSwitch) return@setOnCheckedChangeListener
            if (isChecked) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Testnet Mode")
                    .setMessage("Testnet coins have NO real value. Only use for testing.")
                    .setPositiveButton("Enable") { _, _ ->
                        requireContext().getSharedPreferences("wallet_prefs", 0)
                            .edit().putBoolean("testnet_mode", true).apply()
                        Toast.makeText(context, "Testnet mode enabled - restarting...", Toast.LENGTH_SHORT).show()
                        activity?.recreate()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.switchTestnet.isChecked = false
                    }.show()
            } else {
                requireContext().getSharedPreferences("wallet_prefs", 0)
                    .edit().putBoolean("testnet_mode", false).apply()
                Toast.makeText(context, "Mainnet mode - restarting...", Toast.LENGTH_SHORT).show()
                activity?.recreate()
            }
        }

        val isTestnet = requireContext().getSharedPreferences("wallet_prefs", 0)
            .getBoolean("testnet_mode", false)
        binding.switchTestnet.isChecked = isTestnet
        suppressTestnetSwitch = false

        binding.switchBetaServer.isChecked = ServerConfig.isBeta(requireContext())
        binding.switchBetaServer.setOnCheckedChangeListener { _, isChecked ->
            ServerConfig.setBase(
                requireContext(),
                if (isChecked) ServerConfig.BETA else ServerConfig.PRODUCTION
            )
            // Tokens are per website (different JWT secrets); force a fresh
            // sign-in against the selected account database.
            SecureSession.clear(requireContext())
            Toast.makeText(
                context,
                if (isChecked) "Using beta server (cocoio.cc)" else "Using production server (companycoin.org)",
                Toast.LENGTH_SHORT
            ).show()
            activity?.recreate()
        }
    }

    private fun showRegisterDialog() {
        val nameInput = EditText(requireContext()).apply { hint = "Name (optional)" }
        val emailInput = EditText(requireContext()).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val passwordInput = EditText(requireContext()).apply {
            hint = "Password (min 8 characters)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmInput = EditText(requireContext()).apply {
            hint = "Confirm password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(nameInput); addView(emailInput); addView(passwordInput); addView(confirmInput)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Create a CoCo Account")
            .setMessage("The account is optional. It lets you back up and restore this wallet on the website or another phone.")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (email.isBlank() || password.length < 8) {
                    Toast.makeText(context, "Enter an email and a password of at least 8 characters", Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                if (password != confirmInput.text.toString()) {
                    Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        val api = authApi()
                        withContext(Dispatchers.IO) { api.register(email, password, nameInput.text.toString().trim()) }
                        Toast.makeText(context, "Account created. Check your email, verify it, then sign in here.", Toast.LENGTH_LONG).show()
                    } catch (e: ApiException) {
                        Toast.makeText(context, "Create failed: ${e.message}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Create failed: ${e.message ?: "network error"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDiagnostics(wm: MultiWalletManager) {
        val context = requireContext()
        val prefs = context.getSharedPreferences("wallet_prefs", 0)
        val testnet = prefs.getBoolean("testnet_mode", false)
        val base = if (testnet) CoinType.COCO.testnetRpc else ServerConfig.base(context)
        val address = wm.getAddress(CoinType.COCO) ?: "no wallet"
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val biometric = WalletAuth.status(context)
        binding.tvAccountStatus.post {
            lifecycleScope.launch {
                val balance = withContext(Dispatchers.IO) {
                    if (address == "no wallet") "no wallet"
                    else runCatching { CoCoChainClient(base, null).getBalance(address, CoinType.COCO) }
                        .getOrElse { "error: ${it.message}" }
                }
                val text = buildString {
                    appendLine("App: $version")
                    appendLine("Server: $base")
                    appendLine("Address: $address")
                    appendLine("COCO balance: $balance")
                    appendLine("Biometric/credential: $biometric")
                    appendLine("Last send: ${WalletDiag.lastSendStatus ?: "none"}")
                    appendLine("Last error: ${WalletDiag.lastSendError ?: "none"}")
                }
                AlertDialog.Builder(context)
                    .setTitle("Wallet Diagnostics")
                    .setMessage(text.trim())
                    .setPositiveButton("Copy") { _, _ ->
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("coco-wallet-diagnostics", text))
                        Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun authApi(): AuthApi {
        val isTestnet = requireContext().getSharedPreferences("wallet_prefs", 0)
            .getBoolean("testnet_mode", false)
        val base = if (isTestnet) CoinType.COCO.testnetRpc else ServerConfig.base(requireContext())
        return AuthApi(base)
    }

    private fun updateAccountUi() {
        val token = SecureSession.token(requireContext())
        if (token == null) {
            binding.tvAccountStatus.text = "Not signed in — account is optional"
            binding.btnAccountSignin.text = "Sign In / Restore Wallet"
            binding.btnAccountBackup.visibility = View.GONE
            binding.btnAccountSignout.visibility = View.GONE
            binding.btnAccountRegister.visibility = View.VISIBLE
        } else {
            val email = SecureSession.email(requireContext()) ?: "CoCo account"
            binding.tvAccountStatus.text = "Signed in as $email"
            binding.btnAccountSignin.text = "Restore Another Wallet"
            binding.btnAccountBackup.visibility = View.VISIBLE
            binding.btnAccountSignout.visibility = View.VISIBLE
            binding.btnAccountRegister.visibility = View.GONE
        }
    }

    private fun signInAndRestore(wm: MultiWalletManager) {
        val existingToken = SecureSession.token(requireContext())
        if (existingToken != null) {
            promptPassword("Restore Wallet") { password ->
                fetchAndRestore(existingToken, password, wm)
            }
            return
        }

        val emailInput = EditText(requireContext()).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val passwordInput = EditText(requireContext()).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(emailInput)
            addView(passwordInput)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("CoCo Account")
            .setMessage("Sign in to restore an encrypted wallet backup stored on your CoCo account.")
            .setView(layout)
            .setPositiveButton("Sign In") { _, _ ->
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Enter email and password", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                completeSignIn(authApi(), email, password, null, wm)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun completeSignIn(
        api: AuthApi,
        email: String,
        password: String,
        totpCode: String?,
        wm: MultiWalletManager,
    ) {
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { api.login(email, password, totpCode) }
                SecureSession.save(requireContext(), session.token, session.email, session.name)
                updateAccountUi()
                val synced = withContext(Dispatchers.IO) {
                    WalletSync.saveAll(wm, api, session.token)
                }
                Toast.makeText(context, "Signed in · $synced wallet(s) linked", Toast.LENGTH_SHORT).show()
                fetchAndRestore(session.token, password, wm, api)
            } catch (e: AuthApi.TotpRequiredException) {
                promptTotp { code -> completeSignIn(api, email, password, code, wm) }
            } catch (e: ApiException) {
                Toast.makeText(context, "Sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Sign in failed: ${e.message ?: "network error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun promptTotp(onCode: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = "6-digit code or backup code"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Two-Factor Authentication")
            .setMessage("Enter the code from your authenticator app, or one of your backup codes.")
            .setView(layout)
            .setPositiveButton("Verify") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isBlank()) {
                    Toast.makeText(context, "Enter your 2FA code", Toast.LENGTH_SHORT).show()
                } else {
                    onCode(code)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchAndRestore(token: String, password: String, wm: MultiWalletManager, api: AuthApi = authApi()) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { WalletSync.saveAll(wm, api, token) }
                val wallets = withContext(Dispatchers.IO) { api.wallets(token) }
                val usable = wallets.filter { it.hasBackup && it.appCompatible }
                if (usable.isEmpty()) {
                    Toast.makeText(context, "No backed-up app wallets on this account", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val labels = usable.map {
                    val name = it.label ?: "Wallet"
                    "$name · ${it.address.take(6)}…${it.address.takeLast(5)}"
                }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("Restore Wallet")
                    .setItems(labels) { _, which ->
                        val chosen = usable[which]
                        lifecycleScope.launch inner@{
                            try {
                                val restored = withContext(Dispatchers.IO) {
                                    api.restore(token, chosen.address, password)
                                }
                                val existing = wm.findWalletByAddress(restored.address)
                                val ok = if (existing != null) {
                                    wm.switchWallet(existing)
                                } else {
                                    wm.importWallet(restored.mnemonic, restored.label ?: "CoCo account") != null
                                }
                                if (!ok) {
                                    Toast.makeText(context, "Could not import wallet", Toast.LENGTH_LONG).show()
                                    return@inner
                                }
                                withContext(Dispatchers.IO) {
                                    runCatching { api.saveWallet(token, restored.address, restored.label) }
                                }
                                Toast.makeText(context, "Wallet restored · ${restored.address.take(6)}…", Toast.LENGTH_LONG).show()
                                activity?.recreate()
                            } catch (e: ApiException) {
                                Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: ApiException) {
                Toast.makeText(context, "Account wallets failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun backUpWallet(wm: MultiWalletManager) {
        val token = SecureSession.token(requireContext()) ?: run {
            Toast.makeText(context, "Sign in first", Toast.LENGTH_SHORT).show(); return
        }
        val address = wm.getAddress(CoinType.COCO) ?: run {
            Toast.makeText(context, "No wallet", Toast.LENGTH_SHORT).show(); return
        }
        val mnemonic = wm.getMnemonic() ?: run {
            Toast.makeText(context, "This wallet was imported as a raw seed. Back up the raw seed instead.", Toast.LENGTH_LONG).show(); return
        }
        val walletId = wm.getActiveWalletId()
        val label = walletId?.let { wm.getWalletProfile(it)?.name } ?: "CoCo Wallet"

        promptPassword("Back Up Wallet") { password ->
            lifecycleScope.launch {
                try {
                    val api = authApi()
                    withContext(Dispatchers.IO) {
                        api.backup(token, address, mnemonic, "ed25519", "mainnet", label, password)
                    }
                    Toast.makeText(context, "Wallet backed up to CoCo account", Toast.LENGTH_LONG).show()
                } catch (e: ApiException) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Backup failed: ${e.message ?: "network error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun promptPassword(title: String, onPassword: (String) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = "Account password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage("Confirm your CoCo account password. Wallet backup words are encrypted on the server; restoring them requires this password.")
            .setView(layout)
            .setPositiveButton("Continue") { _, _ ->
                val pw = input.text.toString()
                if (pw.isBlank()) Toast.makeText(context, "Password required", Toast.LENGTH_SHORT).show()
                else onPassword(pw)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
