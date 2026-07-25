(ns kotoba.abi.contract
  "Small, dependency-free constants shared by generated ABI consumers.")

(def component-world "kotoba:app/kotoba-app@0.1.0")
(def component-target :wasm-component-kotoba-v1)
(def wasi-version "0.3")
(def ambient-wasi? false)

(def capability-import-names
  {1 "aiueos-identity-sign"
   2 "aiueos-identity-verify"
   3 "aiueos-hash-sha256"
   4 "aiueos-http-post"
   5 "aiueos-log-read"
   6 "aiueos-log-append"
   7 "aiueos-clock-now"})

(def capability-imports
  (->> capability-import-names vals (map keyword) set))

(def ability-keys
  #{:target :operation :max-bytes :max-items :deadline-ms :audit-id})

(defn valid-ability?
  "Validate the exact, bounded descriptor attached to one named Component
  import. This is data validation only; a runtime must still enforce the
  resulting limits at every host boundary."
  [ability]
  (and (map? ability)
       (= ability-keys (set (keys ability)))
       (string? (:target ability)) (seq (:target ability))
       (keyword? (:operation ability))
       (string? (:audit-id ability)) (seq (:audit-id ability))
       (every? #(pos-int? (get ability %))
               [:max-bytes :max-items :deadline-ms])))

(defn capability-import-name
  "Resolve a compiler-local capability id to its portable WIT import name.
  Unknown ids are a compile-time error; an adapter must never turn one into a
  generic or ambient host call."
  [id]
  (or (get capability-import-names id)
      (throw (ex-info "Component capability has no named ABI import"
                      {:phase :component-abi :capability-id id}))))

(defn component-import-key [id]
  (keyword "aiueos.component" (capability-import-name id)))

(defn world-wit
  "Render the exact compiler Component world for a closed set of capability
  ids. Each effect is a separately named WIT import; no call can acquire
  ambient WASI authority by sharing an umbrella interface."
  [capability-ids]
  (str "package kotoba:app@0.1.0;\n\nworld kotoba-app {\n"
       (apply str (map #(str "  import " (capability-import-name %) ": func(value: s64) -> s64;\n")
                       (sort capability-ids)))
       "  export main: func() -> s64;\n}\n"))

(defn exact-import-grant-provider-sets?
  "True only when declared imports, grants, and provider binding keys agree.
  This is the invariant that all runtime adapters must preserve before linking."
  [imports grants providers]
  (and (set? imports)
       (set? grants)
       (map? providers)
       (= imports grants (set (keys providers)))))
