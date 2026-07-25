(ns kotoba.abi.contract-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.abi.contract :as contract]))

(deftest component-contract-is-explicit
  (is (= "kotoba:app/kotoba-app@0.1.0" contract/component-world))
  (is (= :wasm-component-kotoba-v1 contract/component-target))
  (is (= "aiueos-clock-now" (get contract/capability-import-names 7)))
  (is (false? contract/ambient-wasi?)))

(deftest import-grant-provider-invariant-is-exact
  (let [imports #{:aiueos-clock-now}]
    (is (contract/exact-import-grant-provider-sets? imports imports
                                                    {:aiueos-clock-now :provider}))
    (is (not (contract/exact-import-grant-provider-sets? imports #{}
                                                         {:aiueos-clock-now :provider})))
    (is (not (contract/exact-import-grant-provider-sets? imports imports {})))))
