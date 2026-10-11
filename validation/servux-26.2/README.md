# Servux 26.2 repair validation

This fork's `0.5.1-zedo.1+mc26.2` repairs two independent failures:

- Servux Boolean permission nodes default to null, meaning no override. Keep explicit
  true/false decisions, and use the existing command-level permission check when null.
  The previous unboxing caused command-tree synchronization to fail during player login.
- Include FoxifiedNetworkingAPI `0.2.1+mc26.2`. Its upstream fix
  [7b2c0d3](https://github.com/CagayakeGirls/FoxifiedNetworkingAPI/commit/7b2c0d378c3dbe021f8d64dcaa7b9f295c12f2eb)
  handles FakePlayer connections whose Netty channel is null.

The base is `CagayakeGirls/servux-neoforge`, branch `neoforge/26.2`, commit
`7711c49502848077e3b2d10aa97dd625635cc8e2`. Build with JDK 25:

```sh
bash gradlew build --console=plain
```

## Isolated server regression

`RepairProbe.java` is a test-only NeoForge mod. Compile with `javac --release 25`,
using the NeoForge 26.2.0.88 dedicated server's runtime libraries and the built Servux
JAR as the classpath. Package the resulting `probe/*.class` files with the accompanying
metadata at `META-INF/neoforge.mods.toml`. Load the probe only in a disposable server
with Servux and FoxifiedClassTweaker 0.1.2; the repaired Servux embeds its networking API.

Run separate empty worlds with original Servux plus standalone networking API 0.2.1,
and with this repaired Servux. Bind test servers to loopback, use separate ports,
and keep their worlds/configuration outside production. The probe runs on ServerStartedEvent.
Stop normally after collecting its markers, and remove the disposable test environment.

The original permission bug produces `REPAIR_FAILED java.lang.NullPointerException`
in `PermissionsHelper.checkPlayerPermission`, even with the repaired networking API.
The repaired version produces `REPAIR_PASSED assertions=61`. Coverage includes:

- FakePlayerFactory.getMinecraft and get(level, profile), including a null Netty channel.
- Five permission levels against five requirements, for null and unregistered nodes.
- Command-tree synchronization for test player objects at all five permission levels.
- Console permissions, explicit allow/deny, and ordinary/OP null fallbacks.

Permission-level simulation affects test objects only. The controlled permission provider
is restored in finally. Do not install this probe on production servers.

## Recorded result (2026-10-11)

The JDK 25 build and both isolated regression cases passed on NeoForge 26.2.0.88.
The repaired JAR was subsequently deployed with its ClassTweaker dependency. Three existing
clients successfully logged in, including a previously failing player and a non-OP player;
server join records and player-list observations confirmed entry. No known permission,
FakePlayer network, or Ticking GridNode regression occurred during the observed window.
Broader gameplay feedback remains pending; isolated tests do not prove every mod interaction.

AE2 26.2.1-alpha's Platform bytecode and the original crash stack use the same
FakePlayerFactory.get path covered by the probe; this is the specific equivalent crash test.
The original networking API 0.2.0 had previously reproduced the null-channel failure,
and upstream 0.2.1 successfully created the fake player.

Deployed JAR SHA256:
`998eae95518f9b0acbeb2d886c6b4b2eca2d851fa8b13317bc3ccc1deee4633a`.
Embedded networking JAR SHA256:
`98081dbed3471d5bd7bfeceffe5f36f1ef650f1165c88ad6f6240ca3a71f0087`.
Archive timestamps can vary between builds, so rebuilt JAR bytes may have a different hash.
