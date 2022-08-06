# Installation

## Install datomic dev-local
Go to https://docs.datomic.com/cloud/dev-local.html and follow instructions.

## Install mbrainz dataset
* mbrainz-subset: https://docs.datomic.com/cloud/dev-local.html#samples - extract zip into datomic storage dir, that's it
* mbrainz-1968-1973: https://github.com/Datomic/mbrainz-importer - don't use this one, too big

mbrainz-1968-1973 instructions (don't do this):
3. Clone this repo: 
4. `cd` into it
5. create a file `manifest.edn` with this content:
```clojure
{:client-cfg {:server-type :dev-local
              :system      "datomic-samples"}
 :db-name "mbrainz-1968-1973"
 :basedir "subsets"
 :concurrency 3}
```
4. In `deps.edn`, set `com.datomic/dev-local` version to `"1.0.243"`
5. Run `clojure -M -m datomic.mbrainz.importer manifest.edn`