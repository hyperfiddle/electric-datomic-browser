# Datomic browser — Electric Clojure example

Today, Cognitect's REBL and other GUI data browsers are architected as desktop applications, because their expressive power comes from the **datafy/nav programming model**, where the GUI view process is co-located with the backend data sources, so that the view can directly query and traverse backend data structures as if at the REPL (data-navigate = fn composition). 

The consequence of this co-located architecture is that these data browsers are historically not architected as web applications, because the **frontend/backend web app plumbing breaks composition**, which in turn breaks the data-nav=composition programming model.

Electric Clojure solves this by using **stream functions** to abstract over the frontend/backend web boundary, permitting a data-nav=composition programming model to work with high performance despite the client/server architecture.

https://user-images.githubusercontent.com/124158/219978031-939344eb-4489-4b97-af9f-4b2df38c70db.mp4

*Video: this web-based data browser achieves high performance while still being coded in the data-nav=composition programming model.*

In essence, Electric's property of **"network-transparent composition"** is designed to make possible a datafy/nav implementation that spans the client/server network chasm, which can be used from a dynamic web client. Therefore, we think bringing this browser demo to parity with, say, Cognitect REBL (but for the web) is a straightforward exercise. 

This is the technical thesis of the Hyperfiddle project. Now armed with network-transparent composition, how much further can our programming abstractions scale, before reaching performance limits that introduce incidental complexity?

# Dependencies

* Datomic cloud dev local
* Datomic client API
* [Electric Clojure](https://github.com/hyperfiddle/electric)
* hyperfiddle.history (bundled with Electric) – experimental composable router
* datomic/missionary adapter (bundled with Electric) – experimental streaming adapter for Datomic

# Maturity: POC

* **Websocket issue** - Electric uses a websocket, compatibility with Datomic Cloud Ions is unknown
* **core.async issue** – Electric is a FRP abstraction, so other async paradigms like core.async must be bridged. the Electric library includes an experimental Datomic<>Missionary adapter which is used here, but it's just a scrappy POC. What we really need is a high level Electric Clojure adapter for Datomic. 

# Getting Started

* Install datomic dev-local: https://docs.datomic.com/cloud/dev-local.html
* get mbrainz-subset dataset: https://docs.datomic.com/cloud/dev-local.html#samples
  * extract zip into datomic storage dir, that's it

```
$ clj -A:dev -X user/main

Starting Electric compiler and server...
shadow-cljs - server version: 2.22.10 running at http://localhost:9630
shadow-cljs - nREPL server started on port 9001
[:dev] Configuring build.
[:dev] Compiling ...
Datomic APIs detected:  #{datomic.client.api.async datomic.client.api}
[:dev] Build completed. (225 files, 1 compiled, 0 warnings, 4.69s)

👉 App server available at http://0.0.0.0:8080
```