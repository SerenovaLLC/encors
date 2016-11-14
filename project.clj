(def ring-version "1.4.0")

(defproject com.unbounce/encors "2.3.1cx"
  :description "encors is a CORS library for ring"
  :url "https://www.github.com/unbounce/encors"
  :license {:name "The MIT License (MIT)"
            :url "http://opensource.org/licenses/MIT"
            :comments "Copyright (c) 2014-2016 Unbounce Marketing Solutions Inc."}

  :profiles {:dev {:plugins [[lein-kibit "0.1.2"]
                             [jonase/eastwood "0.2.3"]]
                   :dependencies [[clj-http "3.2.0"]
                                  [ring/ring-jetty-adapter ~ring-version]
                                  [compojure "1.4.0"]
                                  [manifold "0.1.5"]]}}

  ; set to true to have verbose debug of integration tests
  :jvm-opts ["-Ddebug=false"]

  :repositories [["releases" {:url "http://nexus.cxengagelabs.net/content/repositories/releases/"
                              :snapshots false}]
                 ["snapshots" {:url "http://nexus.cxengagelabs.net/content/repositories/snapshots/"
                               :update :always}]
                 ["thirdparty" {:url "http://nexus.cxengagelabs.net/content/repositories/thirdparty/"
                                :update :always}]]

  :eastwood {:exclude-linters [:constant-test]}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [potemkin "0.4.3"]
                 [org.clojure/core.match "0.2.2"]
                 [prismatic/schema "0.4.4"]
                 [ring/ring-core ~ring-version]])
