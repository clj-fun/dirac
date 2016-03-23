(ns dirac.test.settings)

; we want this stuff to be accessible both from clojure and clojurescript

(def ^:const SECOND 1000)
(def ^:const MINUTE (* 60 SECOND))
(def ^:const TEST_DIRAC_AGENT_PORT 8021)
(def ^:const DIRAC_AGENT_BOOT_TIME 2000)
(def ^:const TEST_NREPL_SERVER_PORT 8020)
(def ^:const LAUNCH_TRANSCRIPT_TASK_KEY "launchTranscriptTask")
(def ^:const LAUNCH_TRANSCRIPT_TASK_MESSAGE "launch-transcript-task")
(def ^:const TRANSCRIPT_MATCH_TIMEOUT 5000)

; -- cljs access ------------------------------------------------------------------------------------------------------------

(defmacro get-test-dirac-agent-port []
  TEST_DIRAC_AGENT_PORT)

(defmacro get-dirac-agent-boot-time []
  DIRAC_AGENT_BOOT_TIME)

(defmacro get-test-nrepl-server-port []
  TEST_NREPL_SERVER_PORT)

(defmacro get-launch-transcript-task-key []
  LAUNCH_TRANSCRIPT_TASK_KEY)

(defmacro get-launch-transcript-task-message []
  LAUNCH_TRANSCRIPT_TASK_MESSAGE)

(defmacro get-transcript-match-timeout []
  TRANSCRIPT_MATCH_TIMEOUT)