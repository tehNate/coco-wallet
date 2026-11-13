package com.companycoin.wallet.crypto

enum class CoinType(
    val displayName: String,
    val coinType: Long,
    val curve: String,
    val derivationPath: String,
    val addressPrefix: String,
    val defaultRpc: String,
    val testnetRpc: String,
    val explorerUrl: String,
    val testnetExplorerUrl: String,
    val symbol: String,
    val decimals: Int,
    val erc20Contract: String? = null,
    val testnetErc20Contract: String? = null,
    val chainId: Long? = null,
    val testnetChainId: Long? = null,
) {
    COCO(
        displayName = "CoCo",
        coinType = 499,
        curve = "Ed25519",
        derivationPath = "m/44'/499'/0'/0/0",
        addressPrefix = "SS58",
        defaultRpc = "https://companycoin.org",
        testnetRpc = "http://127.0.0.1:4000",
        explorerUrl = "https://companycoin.org/explorer.html?tx=",
        testnetExplorerUrl = "https://cocoio.cc/explorer.html?tx=",
        symbol = "COCO",
        decimals = 12,
    ),
    BITCOIN(
        displayName = "Bitcoin",
        coinType = 0,
        curve = "SECP256k1",
        derivationPath = "m/44'/0'/0'/0/0",
        addressPrefix = "1",
        defaultRpc = "https://mempool.space/api",
        testnetRpc = "https://mempool.space/testnet/api",
        explorerUrl = "https://mempool.space/tx/",
        testnetExplorerUrl = "https://mempool.space/testnet/tx/",
        symbol = "BTC",
        decimals = 8,
    ),
    ETHEREUM(
        displayName = "Ethereum",
        coinType = 60,
        curve = "SECP256k1",
        derivationPath = "m/44'/60'/0'/0/0",
        addressPrefix = "0x",
        defaultRpc = "https://ethereum-rpc.publicnode.com",
        testnetRpc = "https://sepolia.drpc.org",
        explorerUrl = "https://etherscan.io/tx/",
        testnetExplorerUrl = "https://sepolia.etherscan.io/tx/",
        symbol = "ETH",
        decimals = 18,
        chainId = 1,
        testnetChainId = 11155111,
    ),
    SOLANA(
        displayName = "Solana",
        coinType = 501,
        curve = "Ed25519",
        derivationPath = "m/44'/501'/0'/0/0",
        addressPrefix = "",
        defaultRpc = "https://api.mainnet-beta.solana.com",
        testnetRpc = "https://api.devnet.solana.com",
        explorerUrl = "https://solscan.io/tx/",
        testnetExplorerUrl = "https://solscan.io/tx/",
        symbol = "SOL",
        decimals = 9,
    ),
    USDC(
        displayName = "USD Coin",
        coinType = 60,
        curve = "SECP256k1",
        derivationPath = "m/44'/60'/0'/0/0",
        addressPrefix = "0x",
        defaultRpc = "https://ethereum-rpc.publicnode.com",
        testnetRpc = "https://sepolia.drpc.org",
        explorerUrl = "https://etherscan.io/tx/",
        testnetExplorerUrl = "https://sepolia.etherscan.io/tx/",
        symbol = "USDC",
        decimals = 6,
        erc20Contract = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
        testnetErc20Contract = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238",
        chainId = 1,
        testnetChainId = 11155111,
    ),
    USDT(
        displayName = "Tether",
        coinType = 60,
        curve = "SECP256k1",
        derivationPath = "m/44'/60'/0'/0/0",
        addressPrefix = "0x",
        defaultRpc = "https://ethereum-rpc.publicnode.com",
        testnetRpc = "https://sepolia.drpc.org",
        explorerUrl = "https://etherscan.io/tx/",
        testnetExplorerUrl = "https://sepolia.etherscan.io/tx/",
        symbol = "USDT",
        decimals = 6,
        erc20Contract = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
        testnetErc20Contract = "0xaa8E23Fb1079bE71E7c400E9893EfF2B59B3fC0d",
        chainId = 1,
        testnetChainId = 11155111,
    );

    fun rpc(useTestnet: Boolean): String = if (useTestnet) testnetRpc else defaultRpc
    fun explorer(useTestnet: Boolean): String = if (useTestnet) testnetExplorerUrl else explorerUrl
    fun getErc20Contract(useTestnet: Boolean): String? = if (useTestnet) testnetErc20Contract else erc20Contract
    fun chainId(useTestnet: Boolean): Long? = if (useTestnet) testnetChainId else chainId

    companion object {
        fun fromSymbol(symbol: String): CoinType? = entries.find { it.symbol == symbol.uppercase() }
        val erc20Tokens = listOf(USDC, USDT)
        fun isErc20(coin: CoinType) = coin in erc20Tokens
    }
}
