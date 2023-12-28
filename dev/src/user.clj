(ns user)

(require
  '[aleph.http :as http]
  '[aleph.http.client-middleware :as mw]
  '[manifold.deferred :as d]
  '[beh.core :as beh])


(comment
  ; optional require, if using :json/cheshire
  (require '[cheshire.core :as c])

  ; example usage to enable json and jsonista encode/decode
  (beh/use-jsonista)

  ; will be 'true' if cheshire on classpath, otherwise 'false'
  ; if (beh/use-jsonista) is called, then this will be set to 'true'
  (identity mw/json-enabled?)


  ; test server
  (def s
    (http/start-server
      (beh/->json-response
        ((beh/realize-deferreds {:kind :ring/middleware})
          (fn [_req]
            {:status 200,
             :body   {:thing (d/success-deferred :thing)
                      :error (d/error-deffered (Exception. "hi"))
                      :list  (d/zip (d/success-deferred 0)
                                    (d/success-deferred 1)
                                    (d/success-deferred 2))
                      :go {:one {:deeper (d/success-deferred "- dj hanzel")}}}})))
      {:port 9109}))
  (.close s)

  (time
    (deref (http/get "http://localhost:9109" {:as :json})))

  (time
    (deref (http/post "http://localhost:9109"
                      {:as           :json
                       :content-type :json
                       :accept       :json
                       :form-params  {:ima "teapot"}
                       #_#_:body         "{\"ima\": \"teapot\"}"})))

  (identity
    ; Will use jsonista for encode/decode
    @(http/post "https://httpbin.org/anything"
      {:as :json
       :content-type :json
       :form-params {:ima "teapot"}}))
)
