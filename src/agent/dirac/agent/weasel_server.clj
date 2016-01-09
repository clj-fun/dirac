; original code taken from https://github.com/tomjakubowski/weasel/tree/8bfeb29dbaf903e299b2a3296caed52b5761318f
(ns dirac.agent.weasel-server
  (:refer-clojure :exclude [loaded-libs])
  (:require [cljs.repl]
            [cljs.compiler :as cmp]
            [clojure.tools.logging :as log]
            [org.httpkit.server :as http]
            [dirac.agent.ws-server :as server]
            [dirac.agent.utils :as utils])
  (:import (clojure.lang IDeref Atom)))

(def default-opts {:host       "localhost"
                   :port       9001
                   :port-range 10})

; -- WeaselREPLEnv ----------------------------------------------------------------------------------------------------------

(declare setup-env)
(declare request-eval)
(declare load-javascript)
(declare tear-down-env)

(defrecord WeaselREPLEnv [id options server client-response-promise]
  cljs.repl/IJavaScriptEnv
  (-setup [this opts]
    (log/trace (str this) "-setup called" opts)
    (setup-env this opts))
  (-evaluate [this filename line js]
    (log/trace (str this) "-evaluate called" filename line "\n" js)
    (request-eval this js))
  (-load [this provides url]
    (log/trace (str this) "-load called" (str this) provides url)
    (load-javascript this provides url))
  (-tear-down [this]
    (log/trace (str this) "-tear-down called" (str this))
    (tear-down-env this))

  Object
  (toString [this]
    (str "WeaselREPLEnv#" (:id this))))

; -- WeaselREPLEnv construction ---------------------------------------------------------------------------------------------

(def last-env-id (volatile! 0))

(defn next-env-id! []
  (vswap! last-env-id inc))

(defn make-weasel-repl-env
  "Returns a JS environment to be passed to REPL or Piggieback."
  [options]
  (let [effective-options (merge default-opts options)
        repl-env (WeaselREPLEnv. (next-env-id!) effective-options (atom nil) (atom nil))]
    (log/trace "Made" (str repl-env))
    repl-env))

; -- WeaselREPLEnv access ---------------------------------------------------------------------------------------------------

(defn get-server [env]
  {:pre [(instance? WeaselREPLEnv env)]}
  (let [server-atom (:server env)]
    (assert server-atom)
    (assert (instance? Atom server-atom))
    @server-atom))

(defn set-server! [env server]
  {:pre [(instance? WeaselREPLEnv env)]}
  (reset! (:server env) server))

(defn get-client-response-promise-atom [env]
  {:pre [(instance? WeaselREPLEnv env)]}
  (let [client-response-promise-atom (:client-response-promise env)]
    (assert client-response-promise-atom)
    (assert (instance? Atom client-response-promise-atom))
    client-response-promise-atom))

; -- message builders -------------------------------------------------------------------------------------------------------

(defn make-occupied-error-message []
  {:op   :error
   :type :occupied})

(defn make-eval-js-request-message [js]
  {:op   :eval-js
   :code js})

; -- message processing -----------------------------------------------------------------------------------------------------

(defmulti process-message (fn [_env msg] (:op msg)))

(defmethod process-message :default [env message]
  (log/debug (str env) "Received unrecognized message:\n" (utils/pp message)))

(defmethod process-message :result [env message]
  (let [result (:value message)
        client-response-promise @(get-client-response-promise-atom env)]
    (when client-response-promise                                                                                             ; silently ignore results delivered after timeout, TODO: implement id matching or something here
      (assert (instance? IDeref client-response-promise))
      (deliver client-response-promise result))))

(defmethod process-message :ready [_env _message])

(defmethod process-message :error [env message]
  (log/error (str env) "DevTools reported error:\n" (utils/pp message)))

; -- env helpers ------------------------------------------------------------------------------------------------------------

(defn promise-new-client-response! [env]
  {:pre [(instance? WeaselREPLEnv env)]}
  (let [response-promise-atom (get-client-response-promise-atom env)]
    (assert (instance? Atom response-promise-atom))
    (assert (nil? @response-promise-atom) "promise-new-client-response! previous response promise pending")
    (reset! response-promise-atom (promise))))

(defn wait-for-promised-response! [env]
  {:pre [(instance? WeaselREPLEnv env)]}
  (let [response-promise-atom (get-client-response-promise-atom env)]
    (assert (instance? Atom response-promise-atom))
    (let [response-promise @response-promise-atom]
      (assert response-promise "wait-for-promised-response! expected non-nil pending promise")
      (assert (instance? IDeref response-promise))
      (log/trace (str env) "Waiting for promised response...")
      (let [response @response-promise]                                                                                       ; <===== WILL BLOCK! TODO: implement a timeout
        (log/trace (str env) "Got promised response.")
        (reset! response-promise-atom nil)
        response))))

(defn send-occupied-response-close-channel-and-reject-client! [env channel]
  (log/debug (str env) "Client already connected. Rejecting new client with occupied message on channel" channel)
  (http/send! channel (server/serialize-msg (make-occupied-error-message)))
  (http/close channel)
  :reject)

(defn on-client-connection [env server channel]
  ; we allow only one client connection at a time
  (if (server/has-clients? server)
    (send-occupied-response-close-channel-and-reject-client! env channel)))

(defn on-message [env _server _client message]
  ; we don't need to pass server and client into process-message
  ; because we allow only one client connection and server is stored in the env if needed
  (process-message env message))

; -- WeaselREPLEnv implementation -------------------------------------------------------------------------------------------

(defn setup-env [env _opts]
  (let [options (:options env)
        {:keys [pre-connect]} options
        server-options (assoc options
                         :on-message (partial on-message env)
                         :on-client-connection (partial on-client-connection env))
        server (server/create! server-options)]
    (set-server! env server)
    (if pre-connect
      (pre-connect env (server/get-url server)))
    (server/wait-for-first-client server)
    env))

(defn tear-down-env [env]
  (log/trace "Destroying" (str env))
  (server/destroy! (get-server env))
  (log/debug "Destroyed" (str env)))

(defn request-eval [env js]
  (promise-new-client-response! env)
  (server/send! @(server/get-first-client-promise (get-server env)) (make-eval-js-request-message js))                        ; <===== MIGHT BLOCK if there is currently no client connected TODO: implement timeout
  (wait-for-promised-response! env))                                                                                          ; <===== WILL BLOCK! until client responds

(defn load-javascript [env provides _]
  (request-eval env (str "goog.require('" (cmp/munge (first provides)) "')")))                                                ; what is this?