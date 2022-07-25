# Installation

## Install datomic dev-local
Go to https://docs.datomic.com/cloud/dev-local.html and follow instructions.

## Install mbrainz dataset
1. Clone this repo: https://github.com/Datomic/mbrainz-importer
2. `cd` into it
3. create a file `manifest.edn` with this content:
```clojure
{:client-cfg {:server-type :dev-local
              :system      "datomic-samples"}
 :db-name "mbrainz-1968-1973"
 :basedir "subsets"
 :concurrency 3}
```
4. In `deps.edn`, set `com.datomic/dev-local` version to `"1.0.243"`
5. Run `clojure -M -m datomic.mbrainz.importer manifest.edn`