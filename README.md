# CoCo Wallet

Standalone Android wallet for the CoCo / Company Coin ecosystem.

> **Status: test build with incomplete multi-chain support.**
> Do not use this build to receive BTC, ETH, SOL, USDC, or USDT yet.
> Non-CoCo sending is not implemented, and the current ETH receive-address
> derivation is incorrect. See **Known limitations** below.

- **Package:** `com.companycoin.wallet`
- **Version:** 2.0.2 (versionCode 5)
- **Min SDK:** 24
- **Target SDK:** 34
- **License:** GNU General Public License v3.0
- **Tested APK:** `ANDROID/coco-wallet-2.0.2-debug.apk`

## Supported functionality

| Asset | Balance / receive address | Send |
|---|---|---|
| **CoCo (COCO)** | Yes | Yes — server-assisted, signed on-device |
| Bitcoin (BTC) | Yes | **Not implemented** |
| Ethereum (ETH) | Yes | **Not implemented** |
| Solana (SOL) | Yes | **Not implemented** |
| USDC / USDT (ERC-20) | Yes | **Not implemented** |

The CoCo send path signs the payload on-device and uses the CoCo website API to
prepare and submit the transfer. The seed never leaves the device.

## Known limitations

- **Non-CoCo sending is not implemented.** The Bitcoin, Ethereum, and Solana
  clients return `null` from `submitTransfer`; the Send button will fail for
  those assets.
- **The BTC/ETH/SOL derivation is not standard BIP44/BIP39.** The app uses
  Substrate's `mnemonicToMiniSecret` and a custom child-key derivation. The same
  seed phrase imported into MetaMask, Electrum, Phantom, or another BIP44 wallet
  will produce different addresses. Do not import the seed elsewhere expecting
  the same BTC/ETH/SOL addresses.
- **The current ETH receive address is incorrect.** The derivation hashes the
  uncompressed public key including the `0x04` prefix. The displayed ETH address
  is not the standard Ethereum address for the key. Do not send ETH to it.
- **This is a debug/test build.** It has not been independently audited. Do not
  use it to hold funds you cannot afford to lose.

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

The current unit tests cover CoCo/Ed25519 derivation, mnemonic validation, SS58
encoding, and sign/verify. The live API/send tests require environment variables
and are skipped by default. There are no BTC/ETH/SOL derivation or send tests.

## License

GPL-3.0. See `LICENSE`.
