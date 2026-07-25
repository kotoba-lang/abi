(ns kotoba.abi.contract-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.abi.contract :as contract]))

(deftest component-contract-is-explicit
  (is (= "kotoba:app/kotoba-app@0.1.0" contract/component-world))
  (is (= :wasm-component-kotoba-v1 contract/component-target))
  (is (= "0.3.0" contract/wasi-version))
  (is (contract/profile? :sync))
  (is (contract/cancellation-required? :async))
  (is (= [:fuel :memory-pages]
         (contract/required-budget-keys :sync)))
  (is (= "aiueos-clock-now" (get contract/capability-import-names 7)))
  (is (false? contract/ambient-wasi?)))

(deftest import-grant-provider-invariant-is-exact
  (let [imports #{:aiueos-clock-now}]
    (is (contract/exact-import-grant-provider-sets? imports imports
                                                    {:aiueos-clock-now :provider}))
    (is (not (contract/exact-import-grant-provider-sets? imports #{}
                                                         {:aiueos-clock-now :provider})))
    (is (not (contract/exact-import-grant-provider-sets? imports imports {})))))

(deftest effectful-world-has-only-named-imports
  (let [wit (contract/world-wit #{7})]
    (is (.contains wit "import aiueos-clock-now"))
    (is (not (.contains wit "wasi:")))
    (is (= :aiueos.component/aiueos-clock-now
           (contract/component-import-key 7)))))

(deftest v2-world-never-labels-an-effect-as-a-v1-scalar-import
  (is (.contains (contract/world-wit-v2 #{}) "package kotoba:app@0.2.0"))
  #?(:clj
     (let [wit (contract/world-wit-v2 #{7})]
       (is (.contains wit "package aiueos:capability@0.3.0"))
       (is (not (.contains wit "import aiueos-clock-now: func(value: s64)"))))
     :cljs
     (is (thrown? js/Error (contract/world-wit-v2 #{7})))))

#?(:clj
   (deftest authoritative-v3-wit-is-published-to-compiler-consumers
     (let [wit (contract/typed-capability-wit-v3)]
       (is (= "aiueos:capability/application@0.3.0"
              contract/typed-capability-world-v3))
       (is (.contains wit "package aiueos:capability@0.3.0"))
       (is (.contains wit "acquire: func(request: grant-request)"))
       (is (not (.contains wit "wasi:"))))))

(deftest abilities-are-exact-and-bounded
  (let [ability {:target "clock://monotonic" :operation :clock/now
                 :max-bytes 1 :max-items 1 :deadline-ms 1 :audit-id "test"}]
    (is (contract/valid-ability? ability))
    (is (not (contract/valid-ability? (assoc ability :extra true))))
    (is (not (contract/valid-ability? (assoc ability :deadline-ms 0))))))

(def cid "bafyportablehostcontract")

(deftest portable-host-descriptors-are-closed-and-bound
  (let [plan {:format :kotoba.plan/v1 :plan-cid cid :code-closure-cid cid
              :artifact-cid cid :compiler-contract cid :requested-effects #{:audit/append}
              :requested-resources #{:receipt-log} :input-cid cid :budget {:fuel 1}}
        decision {:format :kotoba.policy-decision/v1 :decision-cid cid :plan-cid cid
                  :policy-cid cid :db-basis cid :result :permit :reasons [:within-budget]
                  :issued-at "2026-07-25T00:00:00Z" :expires-at "2026-07-25T00:01:00Z"}
        lease {:format :kotoba.capability-lease/v1 :capability-cid cid
               :execution-identity-cid cid :component-cid cid :resource-cid cid
               :purpose :audit/append :expires-at "2026-07-25T00:01:00Z"
               :uses 1 :transfer :non-transferable :delegation-depth 0}
        identity {:format :kotoba.execution-identity/v1 :plan-cid cid
                  :code-closure-cid cid :artifact-cid cid :compiler-contract cid
                  :component-cid cid :wit-world-cid cid :package-lock-cid cid
                  :policy-cid cid :policy-decision-cid cid :db-basis cid
                  :grant-cids [cid] :approval-cids [] :runtime-identity cid
                  :input-cid cid :outcome-cid cid :host-receipt-cids [cid]}]
    (is (contract/valid-plan? plan))
    (is (contract/valid-policy-decision? decision))
    (is (contract/valid-capability-lease? lease))
    (is (contract/valid-execution-identity? identity))
    (is (not (contract/valid-capability-lease? (assoc lease :host-handle "42"))))
    (is (not (contract/valid-execution-identity? (assoc identity :wit-world-cid nil))))
    (is (not (contract/valid-policy-decision? (assoc decision :result :maybe))))))

(deftest portable-host-conformance-vectors-have-one-portable-outcome
  (doseq [{:keys [id expect] :as vector} contract/portable-execution-v1-vectors]
    (is (= (= :accept expect) (contract/conformance-result vector)) (name id))))
