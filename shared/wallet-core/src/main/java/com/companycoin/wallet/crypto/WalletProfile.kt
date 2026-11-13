package com.companycoin.wallet.crypto

import java.util.UUID

/**
 * Represents a single wallet profile in the multi-wallet system.
 * Each wallet has its own mnemonic seed and derived addresses.
 */
data class WalletProfile(
    val id: String,                    // Unique identifier (UUID)
    val name: String,                  // User-friendly name (e.g., "Main Wallet")
    val createdAt: Long,               // Timestamp when created
    val isLocked: Boolean = false      // Whether biometric lock is enabled
) {
    companion object {
        fun create(name: String = "Wallet ${System.currentTimeMillis().toString().takeLast(4)}"): WalletProfile {
            return WalletProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis()
            )
        }
    }
    
    /** Short display name for UI */
    fun getDisplayName(): String {
        return name.ifBlank { "Wallet ${id.takeLast(4)}" }
    }
    
    /** Short address preview for selection UIs */
    fun getShortId(): String {
        return id.takeLast(8)
    }
}
