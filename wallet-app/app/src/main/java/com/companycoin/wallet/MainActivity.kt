package com.companycoin.wallet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.companycoin.wallet.crypto.CoinType
import com.companycoin.wallet.crypto.MultiWalletManager
import com.companycoin.wallet.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFragment: Fragment? = null
    private var walletFragment: WalletFragment? = null
    private var portfolioFragment: PortfolioFragment? = null
    private var settingsFragment: SettingsFragment? = null
    lateinit var walletManager: MultiWalletManager
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        walletManager = MultiWalletManager(this)
        // Run the single-wallet migration before address/profile repairs: an
        // upgraded install may not have a multi-wallet list yet.
        walletManager.migrateFromSingleWallet()
        walletManager.repairLegacyAddresses()
        walletManager.repairWalletProfiles()
        initFragments()
        setupBottomNavigation()
        binding.bottomNavigation.selectedItemId = R.id.nav_coins
    }

    private fun initFragments() {
        walletFragment = WalletFragment()
        portfolioFragment = PortfolioFragment()
        settingsFragment = SettingsFragment()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_coins -> {
                    walletFragment?.let {
                        showFragment(it)
                        it.selectCoinExternal(CoinType.COCO)
                    }
                    true
                }
                R.id.nav_portfolio -> {
                    portfolioFragment?.let { showFragment(it) }
                    true
                }
                R.id.nav_settings -> {
                    settingsFragment?.let { showFragment(it) }
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        if (currentFragment === fragment) return
        val transaction = supportFragmentManager.beginTransaction()
        walletFragment?.let { if (it.isAdded) transaction.hide(it) }
        portfolioFragment?.let { if (it.isAdded) transaction.hide(it) }
        settingsFragment?.let { if (it.isAdded) transaction.hide(it) }
        if (!fragment.isAdded) transaction.add(R.id.fragment_container, fragment)
        transaction.show(fragment)
        transaction.commitNow()
        currentFragment = fragment
    }
}
