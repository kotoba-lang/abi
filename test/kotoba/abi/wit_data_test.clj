(ns kotoba.abi.wit-data-test
  "The authoritative WIT is embedded in a generated namespace so every platform
  reads the same string. Embedding buys portability at the cost of a second
  copy, so the copy has to be checked: this asserts each embedded string still
  equals the file it came from.

  It runs on the JVM, where the classpath resource is readable. That is the
  point — the check needs the side that can still see the original."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kotoba.abi.contract :as contract]
            [kotoba.abi.wit-data :as wit-data]))

(deftest embedded-wit-matches-the-file
  (testing "a generated copy that silently drifted from its source would be
            worse than no copy, because consumers would compile against WIT
            that no longer exists in this repository"
    (is (seq wit-data/files))
    (doseq [[var-name path] wit-data/files]
      (let [resource (io/resource path)
            embedded (var-get (resolve (symbol "kotoba.abi.wit-data" (name var-name))))]
        (is (some? resource) (str path " must be on the classpath"))
        (is (= (slurp resource) embedded)
            (str var-name " has drifted from " path
                 " — regenerate with scripts/gen_wit_data.clj"))))))

(deftest the-typed-capability-accessor-serves-the-pinned-world
  (testing "the accessor is no longer JVM-only, and still returns the same
            authoritative bytes it did when it read the classpath"
    (let [wit (contract/typed-capability-wit-v3)]
      (is (string? wit))
      (is (= (slurp (io/resource "aiueos-capability-v2/aiueos-capability.wit")) wit))
      (testing "the directory says v2 and the package says 0.3.0; that is the
                existing naming, and the docstring's claim about which world is
                served has to stay true"
        (is (re-find #"package aiueos:capability@0\.3\.0;" wit))))))

(deftest an-effectful-v2-world-no-longer-refuses-off-jvm
  (testing "world-wit-v2 used to throw 'typed capability WIT source is
            JVM-only' for any effectful consumer under ClojureScript, which is
            what left this profile with no cross-implementation evidence"
    (is (= (contract/typed-capability-wit-v3)
           (contract/world-wit-v2 #{1})))
    (testing "the pure path is unchanged"
      (is (re-find #"package kotoba:app@0\.2\.0;" (contract/world-wit-v2 #{}))))))
