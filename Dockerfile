# FROM clojure:openjdk-11-tools-deps AS clojure-deps
# WORKDIR /app
# COPY deps.edn deps.edn
# COPY src-build src-build
# COPY vendor vendor
# RUN clojure -A:dev -M -e :ok        # preload deps
# RUN clojure -T:build noop           # preload build deps

FROM clojure:openjdk-11-tools-deps AS build
WORKDIR /app
#COPY --from=clojure-deps /root/.m2 /root/.m2
COPY shadow-cljs.edn shadow-cljs.edn
COPY deps.edn deps.edn
COPY src src
COPY src-build src-build
#COPY src-dev src-dev
COPY src-prod src-prod
COPY vendor vendor
COPY resources resources
ARG REBUILD=unknown
ARG VERSION
RUN clojure -X:build uberjar :jar-name "app.jar" :verbose true :version '"'$VERSION'"'

FROM clojure:openjdk-11-tools-deps AS datomic-fixtures
WORKDIR /app
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends unzip curl wget
#COPY state/datomic-pro-1.0.6735.zip state/datomic-pro-1.0.6735.zip
#COPY state/mbrainz.tar state/mbrainz.tar
COPY datomic_fixtures.sh datomic_fixtures.sh
RUN ./datomic_fixtures.sh

FROM amazoncorretto:11 AS app
WORKDIR /app
#RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends netcat
COPY --from=build /app/target/app.jar app.jar
COPY --from=datomic-fixtures /app/state /app/state
COPY prod.sh prod.sh
EXPOSE 8080
ARG VERSION
ENV VERSION=$VERSION
CMD ./prod.sh $VERSION