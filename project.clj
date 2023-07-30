(defproject com.github.s-doti/ginfer "1.0.1"
  :description "Graph inference library"
  :url "https://github.com/s-doti/ginfer"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.673"]
                 [com.taoensso/timbre "6.2.1"]
                 [ring/ring-core "1.10.0"]
                 [http-kit "2.6.0"]
                 [com.github.s-doti/seamless-async "1.0.2"]
                 [com.github.s-doti/persistroids "1.0.1"]
                 [com.github.s-doti/sepl "1.0.1"]]
  :profiles {:dev {:dependencies [[midje "1.10.9"]]}}
  :repl-options {:init-ns ginfer.core})