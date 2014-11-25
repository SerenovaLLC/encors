(ns com.unbounce.encors.integration-test
  (:require [com.unbounce.encors.core :as cors]
            [com.unbounce.encors.types :refer [map->CorsPolicy]]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clj-http.client :as http]
            [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer :all]
            [compojure.route :as route]))

(def ^:dynamic sut (atom nil))

(def http-port
  (let [configured-port
        (or (System/getProperty "test.http.port")
            (System/getenv "TEST_HTTP_PORT"))]
    (if configured-port
      (read-string configured-port)
      (+ 50000 (rand-int 15000)))))

(defn- uri
  ([]
    (uri ""))
  ([path]
    (format "http://localhost:%d%s" http-port path)))

(defn- handler [request]
  (@sut request))

(defroutes app
  (GET "/get" [] "got")
  (POST "/post" [] {:status 201 :body "posted"})
  (route/not-found "not found"))

;; Support

(def ^:const allowed-origin  "test.encors.net")
(def ^:const allowed-methods "GET, POST")
(def ^:const allowed-headers "Content-Type")
(def ^:const expose-headers  "X-Safe-To-Expose, X-Safe-To-Expose-Too")
(def ^:const unallowed-origin "not.cool.io")

(def partial-cors-options
  {:allowed-origins #{allowed-origin}
   :allowed-methods (set (mapv (comp keyword str/lower-case)
                               (str/split allowed-methods #", ")))
   :exposed-headers #{}
   :request-headers #{allowed-headers}
   :max-age nil
   :allow-credentials? false
   :origin-varies? true
   :require-origin? true
   :ignore-failures? false})

(def partial-cors-policy
  (map->CorsPolicy partial-cors-options))

(def full-cors-options
  (merge partial-cors-options
         {:exposed-headers (set (str/split allowed-methods #", "))
          :max-age 1234
          :allow-credentials? true}))

(def full-cors-policy
  (map->CorsPolicy full-cors-options))

(defn- assert-no-cors-response [res]
  (are [header] (nil? (-> res :headers (get header)))
       "Access-Control-Allow-Origin"
       "Access-Control-Allow-Methods"
       "Access-Control-Allow-Headers"
       "Access-Control-Allow-Credentials"))

(defn- assert-partial-cors-preflight-response [res]
  (are [header expected] (= (-> res :headers (get header)) expected)
        "Access-Control-Allow-Origin"      allowed-origin
        "Access-Control-Allow-Methods"     allowed-methods
        "Access-Control-Allow-Headers"     allowed-headers
        "Access-Control-Allow-Credentials" "false"))

(defn- assert-full-cors-preflight-response [res]
  (are [header expected] (= (-> res :headers (get header)) expected)
        "Access-Control-Allow-Origin" allowed-origin
        "Access-Control-Allow-Methods" allowed-methods
        "Access-Control-Allow-Headers" allowed-headers
        "Access-Control-Allow-Credentials" "true"
        "Access-Control-Expose-Headers" expose-headers
        "Access-Control-Max-Age" "1234"))

(defn- assert-partial-cors-response [res]
  (are [header expected] (= (-> res :headers (get header)) expected)
        "Access-Control-Allow-Origin" allowed-origin
        "Access-Control-Allow-Methods" allowed-methods
        "Access-Control-Allow-Headers" allowed-headers
        "Access-Control-Allow-Credentials" "false"))

(defn- assert-full-cors-response [res]
  (are [header expected] (= (-> res :headers (get header)) expected)
        "Access-Control-Allow-Origin" allowed-origin
        "Access-Control-Allow-Methods" allowed-methods
        "Access-Control-Allow-Headers" allowed-headers
        "Access-Control-Allow-Credentials" "true"
        "Access-Control-Expose-Headers" expose-headers))

(defn- assert-response [res expected-status cors-assertions-fn]
  (is (= (:status res) expected-status))
  (is (cors-assertions-fn res)))

;; App features tests

(defn- test-app-features [cors-assertions-fn]
  (testing "Application features"
    (let [debug-res (fn [req-uri res]
                      (println "====> DEBUG for " req-uri ": \n"
                               "\n"
                               "- " (:status res) "\n"
                               "- " (pr-str (:headers res)) "\n"
                               "- " (:body res)))
          get-res (http/get (uri "/get")
                            {:throw-exceptions false})
          post-res (http/post (uri "/post")
                              {:headers {:Content-Type "application/json"}
                               :body "{\"data\":\"fake\"}"
                               :throw-exceptions false})
          root-res (http/get (uri "/")
                             {:throw-exceptions false})
          ]


      (debug-res "get-res" get-res)
      (assert-response get-res 200 cors-assertions-fn)

      (debug-res "post-res" get-res)
      (assert-response post-res 201 cors-assertions-fn)

      (debug-res "root-res" get-res)
      (assert-response root-res 404 cors-assertions-fn))))

(deftest app-features-no-cors
  (test-app-features assert-no-cors-response))

(deftest app-features-partial-cors
  (test-app-features assert-partial-cors-response))

(deftest app-features-full-cors
  (test-app-features assert-full-cors-response))

;; CORS tests

(defn- send-preflight-request [origin]
  (http/options (uri "/")
                {:headers {:Access-Control-Request-Method "POST"
                           :Origin origin}
                 :throw-exceptions false}))

(deftest valid-preflight-no-cors
  (testing "Valid preflight"
    (let [res (send-preflight-request allowed-origin)]
             (assert-response res 404 assert-no-cors-response))))

(deftest invalid-preflight-no-cors
  (testing "Invalid preflight"
    (let [res (send-preflight-request unallowed-origin)]
             (assert-response res 404 assert-no-cors-response))))

(deftest valid-preflight-partial-cors
  (testing "Valid preflight"
    (let [res (send-preflight-request allowed-origin)]
             (assert-response res 200 assert-partial-cors-preflight-response))))

(deftest invalid-preflight-partial-cors
  (testing "Invalid preflight"
    (let [res (send-preflight-request unallowed-origin)]
             (assert-response res 404 assert-no-cors-response))))

(deftest valid-preflight-full-cors
  (testing "Valid preflight"
    (let [res (send-preflight-request allowed-origin)]
             (assert-response res 200 assert-full-cors-preflight-response))))

(deftest invalid-preflight-full-cors
  (testing "Invalid preflight"
    (let [res (send-preflight-request unallowed-origin)]
             (assert-response res 400 assert-no-cors-response))))

;; Jetty, tests and SUT orchestration

(defn test-ns-hook []
  (let [jetty (run-jetty handler {:join? false :port http-port})]

    ;; Test the raw app, without CORS middleware
    (testing "No CORS policy"
      (reset! sut app)
      (app-features-no-cors)
      (valid-preflight-no-cors)
      (invalid-preflight-no-cors))

    ;; Test the app, with CORS middleware (partial config)
    (testing "Partial CORS policy"
      (reset! sut
              (cors/wrap-cors (constantly partial-cors-policy) app))
      (app-features-partial-cors)
      (valid-preflight-partial-cors)
      (invalid-preflight-partial-cors))

    ;; Test the app, with CORS middleware (full config)
    (testing "Full CORS policy"
      (reset! sut
              (cors/wrap-cors (constantly full-cors-policy) app))
      (app-features-full-cors)
      (valid-preflight-full-cors)
      (invalid-preflight-full-cors))

    (.stop jetty)))
