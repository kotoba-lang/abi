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

(defn exact-import-grant-provider-sets?
  "True only when declared imports, grants, and provider binding keys agree.
  This is the invariant that all runtime adapters must preserve before linking."
  [imports grants providers]
  (and (set? imports)
       (set? grants)
       (map? providers)
       (= imports grants (set (keys providers)))))
