(ns kotoba.abi.contract
  "Small, dependency-free constants shared by generated ABI consumers."
  #?(:clj (:require [clojure.java.io :as io])))

(def component-world "kotoba:app/kotoba-app@0.1.0")
(def component-target :wasm-component-kotoba-v1)
(def component-world-v2 "kotoba:app/kotoba-app@0.2.0")
(def component-target-v2 :wasm-component-kotoba-v2)
(def typed-capability-world-v3 "aiueos:capability/application@0.3.0")
(def wasi-version "0.3.0")
(def ambient-wasi? false)

;; Admission is intentionally an ABI concern: every compiler and runtime
;; adapter must agree on this envelope before a Component is linked. Identity
;; verification and concrete engine execution remain local runtime concerns.
(def admission-keys
  #{:target :wasi-version :profile :imports :exports :grants
    :provider-bindings :abilities :runtime-bindings :ambient-wasi :budgets :identity})

(def component-profiles
  {:sync {:required-budgets [:fuel :memory-pages]}
   :async {:required-budgets [:fuel :memory-pages :deadline-ms :max-items :max-bytes]
           :cancellation :required}})

(defn profile? [profile]
  (contains? component-profiles profile))

(defn required-budget-keys [profile]
  (get-in component-profiles [profile :required-budgets]))

(defn cancellation-required? [profile]
  (= :required (get-in component-profiles [profile :cancellation])))

;; Ids 1-7 are the operations `aiueos:capability@0.3.0` (wit/aiueos-capability-v2)
;; defines as `grant-request` cases and that a runtime host can execute today.
;;
;; Ids 8-12 are the Application Profile capabilities (ADR-2607201300;
;; `kotoba/lang/component-model-v1.edn` in kotoba-component declares the same
;; ids, interfaces, and function names). Naming them here is what lets the
;; compiler EMIT a component that imports them -- without a name,
;; `capability-import-name` throws and an application whose only effects are
;; state/ui/llm/storage cannot be compiled at all, which is how the profile
;; stayed unreachable from `.kotoba` source.
;;
;; A name is not a runtime claim. These five are deliberately NOT `grant-request`
;; cases in the v0.3 WIT: no host implements them yet, so a component importing
;; them links only against a provider that supplies the matching
;; `kotoba:application/<interface>@1` function. Adding a case to the enum is the
;; separate, larger P1 work ADR-2607252500 tracks.
;;
;; Ids 13-16 are the bounded, linear stream/object operations. Their names are
;; admitted only together with the explicit Task<Stream<Bytes>> WIT resources
;; below; no ambient executor or host filesystem is implied.
(def capability-import-names
  {1 "aiueos-identity-sign"
   2 "aiueos-identity-verify"
   3 "aiueos-hash-sha256"
   4 "aiueos-http-post"
   5 "aiueos-log-read"
   6 "aiueos-log-append"
   7 "aiueos-clock-now"
   8 "aiueos-state-transact"
   9 "aiueos-ui-commit"
   10 "aiueos-ui-next-event"
   11 "aiueos-llm-generate"
   12 "aiueos-storage-transact"
   13 "aiueos-http-get-stream"
   14 "aiueos-object-get-stream"
   15 "aiueos-object-put-block"
   16 "aiueos-object-compare-and-set-ref"})

;; Exact routing for `aiueos:capability@0.3.0`. Historical application-profile
;; ids 8-12 intentionally remain outside this table: a valid legacy import is
;; not automatically a typed v0.3 operation.
(def typed-capability-operations
  {1 {:name :identity/sign :import "aiueos-identity-sign"
      :interface "identity" :function "sign" :grant-request "identity-sign"
      :request :bytes-request :response :bytes-response}
   2 {:name :identity/verify :import "aiueos-identity-verify"
      :interface "identity" :function "verify" :grant-request "identity-verify"
      :request :bytes-request :response :bool}
   3 {:name :hash/sha256 :import "aiueos-hash-sha256"
      :interface "hash" :function "sha256" :grant-request "hash-sha256"
      :request :bytes-request :response :bytes-response}
   4 {:name :http/post :import "aiueos-http-post"
      :interface "http" :function "post" :grant-request "http-post"
      :request :http-post-request :response :http-post-response}
   5 {:name :log/read :import "aiueos-log-read"
      :interface "log" :function "read" :grant-request "log-read"
      :request :log-read-request :response :log-read-response}
   6 {:name :log/append :import "aiueos-log-append"
      :interface "log" :function "append" :grant-request "log-append"
      :request :bytes-request :response :unit}
   7 {:name :clock/now :import "aiueos-clock-now"
      :interface "clock" :function "now" :grant-request "clock-now"
      :request :unit :response :u64}
   13 {:name :http/get-stream :import "aiueos-http-get-stream"
       :interface "http" :function "get-stream" :grant-request "http-get-stream"
       :request :http-get-stream-request :response :bytes-task :async true}
   14 {:name :object/get-stream :import "aiueos-object-get-stream"
       :interface "object-store" :function "get-stream"
       :grant-request "object-get-stream"
       :request :object-get-stream-request :response :bytes-task :async true}
   15 {:name :object/put-block :import "aiueos-object-put-block"
       :interface "object-store" :function "put-block"
       :grant-request "object-put-block"
       :request :object-put-block-request :response :unit}
   16 {:name :object/compare-and-set-ref
       :import "aiueos-object-compare-and-set-ref"
       :interface "object-store" :function "compare-and-set-ref"
       :grant-request "object-compare-and-set-ref"
       :request :object-compare-and-set-ref-request
       :response :object-compare-and-set-ref-response}})

(def typed-capability-ids (set (keys typed-capability-operations)))

(defn typed-capability-operation
  "Resolve an id to its exact typed v0.3 operation. Unknown and legacy-only
  ids fail closed."
  [id]
  (or (get typed-capability-operations id)
      (throw (ex-info "capability has no typed v0.3 operation"
                      {:phase :component-abi-v3 :capability-id id}))))

(def stream-contract
  {:format :kotoba.stream/bytes-v1
   :task :poll-cancel
   :stream :pull-cancel
   :required-limits #{:deadline-ms :max-items :max-bytes}
   :zero-copy? false
   :ambient-executor? false})

(defn valid-stream-limits?
  [limits]
  (and (map? limits)
       (= #{:deadline-ms :max-items :max-bytes} (set (keys limits)))
       (every? #(pos-int? (get limits %))
               [:deadline-ms :max-items :max-bytes])))

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

#?(:clj (declare typed-capability-wit-v3))

(defn world-wit-v2
  "Render the pure v2 world, or return the authoritative v0.3 capability world
  for an effectful JVM compiler/runtime consumer. It never reuses v1 scalar
  imports under a v2 target label.

  This replaces the earlier unconditional rejection of effectful v2 lowering:
  ADR-2607252500 makes the Wasm Component the primary application artifact, so
  an effectful consumer is served the pinned `aiueos:capability@0.3.0` world
  rather than being told to wait. `:cljs` still has no reader for the resource
  and therefore still refuses."
  [capability-ids]
  (if (seq capability-ids)
    #?(:clj (typed-capability-wit-v3)
       :cljs (throw (ex-info "typed capability WIT source is JVM-only"
                             {:phase :component-abi-v3
                              :capability-ids (set capability-ids)})))
    (str "package kotoba:app@0.2.0;\n\nworld kotoba-app {\n"
         "  export main: func() -> s64;\n}\n")))

#?(:clj
   (defn typed-capability-wit-v3
     "Return the authoritative typed capability WIT bytes from this pinned ABI
     dependency. Compiler consumers must copy this exact source into their
     temporary package graph instead of maintaining a hand-written mirror."
     []
     (let [resource (io/resource "aiueos-capability-v2/aiueos-capability.wit")]
       (when-not resource
         (throw (ex-info "authoritative typed capability WIT is unavailable"
                         {:phase :component-abi-v3})))
       (slurp resource))))

(defn exact-import-grant-provider-sets?
  "True only when declared imports, grants, and provider binding keys agree.
  This is the invariant that all runtime adapters must preserve before linking."
  [imports grants providers]
  (and (set? imports)
       (set? grants)
       (map? providers)
       (= imports grants (set (keys providers)))))

;; Portable-host execution identity. These values are intentionally only
;; descriptors: a guest never receives a serializable host capability handle.
(def plan-keys
  #{:format :plan-cid :code-closure-cid :artifact-cid :compiler-contract
    :requested-effects :requested-resources :input-cid :budget})

(def policy-decision-keys
  #{:format :decision-cid :plan-cid :policy-cid :db-basis :result :reasons
    :issued-at :expires-at})

(def capability-lease-keys
  #{:format :capability-cid :execution-identity-cid :component-cid :resource-cid
    :purpose :expires-at :uses :transfer :delegation-depth})

(def approval-keys
  #{:format :approval-cid :plan-cid :policy-cid :db-basis :resources
    :input-cid :approver-cid :issued-at :expires-at})

(def execution-identity-keys
  #{:format :plan-cid :code-closure-cid :artifact-cid :compiler-contract
    :component-cid :wit-world-cid :package-lock-cid :policy-cid
    :policy-decision-cid :db-basis :grant-cids :approval-cids
    :runtime-identity :input-cid :outcome-cid :host-receipt-cids})

(def component-authority-event-keys
  #{:murakumo.component/version :murakumo.component/event
    :murakumo.component/component-cid :murakumo.component/epoch
    :murakumo.component/sequence :murakumo.component/node})

(def component-authority-envelope-keys
  #{:format :algorithm :key-id :issuer :audience :issued-at-ms
    :event :signature})

(defn- cid? [value]
  (and (string? value) (boolean (re-matches #"b.+" value))))

(defn- cid-vector? [value]
  (and (vector? value) (every? cid? value) (= (count value) (count (distinct value)))))

(defn valid-plan?
  "Validate the portable, bounded plan descriptor. Policy evaluation is a
  host concern; this contract only prevents shape drift before that boundary."
  [plan]
  (and (map? plan)
       (= plan-keys (set (keys plan)))
       (= :kotoba.plan/v1 (:format plan))
       (every? cid? ((juxt :plan-cid :code-closure-cid :artifact-cid
                            :compiler-contract :input-cid) plan))
       (set? (:requested-effects plan))
       (set? (:requested-resources plan))
       (map? (:budget plan))))

(defn valid-policy-decision?
  "Validate a deterministic, basis-bound policy decision. A `:permit` is not
  authority by itself: the host must still issue and enforce scoped leases."
  [decision]
  (and (map? decision)
       (= policy-decision-keys (set (keys decision)))
       (= :kotoba.policy-decision/v1 (:format decision))
       (every? cid? ((juxt :decision-cid :plan-cid :policy-cid :db-basis) decision))
       (contains? #{:permit :deny} (:result decision))
       (vector? (:reasons decision))
       (string? (:issued-at decision))
       (string? (:expires-at decision))))

(defn valid-capability-lease?
  "Validate the serializable audit descriptor for one host-managed capability.
  There is deliberately no handle/token field: possession of this descriptor
  cannot authorize a guest or another host."
  [lease]
  (and (map? lease)
       (= capability-lease-keys (set (keys lease)))
       (= :kotoba.capability-lease/v1 (:format lease))
       (every? cid? ((juxt :capability-cid :execution-identity-cid
                            :component-cid :resource-cid) lease))
       (keyword? (:purpose lease))
       (string? (:expires-at lease))
       (pos-int? (:uses lease))
       (contains? #{:non-transferable :same-component} (:transfer lease))
       (nat-int? (:delegation-depth lease))))

(defn valid-approval?
  "Validate an immutable human/organizational approval witness. An approval is
  authority only for the exact plan, policy, basis, input and resource set it
  names; a host must additionally verify its CID, signer and expiry."
  [approval]
  (and (map? approval)
       (= approval-keys (set (keys approval)))
       (= :kotoba.approval/v1 (:format approval))
       (every? cid? ((juxt :approval-cid :plan-cid :policy-cid :db-basis
                            :input-cid :approver-cid) approval))
       (set? (:resources approval))
       (seq (:resources approval))
       (string? (:issued-at approval))
       (string? (:expires-at approval))))

(defn valid-execution-identity?
  "Validate the immutable identity shared by compiler, authority, runtime and
  fact-store. Component fields are both present or both nil for explicitly
  versioned compatibility runs; production admission decides whether nil is
  allowed for its profile."
  [identity]
  (and (map? identity)
       (= execution-identity-keys (set (keys identity)))
       (= :kotoba.execution-identity/v1 (:format identity))
       (every? cid? ((juxt :code-closure-cid :artifact-cid :compiler-contract
                            :package-lock-cid :policy-cid :policy-decision-cid
                            :db-basis :runtime-identity :input-cid :outcome-cid) identity))
       (or (and (cid? (:component-cid identity)) (cid? (:wit-world-cid identity)))
           (and (nil? (:component-cid identity)) (nil? (:wit-world-cid identity))))
       (or (cid? (:plan-cid identity)) (nil? (:plan-cid identity)))
       (every? cid-vector? ((juxt :grant-cids :approval-cids :host-receipt-cids) identity))))

(defn valid-component-authority-event?
  "Validate Murakumo's portable placement-fence event. Authentication and
  replay/order enforcement remain host transport concerns; this exact shape
  prevents Murakumo and Kototama from independently inventing the wire ABI."
  [event]
  (and (map? event)
       (= component-authority-event-keys (set (keys event)))
       (= 1 (:murakumo.component/version event))
       (contains? #{:placed :revoked} (:murakumo.component/event event))
       (cid? (:murakumo.component/component-cid event))
       (pos-int? (:murakumo.component/epoch event))
       (pos-int? (:murakumo.component/sequence event))
       (let [node (:murakumo.component/node event)]
         (if (= :placed (:murakumo.component/event event))
           (and (string? node) (seq node) (<= (count node) 4096))
           (nil? node)))))

(defn component-authority-signing-payload
  "Canonical, language-portable value covered by an authority signature.
  It is a vector rather than a map so map iteration order cannot change bytes."
  [{:keys [format algorithm key-id issuer audience issued-at-ms event]}]
  (pr-str
   [format algorithm key-id issuer audience issued-at-ms
    [(:murakumo.component/version event)
     (:murakumo.component/event event)
     (:murakumo.component/component-cid event)
     (:murakumo.component/epoch event)
     (:murakumo.component/sequence event)
     (:murakumo.component/node event)]]))

(defn valid-component-authority-envelope?
  "Validate the exact signed Murakumo-to-Kototama envelope. Public-key trust,
  signature verification, clock skew, and replay enforcement are runtime
  responsibilities performed after this shared shape gate."
  [envelope]
  (and (map? envelope)
       (= component-authority-envelope-keys (set (keys envelope)))
       (= :murakumo.component-authority/v1 (:format envelope))
       (= :ed25519 (:algorithm envelope))
       (every? #(and (string? %) (seq %) (<= (count %) 4096))
               ((juxt :key-id :issuer :audience) envelope))
       (pos-int? (:issued-at-ms envelope))
       (valid-component-authority-event? (:event envelope))
       (string? (:signature envelope))
       (boolean (re-matches #"[0-9a-f]{128}" (:signature envelope)))))

;; These vectors are intentionally plain EDN-shaped values.  A host may use
;; any codec/language, but must obtain the same accept/reject result before it
;; issues a non-serializable capability handle or invokes an engine.
(def portable-execution-v1-vectors
  (let [cid "bafyportablehostcontract"
        plan {:format :kotoba.plan/v1 :plan-cid cid :code-closure-cid cid
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
    [{:id :plan/valid :kind :plan :expect :accept :value plan}
     {:id :plan/unknown-field :kind :plan :expect :reject :value (assoc plan :ambient true)}
     {:id :decision/valid :kind :policy-decision :expect :accept :value decision}
     {:id :decision/invalid-result :kind :policy-decision :expect :reject
      :value (assoc decision :result :maybe)}
     {:id :lease/non-bearer :kind :capability-lease :expect :accept :value lease}
     {:id :lease/forged-handle :kind :capability-lease :expect :reject
      :value (assoc lease :host-handle "42")}
     {:id :approval/valid :kind :approval :expect :accept :value approval}
     {:id :approval/policy-substitution :kind :approval :expect :reject
      :value (assoc approval :policy-cid nil)}
     {:id :identity/valid-component :kind :execution-identity :expect :accept :value identity}
     {:id :identity/partial-component :kind :execution-identity :expect :reject
      :value (assoc identity :wit-world-cid nil)}]))

(defn conformance-result
  "Return the schema-level result for one `portable-execution-v1-vectors`
  entry. Engine hosts add their own component/lease lifecycle checks after
  this shared descriptor gate."
  [{:keys [kind value]}]
  (case kind
    :plan (valid-plan? value)
    :policy-decision (valid-policy-decision? value)
    :capability-lease (valid-capability-lease? value)
    :approval (valid-approval? value)
    :execution-identity (valid-execution-identity? value)
    false))
