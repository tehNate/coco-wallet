# CoCo Wallet

Standalone Android wallet for the CoCo / Company Coin ecosystem.

- **Package:** `com.companycoin.wallet`
- **Version:** 2.1.1 (versionCode 7)
- **Min SDK:** 24
- **Target SDK:** 34
- **License:** GNU General Public License v3.0
- **Tested APK:** `ANDROID/coco-wallet-2.1.1-debug.apk`

## Supported functionality

| Asset | Balance / receive address | Send |
|---|---|---|
| **CoCo (COCO)** | Yes | Yes — server-assisted, signed on-device |
| Bitcoin (BTC) | Yes | Implemented — mainnet testing pending |
| Ethereum (ETH) | Yes | Implemented — mainnet testing pending |
| Solana (SOL) | Yes | Implemented — mainnet testing pending |
| USDC / USDT (ERC-20) | Yes | Implemented — mainnet testing pending |

CoCo signing happens on-device; the CoCo website API prepares and submits the
transfer. BTC uses mempool.space for UTXOs, fees, and broadcast. ETH/ERC-20 uses
the configured JSON-RPC endpoint. SOL uses the configured Solana RPC.

## Mainnet status (2026-11-16)

CoCo send/receive was verified on CoCo mainnet with a real 1 COCO transfer.
See [`docs/MAINNET_TEST.md`](docs/MAINNET_TEST.md) for the addresses, tx hashes,
and block proof.

The successful test used the beta gateway:

**Settings → Use beta server (cocoio.cc)**

The production `companycoin.org` gateway timed out for sends during this test and
is being revalidated. Use the beta gateway until the production path is confirmed.

BTC, ETH, and SOL are implemented and covered by offline vector tests and a
Solana devnet simulation, but they have not been mainnet-tested. They are marked
**coming soon** in the release announcement.

### 2.1.1 fix

COCO balance responses are now converted from human COCO to planck before the
UI applies its decimal formatting. This fixes an integer COCO balance showing as
`0.000000` (for example, 10 COCO now displays as `10.000000`).

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

A real CoCo mainnet send/receive test is documented in
[`docs/MAINNET_TEST.md`](docs/MAINNET_TEST.md).

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
- The production `companycoin.org` send gateway timed out during the 2026-11-16
  test. Use the beta gateway (`cocoio.cc`) until the production path is
  revalidated.
- BTC sends from legacy P2PKH inputs. No RBF/CPFP, and the fee estimate is
  conservative rather than exact.
- ETH uses legacy `gasPrice`, not EIP-1559. ERC-20 sends use a fixed gas limit.
- Solana uses the standard 5,000-lamport fee; no priority fee.
- Transaction history is not shown in the UI.
- The testnet toggle uses the same seed material. Do not reuse a mainnet seed for
  testnet, and do not send real funds to testnet addresses.

## License

GPL-3.0. See `LICENSE`.
