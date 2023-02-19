# Datomic browser — Electric Clojure example

https://user-images.githubusercontent.com/124158/219978031-939344eb-4489-4b97-af9f-4b2df38c70db.mp4

## Dependencies

* Datomic cloud dev local
* Datomic client API
* hyperfiddle.electric
* hyperfiddle.history (bundled with Electric) – experimental composable router
* datomic/missionary adapter (bundled with Electric) – experimental streaming adapter for Datomic

## Maturity: experimental

* **Websocket issue** - Electric uses a websocket, compatibility with Datomic Cloud Ions is unknown
* **core.async issue** – Electric is a FRP abstraction, so other async paradigms like core.async must be bridged. the Electric library includes an experimental Datomic<>Missionary adapter which is used here, but it's just a scrappy POC. What we really need is a high level Electric Clojure adapter for Datomic. 

## Getting Started

* Install datomic dev-local: https://docs.datomic.com/cloud/dev-local.html
* get mbrainz-subset dataset: https://docs.datomic.com/cloud/dev-local.html#samples
  * extract zip into datomic storage dir, that's it
