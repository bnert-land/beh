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

(defn default-on-catch [^Exception e]
  {:status 500
   :body   {:errors [{:meta   {:cause (.getMessage e)}
                      :status 500
                      :title  "Server Error"}]}})

(defmulti realize-deferreds :kind)

(defmethod realize-deferreds :ring/middleware
  [{:keys [on-catch], :or {on-catch default-on-catch}}]
  (fn realize-deferreds-middleware [handler]
    (fn realize-deferreds-middleware* [req]
      (d/catch
        (d/chain
          (handler req)
          walk+defer)
        on-catch))))

(defmethod realize-deferreds :sieppari/interceptor
  [{:keys [on-catch], :or {on-catch default-on-catch}}]
  {:leave
   (fn [ctx]
     ; probably could be better from a perf standpoint?
     (d/catch
       (d/chain
         ctx
         #(update % :response walk+defer))
       on-catch))})

(defmethod realize-deferreds :default
  [opts]
  (realize-deferreds (assoc opts :kind :ring/middleware)))

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

