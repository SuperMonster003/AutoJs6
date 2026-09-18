# P4.2 BKS 兼容证据 (2026-09-18)

问题 (Q8): 宿主的 `KeyStoreHelper.loadBks` 先用系统 `KeyStore.getInstance("BKS", "BC")`, 失败再回退到打包在宿主内的 SpongyCastle 1.58.0.0 (`org.spongycastle`, 2.28 MiB 归属 DEX). 系统 BC 是否足以加载宿主可能遇到的全部 BKS 密钥库?

结论: 是. 六个环境 (API 24 / 28 / 31 / 33 / 33 / 35, 系统 BC 1.54 / 1.57 / 1.68) 全部通过, 回退分支从未被触发; P4.3 据此移除回退与 SpongyCastle 依赖.

## 样本

同一对 RSA 2048 密钥与自签名证书 (SHA-256 `366f848e93cbcf4ed4cfec91f6fecb399ebb6b0728aa5ce7a46b63ad35030613`) 写入不同格式; 生成脚本 `build/tmp-size/p4/BksGen.java` (读取 keytool JKS 后用指定 provider 类以指定类型写出), 用 Temurin 11 运行 (Oracle JDK 8 / 21 拒绝未签名的 SpongyCastle jar: `JCE cannot authenticate the provider SC`).

| 样本 | 生成方式 | 格式头 | 字节 |
| --- | --- | --- | --- |
| `keytool-jdk8.jks` | JDK 1.8.0_312 `keytool -genkeypair -storetype JKS` | `feed feed 0000 0002` | 2,222 |
| `spongy158-bks-v2.bks` | SpongyCastle prov 1.58.0.0, 类型 `BKS` (宿主原回退路径写出的格式) | `0000 0002` | 2,227 |
| `spongy158-bks-v1.bks` | SpongyCastle prov 1.58.0.0, 类型 `BKS-V1` | `0000 0001` | 2,227 |
| `bc170-bks-v2.bks` | BouncyCastle bcprov-jdk15on 1.70, 类型 `BKS` | `0000 0002` | 2,227 |
| `bc181-bks-v2.bks` | BouncyCastle bcprov-jdk18on 1.81, 类型 `BKS` | `0000 0002` | 2,227 |
| `default_key_store.bks` | 仓库资产 `app/src/main/assets/default_key_store.bks` (口令 / 别名 `AutoJs6`, 证书 `c4530b8e…`) | `0000 0002` | 2,172 |
| `autojs.keystore` | 仓库资产 (JKS 格式, 口令未知, 已被 `appMergedAssetPruneIncludes` 剪除, 不进入 APK) | `feed feed 0000 0002` | 2,062 |

BouncyCastle 1.70 与 1.81 不再注册 `BKS-V1` 类型 (provider 的 KeyStore 服务只有 `BKS` / `BouncyCastle` / `PKCS12*` / `BCFKS*`), 所以 v1 样本只由 SpongyCastle 1.58 产生; 这与 Android 系统 BC 一致 (下表: 所有 API 级别都没有 `BKS-V1` 类型, 但 `BKS` 类型能读取 v1 文件).

## 设备结果

脚本 `build/tmp-size/p4-bks-smoke.js` 在宿主 6.8.0 (P3 构建, 仍含 SpongyCastle 回退) 内对每个样本执行: (a) 系统 `KeyStore.getInstance("BKS", "BC")` 加载并读出别名, 私钥与证书摘要; (b) 宿主 `KeyStoreHelper.loadBks`; (c) `KeyStoreHelper.loadKeyStore` (按扩展名分派). JKS 样本经 `KeyStoreHelper.loadJks`. 另在设备上用 `KeyStoreHelper.createKeyStore` + `writeKeyStore` (插件 `CertCreator` 的同一路径) 各写一份 `.bks` / `.jks` 再重新加载.

| 环境 | 系统 BC | 5 个 BKS 样本 x (a)(b)(c) | JKS 样本 | 设备写出 `.bks` / `.jks` 回读 | (b) 实际 provider |
| --- | --- | --- | --- | --- | --- |
| AVD API 24 (x86, Android 7.0) | 1.54 | 15/15 通过 | 通过 | 通过 (BKS v2 头 `0000 0002`) | BC (系统) |
| Sony G8441 (API 28, Android 9) | 1.57 | 15/15 通过 | 通过 | 通过 | BC (系统) |
| Sony XQ-AT72 (API 31, Android 12) | 1.68 | 15/15 通过 | 通过 | 通过 | BC (系统) |
| Sony XQ-DQ72 (API 33, Android 13) | 1.68 | 15/15 通过 | 通过 | 通过 | BC (系统) |
| Redmi 22120RN86C (API 33, Android 13) | 1.68 | 15/15 通过 | 通过 | 通过 | BC (系统) |
| Xiaomi 23046RP50C (API 35, Android 15) | 1.68 | 15/15 通过 | 通过 | 通过 | BC (系统) |

每个环境相同的附带观察:

- `KeyStore.getInstance("BKS-V1", "BC")`: `no such algorithm: BKS-V1 for provider BC` (类型不存在, 但 `BKS` 类型读取 v1 文件成功).
- `KeyStore.getInstance("JKS")`: `JKS KeyStore not available` (Android 没有 JKS provider, 宿主的纯 Java 读取器 `JKS.java` 承担 JKS).
- 系统 `BKS` 加载 JKS 文件: `Wrong version of key store.` (格式区分正常).
- `autojs.keystore` 用 6 个候选口令均报 `Incorrect password, or integrity check failed.`: 文件按 JKS 解析, 口令未知; 该资产不进入 APK, 与本决策无关.
- 加载结果中 `provider=BC` 表示系统 provider; SpongyCastle 只以 `provider=SC` 出现在 `JksKeyStore` 的元数据里 (它把 SpongyCastle provider 对象当作 `KeyStore` 构造参数), P4.3 改为不注册任何服务的 `JksProvider`.

原始输出: `build/tmp-size/p4/result-<serial>.txt` (会话临时目录, 不入库); 关键行摘录:

```
device: google Android SDK built for x86 api 24 (7.0), host 6.8.0
OK  providers -> AndroidNSSP 1, AndroidOpenSSL 1, CertPathProvider 1, AndroidKeyStoreBCWorkaround 1, BC 1.54, HarmonyJSSE 1, AndroidKeyStore 1
OK  BC KeyStore types -> BKS, BouncyCastle, PKCS12
--- spongy158-bks-v1.bks (2227 bytes)
OK    system BKS/BC -> type=BKS provider=BC aliases=[autojs6] key=RSA/PKCS#8 cert=366f848e93cbcf4e
OK    KeyStoreHelper.loadBks -> type=BKS provider=BC aliases=[autojs6] key=RSA/PKCS#8 cert=366f848e93cbcf4e
--- device-created .bks
OK    createKeyStore + writeKeyStore -> type=BKS provider=BC bytes=2226 header=2
OK    reload via system BKS/BC -> type=BKS provider=BC aliases=[device] key=RSA/PKCS#8 cert=366f848e93cbcf4e
```

## 决策 (P4.3)

- `modules/apk-verifier` 的 `KeyStoreHelper.loadBks` 只使用 `KeyStore.getInstance("BKS", "BC")`; 桌面 JVM 上的单测以同名注册 bcprov-jdk15on 1.70 (`testImplementation`, 不进入 APK).
- 宿主不再依赖 `modules/apk-signer`, 因而不再打包 `org.spongycastle`; SpongyCastle 仍由 `modules/apk-signer` (证书创建 `CertCreator` / `DistinguishedNameValues`, 签名工具 `ApkSignerTool`) 与 `plugins/apk-builder-template` 使用.
- 插件在设备上创建 BKS 一直走系统 BC (`createKeyStore` -> `loadBks(null)` 首选系统), 因此插件写出的密钥库与本证据的 `device-created .bks` 相同, 不受影响.
