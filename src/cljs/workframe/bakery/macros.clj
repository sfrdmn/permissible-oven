(ns workframe.bakery.macros
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

(defmacro read-json
  "Read in a JSON file as a map at compile time"
  [path]
  (json/parse-string (slurp (io/resource path)) true))
