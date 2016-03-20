(ns p01.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [put! <! chan timeout alts! close!]]
            [dirac.fixtures :refer [setup! task-finished! wait-for-transcript-match]]
            [dirac.automation :refer [wait-for-dirac-frontend-initialization wait-for-implant-initialization
                                      wait-for-console-initialization switch-inspector-panel!
                                      open-dirac-devtools! close-dirac-devtools! reset-connection-id-counter!
                                      switch-to-dirac-prompt! switch-to-js-prompt!
                                      wait-switch-to-console]]))

(setup!)

(go
  (reset-connection-id-counter!)
  (open-dirac-devtools!)
  (<! (wait-for-dirac-frontend-initialization))
  (<! (wait-for-implant-initialization))
  (<! (wait-switch-to-console))
  (switch-to-dirac-prompt!)
  (<! (wait-for-transcript-match #".*will try reconnect in 4 seconds.*" (* 60 1000)))
  (close-dirac-devtools!)
  (task-finished!))