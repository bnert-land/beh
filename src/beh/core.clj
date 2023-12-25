(ns beh.core
  (:require
    [aleph.http.client-middleware :as mw]
    [clojure.walk :as walk]
    [jsonista.core :as json]
    [manifold.deferred :as d]))

(set! *warn-on-reflection* true)

(defn- json-decode [x keys-as-keyword?]
  (json/read-value x (if keys-as-keyword?
                       json/keyword-keys-object-mapper
                       json/default-object-mapper)))

(defn- json-encode [x _opts]
  (json/write-value-as-bytes x))


(defn- walk+defer [res]
  (update res :body
    (fn [body]
      (walk/prewalk
        (fn [x]
          (if (d/deferred? x)
            (deref x)
            x))
        body))))

; --

; Don't want this library to have side effectful imports
(defn use-jsonista
  "At the root/core of your project, call this fn to patch the aleph
  client to use jsonista instead of cheshire for json encode/decode."
  []
  (alter-var-root #'mw/json-enabled? (constantly true))
  (alter-var-root #'mw/json-decode (constantly json-decode))
  (alter-var-root #'mw/json-decode-strict (constantly json-decode))
  ; json-encode is lazy for cheshire, and as far as I know, jsonista isn't
  ; lazy. Therefore, we're re-using the same json-decode.
  (alter-var-root #'mw/json-encode (constantly json-encode))
  nil)

(defn realize-deferreds
  "Ring middleware for realizing top level or nested deferred values.

  An optional catch function may be supplied in order to handle
  any error deferred values or exceptions which are thrown."
  ([handler]
   (realize-deferreds handler
     (fn [^Exception e]
       {:status 500
        :body   {:errors [{:meta   {:cause (.getMessage e)}
                           :status 500
                           :title  "Server Error"}]}})))
  ([handler on-catch]
   (fn realize-deferreds* [req]
     (d/catch
       (d/chain
         (handler req)
         walk+defer)
       on-catch))))

; mainly to testing, but could be useful? Dunno, yet...
(defn ->json-response
  "Assumes the :body of a response is a clojure data structure which
  needs to be encoded to JSON."
  [handler]
  (fn ->json-response* [req]
    (d/catch
      (d/chain
        (handler req)
        #(update % :body json/write-value-as-bytes))
      (fn [^Exception e]
        {:status 500,
         :body   {:errors [{:meta   {:cause (.getMessage e)
                                     :fn    "->json-response"}
                            :status 500
                            :title  "Server error"}]}}))))

