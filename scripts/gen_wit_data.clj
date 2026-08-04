;; Regenerates src/kotoba/abi/wit_data.cljc from the authoritative WIT files.
;;
;; The typed capability world had to be readable on every platform, not just
;; the JVM. Reading it from the filesystem is not an option: this repository is
;; consumed as a git dependency, so a consumer runs from its own root and any
;; relative path to `wit/` is wrong. Embedding the bytes in a .cljc gives both
;; platforms the same string with no IO at all, which is also the only way the
;; two can be guaranteed byte-identical.
;;
;; The embedded copy is checked against the file by
;; kotoba.abi.wit-data-test, so it cannot drift silently.
;;
;;   clojure -M -e "(load-file \"scripts/gen_wit_data.clj\")"

(require '[clojure.java.io :as io]
         '[clojure.string :as str])

(def sources
  [{:var 'aiueos-capability-v2-wit
    :path "aiueos-capability-v2/aiueos-capability.wit"
    :note "The pinned aiueos:capability@0.3.0 world served to effectful
    consumers. The directory says v2 and the package says 0.3.0; that is the
    existing naming, not a mismatch."}])

(defn- read-wit [path]
  (let [resource (io/resource path)]
    (when-not resource
      (throw (ex-info "authoritative WIT is not on the classpath" {:path path})))
    (slurp resource)))

(def entries
  (mapv (fn [{:keys [var path note]}]
          (assoc (into {} [[:var var] [:path path] [:note note]])
                 :text (read-wit path)))
        sources))

(spit "src/kotoba/abi/wit_data.cljc"
      (str "(ns kotoba.abi.wit-data\n"
           "  \"GENERATED — do not edit. Run scripts/gen_wit_data.clj.\n\n"
           "  The authoritative WIT bytes, embedded so every platform reads the same\n"
           "  string. This repository is a git dependency, so a consumer runs from its\n"
           "  own root and cannot resolve a relative path into this repo's wit/ tree;\n"
           "  embedding is what makes the JVM and ClojureScript copies byte-identical\n"
           "  rather than merely intended to be.\n\n"
           "  kotoba.abi.wit-data-test asserts each string still equals its file.\")\n\n"
           (str/join "\n\n"
                     (map (fn [{:keys [var path note text]}]
                            (str ";; " path "\n"
                                 ";; " (str/replace (str/trim note) #"\s+" " ") "\n"
                                 "(def " var "\n  " (pr-str text) ")"))
                          entries))
           "\n\n(def files\n  \"var name -> classpath path, for the drift test.\"\n  '"
           (pr-str (into {} (map (juxt :var :path)) entries))
           ")\n"))

(println "wrote src/kotoba/abi/wit_data.cljc with" (count entries) "embedded WIT file(s)")
(doseq [{:keys [var text]} entries]
  (println " " var (count text) "chars"))
