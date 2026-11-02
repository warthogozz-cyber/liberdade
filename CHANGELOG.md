# Changelog

## 0.1.0 (2026-11-02)


### Features

* **api:** add Money value object with largest-remainder allocation ([86b3bda](https://github.com/warthogozz-cyber/liberdade/commit/86b3bda77339dcd70172ddc417006faa3b189edb))
* **api:** add RFC 9457 problem handler and UTC clock ([e9923e2](https://github.com/warthogozz-cyber/liberdade/commit/e9923e257597994befe1b1b63736608a7e63f99b))
* **api:** add Spring Boot skeleton with health probes and coverage gates ([e57ef06](https://github.com/warthogozz-cyber/liberdade/commit/e57ef060c04c51b405df924b7b1411e647366f92))
* **api:** serialize Money as decimal string in JSON ([0585b01](https://github.com/warthogozz-cyber/liberdade/commit/0585b0198ea79f8a1ba2147940b47ae027f16273))
* **infra:** add an API healthcheck to the compose stack ([775395a](https://github.com/warthogozz-cyber/liberdade/commit/775395a6b3a000df4b2cf9911ccaabb20ac45954))
* **infra:** add API container image and local compose stack ([b220660](https://github.com/warthogozz-cyber/liberdade/commit/b2206602900a2ba8f358526ff29715ea7a78583f))
* **web:** add bigint money helpers for BRL input and formatting ([eb64c8d](https://github.com/warthogozz-cyber/liberdade/commit/eb64c8d57a5c730ef8ebc7797d1e7c3472967abd))
* **web:** add home page with pt-BR messages and design tokens ([282a644](https://github.com/warthogozz-cyber/liberdade/commit/282a644e3a278b1a7ae8ae755784e4dd890a45be))
* **web:** add Vite React scaffold with module boundary lint rules ([28e7129](https://github.com/warthogozz-cyber/liberdade/commit/28e71299b026064cc735981cef9b736ad014c84c))


### Bug Fixes

* **api:** log server errors raised by the framework ([4c9c563](https://github.com/warthogozz-cyber/liberdade/commit/4c9c563011ef87e7820cbac64f23c6afd510a48e))
* **build:** pin commons-lang3 on the Gradle build classpath ([#19](https://github.com/warthogozz-cyber/liberdade/issues/19)) ([fbf3583](https://github.com/warthogozz-cyber/liberdade/commit/fbf3583cfbd219220521873d3d1b388d012f752a))
* **ci:** install wrangler as a pinned dev dependency for previews ([1722f9a](https://github.com/warthogozz-cyber/liberdade/commit/1722f9af5481cd80bf681a7e409db4e40afb7710))
* **ci:** produce the JaCoCo report on every build ([358023c](https://github.com/warthogozz-cyber/liberdade/commit/358023c3504de9bea3cd0025e8bb951fe41ffa4c))
* **ci:** start the first release at 0.1.0 ([#18](https://github.com/warthogozz-cyber/liberdade/issues/18)) ([4e3c2f0](https://github.com/warthogozz-cyber/liberdade/commit/4e3c2f08ef7ae40044501987170a3c0cbbfcca28))
* **repo:** label issue templates with the type taxonomy ([f5491dd](https://github.com/warthogozz-cyber/liberdade/commit/f5491dd2e060d07938035b671429659509816d2d))
* **web:** close the relative-import hole in the boundary rules ([5e66a6d](https://github.com/warthogozz-cyber/liberdade/commit/5e66a6ddd3dc4cf2c6299277ac394110df297e95))
* **web:** write NBSP and minus as escape sequences ([6c1c3fd](https://github.com/warthogozz-cyber/liberdade/commit/6c1c3fd591812395aeafbc0f0c19e1677ecac3df))
