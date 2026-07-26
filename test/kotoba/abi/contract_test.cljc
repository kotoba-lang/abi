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
        approval {:format :kotoba.approval/v1 :approval-cid cid :plan-cid cid
                  :policy-cid cid :db-basis cid :resources #{:receipt-log}
                  :input-cid cid :approver-cid cid
                  :issued-at "2026-07-25T00:00:00Z"
                  :expires-at "2026-07-25T00:01:00Z"}
        identity {:format :kotoba.execution-identity/v1 :plan-cid cid
                  :code-closure-cid cid :artifact-cid cid :compiler-contract cid
                  :component-cid cid :wit-world-cid cid :package-lock-cid cid
                  :policy-cid cid :policy-decision-cid cid :db-basis cid
                  :grant-cids [cid] :approval-cids [] :runtime-identity cid
                  :input-cid cid :outcome-cid cid :host-receipt-cids [cid]}]
    (is (contract/valid-plan? plan))
    (is (contract/valid-policy-decision? decision))
    (is (contract/valid-capability-lease? lease))
    (is (contract/valid-approval? approval))
    (is (contract/valid-execution-identity? identity))
    (is (not (contract/valid-capability-lease? (assoc lease :host-handle "42"))))
    (is (not (contract/valid-approval? (assoc approval :runtime-prompt true))))
    (is (not (contract/valid-execution-identity? (assoc identity :wit-world-cid nil))))
    (is (not (contract/valid-policy-decision? (assoc decision :result :maybe))))))

(deftest portable-host-conformance-vectors-have-one-portable-outcome
  (doseq [{:keys [id expect] :as vector} contract/portable-execution-v1-vectors]
    (is (= (= :accept expect) (contract/conformance-result vector)) (name id))))

(deftest component-authority-events-have-one-exact-wire-shape
  (let [event {:murakumo.component/version 1
               :murakumo.component/event :revoked
               :murakumo.component/component-cid cid
               :murakumo.component/epoch 2
               :murakumo.component/sequence 3
               :murakumo.component/node nil}]
    (is (contract/valid-component-authority-event? event))
    (is (not (contract/valid-component-authority-event?
              (assoc event :murakumo.component/epoch 0))))
    (is (not (contract/valid-component-authority-event?
              (assoc event :ambient-authority true))))
    (is (not (contract/valid-component-authority-event?
              (assoc event :murakumo.component/event :placed))))))

(deftest component-authority-signatures-cover-issuer-audience-and-event
  (let [event {:murakumo.component/version 1
               :murakumo.component/event :revoked
               :murakumo.component/component-cid cid
               :murakumo.component/epoch 2
               :murakumo.component/sequence 3
               :murakumo.component/node nil}
        envelope {:format :murakumo.component-authority/v1
                  :algorithm :ed25519
                  :key-id "murakumo-2026-01"
                  :issuer "did:key:murakumo"
                  :audience "did:key:kototama-edge-a"
                  :issued-at-ms 1785000000000
                  :event event
                  :signature (apply str (repeat 128 "a"))}]
    (is (contract/valid-component-authority-envelope? envelope))
    (is (= (contract/component-authority-signing-payload envelope)
           (contract/component-authority-signing-payload
            (into (sorted-map) envelope))))
    (doseq [field [:issuer :audience :issued-at-ms :event]]
      (is (not=
           (contract/component-authority-signing-payload envelope)
           (contract/component-authority-signing-payload
            (update envelope field
                    (case field
                      :issued-at-ms inc
                      :event #(update % :murakumo.component/epoch inc)
                      #(str % "-tampered")))))))
    (is (not (contract/valid-component-authority-envelope?
              (assoc envelope :signature "00"))))
    (is (not (contract/valid-component-authority-envelope?
              (assoc envelope :public-key "self-asserted"))))))

;; The Application Profile capabilities (ADR-2607201300) need portable import
;; names for the compiler to be able to EMIT a component that imports them --
;; without a name `capability-import-name` throws and an application whose only
;; effects are state/ui/llm/storage cannot be compiled at all. A name is not a
;; runtime claim: these ids are deliberately absent from the v0.3
;; `grant-request` enum, because no host implements them yet.
(deftest application-profile-capabilities-have-portable-import-names
  (is (= {8 "aiueos-state-transact"
          9 "aiueos-ui-commit"
          10 "aiueos-ui-next-event"
          11 "aiueos-llm-generate"
          12 "aiueos-storage-transact"}
         (select-keys contract/capability-import-names [8 9 10 11 12])))
  (is (= :aiueos.component/aiueos-llm-generate (contract/component-import-key 11)))
  ;; Streams and linear resources stay unnamed on purpose: a name would promise
  ;; a lowering the Canonical ABI path does not have.
  (doseq [id [13 14 15 16]]
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (contract/capability-import-name id))
        (str "capability " id " must not claim a portable import name yet")))
  ;; Every name stays unique, so no two capabilities can collide onto one
  ;; component import key.
  (let [names (vals contract/capability-import-names)]
    (is (= (count names) (count (distinct names))))))
