(set-env!
 :source-paths #{"src/cljs"}
 :resource-paths #{"resources"}
 
 :dependencies '[[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.854"]
                 [reagent "0.7.0"]
                 [cheshire "5.8.0"]

                 [adzerk/boot-cljs "LATEST" :scope "test"]                          
                 [powerlaces/boot-figreload "LATEST" :scope "test"]
                 [pandeiro/boot-http "0.7.6" :scope "test"]                 
                 [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                 [com.cemerick/piggieback "0.2.1"  :scope "test"]
                 [weasel "0.7.0"  :scope "test"]
                 [org.clojure/tools.nrepl "0.2.12" :scope "test"]])

(require '[adzerk.boot-cljs          :refer [cljs]]
         '[adzerk.boot-cljs-repl     :refer [cljs-repl start-repl]]
         '[powerlaces.boot-figreload :refer [reload]]
         '[pandeiro.boot-http        :refer [serve]])

(deftask dev []
  (comp (serve :dir "out")
        (watch)
        (reload)
        (cljs-repl)
        (cljs :source-map true
              :optimizations :none)
        (target :dir #{"out"})))

(deftask lightbuild []
  (comp
   (cljs
    :optimizations :none)
   (target :dir #{"out"})))

(deftask build []
  (cljs
   :optimizations :advanced)
  (target :dir #{"out"}))
