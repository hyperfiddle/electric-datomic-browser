# Datomic browser — Electric Clojure example

Video (todo)

## Dependencies

* Datomic cloud dev local
* hyperfiddle.electric
* hyperfiddle.history (bundled with Electric) – experimental composable router
* datomic/missionary adapter (bundled with Electric) – experimental streaming adapter for Datomic

## Maturity: experimental

* **Websocket issue** - Electric uses a websocket, compatibility with Datomic Cloud Ions is unknown
* **core.async issue** – Electric is fully async, so core.async APIs like Datomic Client API must be bridged. the Electric library includes an experimental Missionary adapter which is used here, but there is future work to do for a high level Electric adapter to Datomic Client API.

## Getting Started

* Install datomic dev-local: https://docs.datomic.com/cloud/dev-local.html
* get mbrainz-subset dataset: https://docs.datomic.com/cloud/dev-local.html#samples
  * extract zip into datomic storage dir, that's it
