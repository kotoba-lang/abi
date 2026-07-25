(ns kotoba.abi.test-runner
  (:require [clojure.test :as test]
            [kotoba.abi.contract-test]))

(defn -main [& _]
  (let [result (test/run-tests 'kotoba.abi.contract-test)]
    (when (pos? (+ (:fail result) (:error result)))
      (System/exit 1))))
