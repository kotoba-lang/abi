(ns kotoba.abi.contract
  "Small, dependency-free constants shared by generated ABI consumers.")

(def component-world "kotoba:app/kotoba-app@0.1.0")
(def component-target :wasm-component-kotoba-v1)
(def wasi-version "0.3")
(def ambient-wasi? false)

(def capability-imports
  #{:aiueos-identity-sign
    :aiueos-identity-verify
    :aiueos-hash-sha256
    :aiueos-http-post
    :aiueos-log-read
    :aiueos-log-append
    :aiueos-clock-now})

(defn exact-import-grant-provider-sets?
  "True only when declared imports, grants, and provider binding keys agree.
  This is the invariant that all runtime adapters must preserve before linking."
  [imports grants providers]
  (and (set? imports)
       (set? grants)
       (map? providers)
       (= imports grants (set (keys providers)))))
