(ns peak.client.ws
    (:require-macros
              [cljs.core.async.macros :as asyncm :refer (go go-loop)])
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [peak.client.tools :refer [round container getValueById getElementById changeValueById on-key-press on-click]]
              [cljs.core.async :as async :refer (<! >! put! chan)] 
              [cljs.core.match :refer-macros [match]]
              [cljs.reader :refer [read-string]]
              [clojure.string  :as str]
              [taoensso.encore :as encore :refer-macros (have have?)]
              [taoensso.sente  :as sente :refer (cb-success?)]
              [taoensso.timbre :as timbre
                    :refer-macros (tracef debugf infof warnf errorf)]
              [ajax.core :refer [GET POST DELETE]]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]))


;(timbre/set-level! :trace) ; Uncomment for more logging

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"
       {:type :auto
        :packer :edn
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state)
  )

;;;; Util for logging output to on-screen console

(def output-el (getElementById "output"))

(defn ->output! [fmt & args]
  (let [msg (apply encore/format fmt args)]
    (timbre/debug msg)
    (aset output-el "value" (str "• " (.-value output-el) "\n" msg))
    (aset output-el "scrollTop" (.-scrollHeight output-el))))


(def online-players (reagent/atom nil))

;;;; Sente event handlers

(defmulti -event-msg-handler :id)

(defn event-msg-handler [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event]}]
  (->output! "Unhandled event on client: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (->output! "Channel socket successfully established!: %s" new-state-map)
      (->output! "Channel socket state change: %s"              new-state-map))))


(def chat-output (getElementById "chat-msgs"))


(defn ->chat! [fmt & args]
  (let [msg (apply encore/format fmt args)]
    (timbre/debug msg)
    (aset chat-output "value" (str "• "(.-value chat-output) "\n" msg))
    (aset chat-output "scrollTop" (.-scrollHeight chat-output))))


(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  ;;Handler for different push events from the server
  (match [?data]
    [[:chat/push-back new-message]] (->chat! "%s" new-message)
    [[:server/uids uids]] (reset! online-players uids)
    [[:server/login-failed a]] (.alert js/window "Rossz jelszó.")
    :else (->output! "Unhandled push event from server: %s" ?data)
  ))

(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (->output! "Handshake: %s" ?data)))

(defmethod -event-msg-handler :chat/all-messages
  [{:as ev-msg :keys [?data]}]
  (->chat! "%s" ?data))

;;;; Sente event router (our `event-msg-handler` loop)

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_
    (sente/start-client-chsk-router!
      ch-chsk event-msg-handler)))



;; UI Events

(on-click "btn1"
    (fn [ev]
      (->output! "Button 1 was clicked (won't receive any reply)")
      (chsk-send! [:example/button1 {:had-a-callback? "nope"}])))

(on-click "btn2"
    (fn [ev]
      (->output! "Button 2 was clicked (will receive reply)")
      (chsk-send! [:example/button2 {:had-a-callback? "indeed"}] 5000
        (fn [cb-reply] (->output! "Callback reply: %s" cb-reply)))))

(on-click "btn3"
    (fn [ev]
      (->output! "Button 3 was clicked
                  (will ask server to test rapid async push)")
      (chsk-send! [:example/test-rapid-push])))

(on-click "btn4"
    (fn [ev]
      (->output! "Button 4 was clicked
                  (will toggle async broadcast loop)")
      (chsk-send! [:example/toggle-broadcast] 5000
        (fn [cb-reply]
          (when (cb-success? cb-reply)
            (let [loop-enabled? cb-reply]
              (if loop-enabled?
                (->output! "Async broadcast loop now enabled")
                (->output! "Async broadcast loop now disabled"))))))))

(on-click "btn5"
     (fn [ev]
       (->output! "Disconnecting")
       (sente/chsk-disconnect! chsk)))

(on-click "btn6"
     (fn [ev]
       (->output! "Reconnecting")
       (sente/chsk-reconnect! chsk)))

(on-click "btn-login"
    (fn [ev]
      (let [user-id (getValueById "login-uid")
            user-pass (getValueById "login-password")]
        (if (str/blank? user-id)
          (js/alert "Please enter a user-id first")
          (do
            (->output! "Logging in with user-id %s" user-id)
            (sente/ajax-lite "/auth"
              {:method :post
               :headers {:X-CSRF-Token (:csrf-token @chsk-state)}
               :params  {:user-id (str user-id)
                         :user-pass (str user-pass)}}
              (fn [ajax-resp]
                (->output! "Ajax login response: %s" ajax-resp)
                (let [login-successful? true ; Your logic here
                      ]
                  (if-not login-successful?
                    (->output! "Login failed")
                    (do
                      (->output! "Login successful")
                      (sente/chsk-reconnect! chsk)))))))))))