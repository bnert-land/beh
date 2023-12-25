# beh

> beh - second letter in the hebrew alphabet, after aleph

## Overview

This micro-library is a companion to [`aleph`](https://github.com/clj-commons/aleph),
providing:

1. [`metosin/jsonista`](https://github.com/metosin/jsonista) support for json encode/decode for the aleph http client.
2. Nested deferred derefencing, which doesn't happend natively w/ aleph
3. Others...?

This library does not assume versions of aleph or jsonista, and leave the provision
of those libraries to you.

See [`deps.edn`](./deps.edn) for specific versions of the various libraries
which have been tested against.

## Installation

deps.edn/Clojure cli
```
land.bnert/beh {:git/url "https://github.com/bnert-land/beh"
                :git/tag "0.1.0"
                :git/sha ""}
```
or:
```
io.github.bnert-land/beh {:git/tag "0.1.0", :git/sha ""}
```
or (still todo, so don't use this option yet):
```
land.bnert/beh {:mvn/version "0.1.0"}
```


## Getting Started

jsonista for aleph http client:
```clojure
(ns exaple.core
  (:require
    [aleph.http :as http]
    [beh.core :as beh]))

(beh/use-jsonista)

(defn -main [_]
  (println
    ; Will use jsonista for encode/decode
    @(http/post "http://localhost:9109"
      {:as :json
       :content-type :json
       :form-params {:ima "teapot"}})))
```

realizing nested deferred values:
```clojure
(ns exaple.core
  (:require
    [aleph.http :as http]
    [beh.core :as beh]))


(beh/use-jsonista)

; starts a server
(defn start-server []
  (http/start-server
    (beh/->json-response
      (beh/realize-deferreds
        (fn [req]
          ; Top level deferred is optional
          (d/success-deferred
            {:status 200,
             :headers {"Content-Type" "application/json"}
             :body
             {:thing (d/success-deferred :thing)
              :list  (d/zip (d/success-deferred 0)
                            (d/success-deferred 1)
                            (d/success-deferred 2))
              :go
              {:one
               {:deeper
                 (d/success-deferred "- dj hanzel")}}}}))))
    {:port 9109}))

(defn -main [_]
  (start-server)
  (println
    @(http/post "http://localhost:9109"
      {:as           :json
       :content-type :json
       :form-params  {:ima "teapot"}})))
```

