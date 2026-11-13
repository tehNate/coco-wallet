package com.companycoin.wallet

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.companycoin.wallet.crypto.*
import com.companycoin.wallet.databinding.FragmentWalletBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletFragment : Fragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!
    private lateinit var walletManager: MultiWalletManager
    private var selectedCoin: CoinType = CoinType.COCO
    private var rpcClients = mutableMapOf<CoinType, ChainRpcClient>()
    private var coinButtons = mutableMapOf<CoinType, Button>()
    private var useTestnet = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        walletManager = MultiWalletManager(requireContext())
        walletManager.migrateFromSingleWallet()

        rebuildRpcClients()

        initCoinButtons()
        selectCoin(CoinType.COCO)
        updateWalletUI()

        binding.btnRefresh.setOnClickListener { refreshBalance() }
        binding.btnSend.setOnClickListener { onSend() }
        binding.btnReceive.setOnClickListener { onReceive() }
        binding.btnBackupWallet.setOnClickListener { showBackup() }
        binding.btnCreateWallet.setOnClickListener { showCreateWalletDialog() }
        binding.btnImportWallet.setOnClickListener { showImportWalletDialog() }
        binding.walletNameLabel.setOnClickListener { showWalletSwitcher() }
    }

    fun rebuildRpcClients() {
        val prefs = requireContext().getSharedPreferences("wallet_prefs", 0)
        useTestnet = prefs.getBoolean("testnet_mode", false)
        binding.testnetBadge.visibility = if (useTestnet) View.VISIBLE else View.GONE

        rpcClients[CoinType.ETHEREUM] = EthereumRpcClient(CoinType.ETHEREUM.rpc(useTestnet), useTestnet)
        rpcClients[CoinType.BITCOIN] = BitcoinRpcClient(CoinType.BITCOIN.rpc(useTestnet), useTestnet)
        rpcClients[CoinType.SOLANA] = SolanaRpcClient(CoinType.SOLANA.rpc(useTestnet))
        rpcClients[CoinType.COCO] = CoCoChainClient(
            if (useTestnet) CoinType.COCO.testnetRpc else ServerConfig.base(requireContext()),
            null
        )
        rpcClients[CoinType.USDC] = rpcClients[CoinType.ETHEREUM]!!
        rpcClients[CoinType.USDT] = rpcClients[CoinType.ETHEREUM]!!
    }

    private fun initCoinButtons() {
        val mapping = mapOf(
            binding.coinCoco to CoinType.COCO, binding.coinBtc to CoinType.BITCOIN,
            binding.coinEth to CoinType.ETHEREUM, binding.coinSol to CoinType.SOLANA,
            binding.coinUsdc to CoinType.USDC, binding.coinUsdt to CoinType.USDT,
        )
        for ((btn, coin) in mapping) {
            coinButtons[coin] = btn
            btn.text = coin.symbol
            btn.setOnClickListener { selectCoin(coin) }
        }
    }

    private fun selectCoin(coin: CoinType) {
        selectedCoin = coin
        for ((c, btn) in coinButtons) {
            if (c == coin) {
                btn.setBackgroundColor(0xFF1a4a7a.toInt())
                btn.setTextColor(android.graphics.Color.WHITE)
            } else {
                btn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btn.setTextColor(0xFFe0e0e0.toInt())
            }
        }
        binding.balanceHeader.text = "${coin.displayName} Balance"
        binding.balanceAmount.text = "—"
        binding.balanceUsd.text = ""
        if (walletManager.hasWallet()) {
            binding.walletAddress.text = walletManager.getAddress(coin, useTestnet) ?: "—"
            refreshBalance()
        } else binding.walletAddress.text = "No wallet"
    }

    private fun refreshBalance() {
        if (!walletManager.hasWallet()) return
        val addr = walletManager.getAddress(selectedCoin, useTestnet) ?: return
        val client = rpcClients[selectedCoin] ?: return
        binding.balanceAmount.text = "Loading..."
        lifecycleScope.launch {
            var error: String? = null
            val balance = withContext(Dispatchers.IO) {
                try { client.getBalance(addr, selectedCoin) }
                catch (e: Exception) { error = e.message; null }
            }
            if (balance != null) {
                val bd = balance.toBigIntegerOrNull()
                val display = if (bd != null) {
                    val divisor = Math.pow(10.0, selectedCoin.decimals.toDouble())
                    String.format("%.${selectedCoin.decimals.coerceAtMost(6)}f", bd.toDouble() / divisor)
                } else balance
                binding.balanceAmount.text = "$display ${selectedCoin.symbol}"
            } else {
                binding.balanceAmount.text = "Error"
                Toast.makeText(context, "Balance failed: ${error ?: "network error"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onSend() {
        if (!walletManager.hasWallet()) { Toast.makeText(context, "Create a wallet first", Toast.LENGTH_SHORT).show(); return }
        WalletAuth.authenticate(
            this,
            "Authorize Send",
            "Confirm to send ${selectedCoin.symbol}",
        ) { showSendDialog() }
    }

    private fun showSendDialog() {
        val toInput = android.widget.EditText(requireContext()).apply { hint = "Recipient address" }
        val amountInput = android.widget.EditText(requireContext()).apply {
            hint = "Amount (${selectedCoin.symbol})"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Send ${selectedCoin.symbol}")
            .setMessage("Signed on-device. Seed never leaves.")
            .setView(android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL; addView(toInput); addView(amountInput)
            })
            .setPositiveButton("Send") { _, _ ->
                val to = toInput.text.toString().trim()
                val amount = amountInput.text.toString().trim()
                if (to.isBlank() || amount.isBlank()) {
                    Toast.makeText(context, "Enter recipient and amount", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                val client = rpcClients[selectedCoin]
                if (client == null) { Toast.makeText(context, "Chain not available", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                if (!client.isValidAddress(to, selectedCoin)) {
                    Toast.makeText(context, "Recipient address is invalid", Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                val pk = walletManager.getPrivateKey(selectedCoin)
                if (pk == null) { Toast.makeText(context, "Wallet key unavailable", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                lifecycleScope.launch {
                    var error: String? = null
                    val txHash = withContext(Dispatchers.IO) {
                        try { client.submitTransfer(pk, to, amount, selectedCoin) }
                        catch (e: Exception) { error = e.message; null }
                    }
                    if (txHash != null) {
                        val short = if (txHash.length > 18) txHash.take(10) + "…" + txHash.takeLast(6) else txHash
                        WalletDiag.lastSendError = null
                        WalletDiag.lastSendStatus = "Included: $short"
                        Toast.makeText(context, "Sent ✓ included ($short)", Toast.LENGTH_LONG).show()
                        refreshBalance()
                    } else {
                        WalletDiag.lastSendError = error ?: "unknown error"
                        WalletDiag.lastSendStatus = null
                        Toast.makeText(context, "Send failed: ${error ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun onReceive() {
        val addr = walletManager.getAddress(selectedCoin, useTestnet) ?: run {
            Toast.makeText(context, "Create wallet first", Toast.LENGTH_SHORT).show(); return
        }
        runCatching {
            val bitMatrix = QRCodeWriter().encode(addr, BarcodeFormat.QR_CODE, 400, 400)
            val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.RGB_565)
            for (x in 0 until 400) for (y in 0 until 400)
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            val iv = android.widget.ImageView(requireContext()).apply { setImageBitmap(bmp) }
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Receive ${selectedCoin.symbol}")
                .setMessage(addr)
                .setView(iv)
                .setPositiveButton("Copy Address") { _, _ ->
                    (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("address", addr))
                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Close", null).show()
            dialog?.window?.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }.onFailure { Toast.makeText(context, "QR error", Toast.LENGTH_SHORT).show() }
    }

    private fun showBackup() {
        val walletId = walletManager.getActiveWalletId()
        if (walletId == null) {
            Toast.makeText(context, "No wallet", Toast.LENGTH_SHORT).show(); return
        }

        val executor = ContextCompat.getMainExecutor(requireContext())
        BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val mnemonic = walletManager.getMnemonic() ?: return
                val addrs = CoinType.entries.joinToString("\n") { c ->
                    "${c.symbol}: ${walletManager.getAddress(c, useTestnet) ?: "—"}"
                }

                val addrView = android.widget.TextView(requireContext()).apply {
                    text = mnemonic
                    textSize = 14f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(0xFFF5C918.toInt())
                    setPadding(16, 16, 16, 16)
                    setBackgroundColor(0xFF1A1A1A.toInt())
                }

                val layout = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    addView(android.widget.TextView(requireContext()).apply {
                        text = "Seed Phrase (write down & store safely):"
                        textSize = 14f; setTextColor(0xFF888888.toInt())
                    })
                    addView(addrView)
                    addView(android.widget.TextView(requireContext()).apply {
                        text = "\nDerived Addresses:\n$addrs"
                        textSize = 12f; setTextColor(0xFF888888.toInt())
                    })
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("Seed Phrase")
                    .setView(layout)
                    .setPositiveButton("Copy & Close") { _, _ ->
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("seed", mnemonic))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }.show()
            }

            override fun onAuthenticationError(e: Int, m: CharSequence) {
                Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
            }
        }).authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm Identity")
            .setSubtitle("View seed phrase")
            .setNegativeButtonText("Cancel").build())
    }

    private fun showCreateUi() {
        binding.walletAddress.text = "No wallet"; binding.balanceAmount.text = "—"
        binding.walletNameLabel.text = "No Wallet"
        binding.btnCreateWallet.visibility = View.VISIBLE; binding.btnImportWallet.visibility = View.VISIBLE
        binding.btnBackupWallet.visibility = View.GONE; binding.btnSend.visibility = View.GONE; binding.btnReceive.visibility = View.GONE
    }

    private fun showWalletUi() {
        binding.btnCreateWallet.visibility = View.GONE; binding.btnImportWallet.visibility = View.GONE
        binding.btnBackupWallet.visibility = View.VISIBLE; binding.btnSend.visibility = View.VISIBLE; binding.btnReceive.visibility = View.VISIBLE
        updateWalletNameLabel()
        selectCoin(selectedCoin)
    }

    private fun updateWalletUI() {
        if (walletManager.hasWallet()) showWalletUi()
        else showCreateUi()
    }

    private fun updateWalletNameLabel() {
        val profile = walletManager.getActiveWalletId()?.let { walletManager.getWalletProfile(it) }
        binding.walletNameLabel.text = profile?.getDisplayName() ?: "Wallet"
        binding.walletNameLabel.setOnClickListener { showWalletSwitcher() }
    }

    private fun showWalletSwitcher() {
        if (!walletManager.hasWallet()) return
        val wallets = walletManager.getAllWallets()
        val activeId = walletManager.getActiveWalletId()
        val items = wallets.map { w ->
            "${w.getDisplayName()} (${w.getShortId()})${if (w.id == activeId) " ✓" else ""}"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Switch Wallet")
            .setItems(items) { _, which ->
                val selected = wallets[which]
                walletManager.switchWallet(selected.id)
                updateWalletNameLabel()
                selectCoin(selectedCoin)
                Toast.makeText(context, "Switched to ${selected.getDisplayName()}", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Add New") { _, _ -> showCreateWalletDialog() }
            .setNeutralButton("Import Wallet") { _, _ -> showImportWalletDialog() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showCreateWalletDialog() {
        val nameInput = android.widget.EditText(requireContext()).apply {
            hint = "My Wallet"
            setText("Wallet ${System.currentTimeMillis().toString().takeLast(4)}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Create New Wallet")
            .setMessage("Generate a new 12-word seed phrase and derive 6 coin addresses.\n\nEach wallet has its own independent seed - write it down!")
            .setView(nameInput)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim().ifBlank { "Wallet ${System.currentTimeMillis().toString().takeLast(4)}" }
                lifecycleScope.launch {
                    val profile = withContext(Dispatchers.IO) {
                        try { walletManager.createWallet(name) } catch (e: Exception) { null }
                    }
                    if (profile != null) {
                        updateWalletUI()
                        syncWalletToAccount()
                        val mnemonic = walletManager.getMnemonic() ?: "⚠️ Could not retrieve seed"
                        val seedView = android.widget.TextView(requireContext()).apply {
                            text = mnemonic
                            textSize = 14f
                            typeface = android.graphics.Typeface.MONOSPACE
                            setTextColor(0xFFF5C918.toInt())
                            setPadding(16, 16, 16, 16)
                            setBackgroundColor(0xFF1A1A1A.toInt())
                        }
                        val layout = android.widget.LinearLayout(requireContext()).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            addView(android.widget.TextView(requireContext()).apply {
                                text = "Wallet: ${profile.getDisplayName()}\n\nYour seed phrase (write this down!):"
                                textSize = 14f; setTextColor(0xFFCCCCCC.toInt())
                            })
                            addView(seedView)
                            addView(android.widget.TextView(requireContext()).apply {
                                text = "\n⚠️ Anyone with this phrase controls ALL coins. Write it on paper. Never share."
                                textSize = 12f; setTextColor(0xFFE74C3C.toInt())
                            })
                        }
                        AlertDialog.Builder(requireContext())
                            .setTitle("✅ Wallet Created!")
                            .setView(layout)
                            .setPositiveButton("I've Saved It") { _, _ ->
                                Toast.makeText(context, "Keep your seed phrase safe!", Toast.LENGTH_LONG).show()
                            }.show()
                    } else {
                        Toast.makeText(context, "Failed to create wallet", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showImportWalletDialog() {
        val nameInput = android.widget.EditText(requireContext()).apply { hint = "Imported Wallet" }
        val phraseInput = android.widget.EditText(requireContext()).apply {
            hint = "Paste 12/24-word phrase OR 64-hex raw Ed25519 seed"
            setLines(3)
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(nameInput)
            addView(phraseInput)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Import Wallet")
            .setView(container)
            .setMessage("Enter your existing 12 or 24-word BIP39 phrase, or the raw 32-byte Ed25519 seed (64 hex chars). All coin addresses are derived on-device.")
            .setPositiveButton("Import") { _, _ ->
                val name = nameInput.text.toString().trim().ifBlank { "Imported ${System.currentTimeMillis().toString().takeLast(4)}" }
                val phrase = phraseInput.text.toString().trim()
                if (phrase.isBlank()) {
                    Toast.makeText(context, "Enter seed phrase or raw seed", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                val rawSeed = phrase.removePrefix("0x")
                val isRaw = rawSeed.matches(Regex("^[0-9a-fA-F]{64}$"))
                if (!isRaw && !LocalWallet.isValidMnemonic(phrase)) {
                    Toast.makeText(context, "Invalid mnemonic/raw seed - check the words or hex", Toast.LENGTH_SHORT).show(); return@setPositiveButton
                }
                lifecycleScope.launch {
                    val profile = withContext(Dispatchers.IO) {
                        try {
                            if (isRaw) walletManager.importFromSeed(rawSeed, name)
                            else walletManager.importWallet(phrase, name)
                        } catch (e: Exception) { null }
                    }
                    if (profile != null) {
                        updateWalletUI()
                        syncWalletToAccount()
                        Toast.makeText(context, "Imported: ${profile.getDisplayName()}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun syncWalletToAccount() {
        val token = SecureSession.token(requireContext()) ?: return
        val testnet = requireContext().getSharedPreferences("wallet_prefs", 0)
            .getBoolean("testnet_mode", false)
        val api = AuthApi(if (testnet) CoinType.COCO.testnetRpc else ServerConfig.base(requireContext()))
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { WalletSync.saveAll(walletManager, api, token) }
        }
    }

    fun selectCoinExternal(coin: CoinType) { if (::walletManager.isInitialized) selectCoin(coin) }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
