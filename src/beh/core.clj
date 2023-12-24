(ns beh.core
  (:require
    [aleph.http.client-middleware :as mw]
    [clojure.walk :as walk]
    [jsonista.core :as json]
    [manifold.deferred :as d]))

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
  client to use jsonista and enable json encode/decode"
  []
  (alter-var-root #'mw/json-enabled? (constantly true))
  (alter-var-root #'mw/json-decode (constantly json-decode))
  (alter-var-root #'mw/json-decode-strict (constantly json-decode))
  ; json-encode is lazy for cheshire, and as far as I know, jsonista isn't
  ; lazy. Therefore, we're re-using the same json-decode.
  (alter-var-root #'mw/json-encode (constantly json-encode))
  nil)

(defn realize-deferreds
  ([handler]
   (realize-deferreds handler
     (fn [e]
       {:status 500
        :body   {:errors [{:meta   {:cause (.getMessage e)}
                           :status 500
                           :title  "Server Error"}]}})))
  ([handler on-catch]
   (fn realize-deferreds* [req]
     (let [res (handler req)
           res (if (d/deferred? res)
                 res
                 (d/success-deferred res))]
       (d/catch
         (d/chain' res walk+defer)
         on-catch)))))

; mainly to testing, but could be useful? Dunno, yet...
(defn ->json-response [handler]
  (fn ->json-response* [req]
    (update (handler req) :body json/write-value-as-bytes)))

