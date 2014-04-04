(defproject liberator-friend "0.1.0-SNAPSHOT"
  :description "Example of Friend and Liberator integration."
  :url "http://github.com/sritchie/liberator-friend"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main liberator-friend.core
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.cemerick/friend "0.2.0"]
                 [liberator "0.11.0"]
                 [compojure "1.1.5"]
                 [ring "1.2.2"]
                 [hiccup "1.0.1"]]
  :plugins [[lein-ring "0.8.10"]]
  :ring {:handler liberator-friend.core/site})
