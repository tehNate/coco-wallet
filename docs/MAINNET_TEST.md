# CoCo Wallet — Mainnet Test

**Date:** 2026-11-16  
**App:** CoCo Wallet 2.1.1 (versionCode 7)  
**APK SHA256:** `668f62cc327f1630f308a95d09fd4831c1f9b6eb4bbd964f3443117850370a50`

## Wallet

- Wallet name: `Wallet 7181`
- CoCo address: `5Gm7oqxTTveus8RtxCiasqRgc6EFLtPJcN3BA43DXaxVGo3C`

## Receive test

- Sent 10 COCO from the 204 hot wallet `5EZV81eQca94RspnWTQ4zpakpv2UrwLPZ7oUZXyRwUuSLNd6`.
- Funding tx: `0xc4527625499ed196eb896d87bcd9f5e5ab6ecc0e4d67ff3d79e0bd6ab19ad335`
- Finalized block hash: `0xf5953025b91486b31b1b57feb4b9057bee82e5c05b3bada24253e98eecd83517`
- The app refreshed to `10.000000 COCO`.

## Send test

- Sent 1 COCO from the app wallet to `5EZV81eQca94RspnWTQ4zpakpv2UrwLPZ7oUZXyRwUuSLNd6`.
- Block number: `2,828,933`
- Block hash: `0x139f0bb257a5be036ea79a18be760b9274716d84815d32a2576aa9af0bd504c2`
- Tx hash: `0x893cc1649487c683eb0086037b3114a8bb75c63cadd866598f088df79396d3df`
- Event: `balances.Transfer` from the app address to the hot wallet, amount `1000000000000` planck (1 COCO).
- App balance after send: `8.999891842854 COCO`.
- Hot wallet balance after send: `104063.521473316466 COCO`.
- The wallet and balance persisted across an app restart.

## Gateway note

The successful mainnet transfer used the beta gateway:

`https://cocoio.cc` (Settings → Use beta server).

The production gateway `https://companycoin.org` timed out for sends during this test and is being revalidated. Use the beta gateway until the production path is confirmed.

## BTC / ETH / SOL

BTC, ETH, and SOL sending are implemented and covered by offline vector tests
and a Solana devnet simulation, but have not been mainnet-tested. They are
marked **coming soon** in the release announcement.
