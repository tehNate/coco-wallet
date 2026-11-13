# CoCo Wallet

Standalone Android wallet for the CoCo / Company Coin ecosystem.

- **Package:** `com.companycoin.wallet`
- **Version:** 2.0.2 (versionCode 5)
- **Min SDK:** 24
- **Target SDK:** 34
- **License:** GNU General Public License v3.0
- **Tested APK:** `ANDROID/coco-wallet-2.0.2-debug.apk`

## What it does

The wallet stores keys locally, supports biometric unlock and encrypted
wallet backup/restore, and connects to the CoCo chain and other supported
networks for balances, transfers, and governance voting.

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

## Security

This is a test/debug build of beta software. It has not been independently
audited. Do not use it to hold funds you cannot afford to lose. Never share
your seed phrase or private keys.

## License

GPL-3.0. See `LICENSE`.
