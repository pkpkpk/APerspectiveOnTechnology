(ns build
  (:require [cljs.build.api :as api]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def build-config
  {:id "cube"
   :src-path "src/main"
   :compiler {:main 'cube.core
              :parallel-build true
              :aot-cache true
              :asset-path "js/compiled/out"
              :output-to  "resources/public/js/compiled/cube.js"
              :output-dir "resources/public/js/compiled/out"}})

(defn delete-file-recursively
  "Delete file f. If it's a directory, recursively delete all its contents.
  Raise an exception if any deletion fails unless silently is true."
  [f & [silently]]
  (let [f (io/file f)]
    (when (.isDirectory f)
      (doseq [child (.listFiles f)]
        (delete-file-recursively child silently)))
    (.setWritable f true)
    (io/delete-file f silently)))



(defmulti task first)

(defmethod task "clean" [_]
  (let [main (get-in build-config [:compiler :output-to])
        out (get-in build-config [:compiler :output-dir])]
    (io/delete-file main)
    (delete-file-recursively out)))

(defmethod task "once" [[_ _]]
  (let [config (:compiler build-config)]
    (println "building " (:id build-config) "to" (:output-to config))
    (api/build (:src-path build-config) config)))

(defmethod task "auto" [[_ _]]
  (let [config (:compiler build-config)]
    (println "building " (:id build-config) "to" (:output-to config))
    (api/watch (:src-path build-config) config)))

(defmethod task :default
  [args]
  (let [all-tasks (-> task methods (dissoc :default) keys sort (->> (interpose ", ") (apply str)))]
    (println "unknown or missing task argument. Choose one of:" all-tasks)
    (System/exit 1)))

(task *command-line-args*)