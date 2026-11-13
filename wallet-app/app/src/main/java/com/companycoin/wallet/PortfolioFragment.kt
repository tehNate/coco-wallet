package com.companycoin.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.companycoin.wallet.crypto.CoinType

class PortfolioFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)

        val wm = (activity as MainActivity).walletManager

        if (!wm.hasWallet()) {
            layout.addView(TextView(requireContext()).apply {
                text = "No wallet found.\nCreate or import one in the Coins tab."
                textSize = 16f
            })
            return layout
        }

        for (coin in CoinType.entries) {
            val addr = wm.getAddress(coin) ?: continue
            val row = LinearLayout(requireContext())
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 16, 0, 16)

            val symbol = TextView(requireContext()).apply {
                text = coin.symbol
                textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val address = TextView(requireContext()).apply {
                text = addr; textSize = 11f
                val typeface = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("address", addr))
                    Toast.makeText(context, "${coin.symbol} copied", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(symbol); row.addView(address)
            layout.addView(row)
        }
        return layout
    }
}
