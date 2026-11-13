# CoCo Wallet

Standalone Android wallet for the CoCo / Company Coin ecosystem.

- **Package:** `com.companycoin.wallet`
- **Version:** 2.1.0 (versionCode 6)
- **Min SDK:** 24
- **Target SDK:** 34
- **License:** GNU General Public License v3.0
- **Tested APK:** `ANDROID/coco-wallet-2.1.0-debug.apk`

## Supported functionality

| Asset | Balance / receive address | Send |
|---|---|---|
| **CoCo (COCO)** | Yes | Yes — server-assisted, signed on-device |
| Bitcoin (BTC) | Yes | Yes — legacy P2PKH inputs; P2PKH, P2SH, and bech32 outputs |
| Ethereum (ETH) | Yes | Yes — EIP-155 native transfer |
| Solana (SOL) | Yes | Yes — SystemProgram transfer |
| USDC / USDT (ERC-20) | Yes | Yes — ERC-20 `transfer` |

CoCo signing happens on-device; the CoCo website API prepares and submits the
transfer. BTC uses mempool.space for UTXOs, fees, and broadcast. ETH/ERC-20 uses
the configured JSON-RPC endpoint. SOL uses the configured Solana RPC.

## Derivation

- **CoCo:** Substrate `mnemonicToMiniSecret` + Ed25519, SS58 address.
- **BTC/ETH/USDC/USDT:** standard BIP39 seed + BIP32:
  - BTC `m/44'/0'/0'/0/0`
  - ETH `m/44'/60'/0'/0/0`
- **SOL:** standard BIP39 seed + SLIP-0010:
  - `m/44'/501'/0'/0'/0'`

The BTC/ETH/SOL addresses match MetaMask, Electrum, and Phantom for the same
mnemonic. Wallets created by test builds <= 2.0.2 are re-derived on first launch;
the old non-standard keys are preserved under `legacy_*` preferences.

## Build

Requirements: JDK 17 and Android SDK 34.

```bash
cd wallet-app
./gradlew assembleDebug
```

The output APK is written under `wallet-app/app/build/outputs/apk/debug/`.

Release signing is intentionally not included. Provide your own keystore
through `COCO_STORE_FILE`, `COCO_STORE_PASSWORD`, `COCO_KEY_ALIAS`, and
`COCO_KEY_PASSWORD`.

## Tests

```bash
cd wallet-app
./gradlew :wallet-core:test
```

The offline suite covers CoCo/Ed25519, standard BIP39/BIP32/SLIP-0010 vectors,
ETH address/signing, BTC P2PKH transaction signing (verified with bitcoinj), and
Solana message/signature construction.

Opt-in live checks:

```bash
# Read-only Sepolia / BTC testnet / Solana devnet + Solana devnet simulation
MULTICHAIN_LIVE_TEST=1 ./gradlew :wallet-core:testDebugUnitTest \
  --tests 'com.companycoin.wallet.crypto.LiveMultiChainTest'

# Real Solana devnet send; fund the key at https://faucet.solana.com first
SOLANA_LIVE_TEST=1 SOLANA_TEST_PRIVATE_KEY=<64-hex> ./gradlew :wallet-core:testDebugUnitTest \
  --tests 'com.companycoin.wallet.crypto.LiveMultiChainTest'
```

## Known limitations

- Beta/debug build. It has not been independently audited.
- BTC sends from legacy P2PKH inputs. No RBF/CPFP, and the fee estimate is
  conservative rather than exact.
- ETH uses legacy `gasPrice`, not EIP-1559. ERC-20 sends use a fixed gas limit.
- Solana uses the standard 5,000-lamport fee; no priority fee.
- Transaction history is not shown in the UI.
- The testnet toggle uses the same seed material. Do not reuse a mainnet seed for
  testnet, and do not send real funds to testnet addresses.

## License

GPL-3.0. See `LICENSE`.
