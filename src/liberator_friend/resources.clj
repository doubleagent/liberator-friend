(ns liberator-friend.resources
  "Helpful extensions to liberator."
  (:require [clojure.string :as s]
            [cemerick.friend :as friend]
            [clojure.data.json :as json]
            [compojure.route :as route]
            [liberator.conneg :as conneg]
            [liberator.core :as l]
            [liberator.representation :as rep]
            [ring.util.response :as r])
  (:import [liberator.representation RingResponse]))

;; ## Utilities

(defn accepted-types
  "Returns a sequence of content types accepted by the supplied
  request. If no accept header is present, returns nil."
  [req]
  (when-let [accepts-header (get-in req [:headers "accept"])]
    (->> (conneg/sorted-accept accepts-header ["*/*"])
         (map (comp conneg/stringify :type))
         (filter not-empty))))

(defn get-media
  "Pulls the media type out of the request, or parses it from the
  content headers.

  allowed-types is a set containing pairs (e.g., [\"text\" \"*\"])
  or strings (e.g., \"text/plain\").

  If no allowed-types is present, returns the type most favored by the
  client."
  ([req]
     (first (accepted-types req)))
  ([req allowed-types]
     {:pre [(contains? (:headers req) "accept")
            (sequential? allowed-types)]}
     (l/try-header "Accept"
                   (when-let [accept-header (get-in req [:headers "accept"])]
                     (let [type (conneg/best-allowed-content-type
                                 accept-header
                                 allowed-types)]
                       (not-empty (conneg/stringify type)))))))

(def ring-response rep/ring-response)
(def ringify
  (comp rep/ring-response r/response))

(defn to-response
  "to-response does more intelligent response parsing on your
  liberator ring responses.

  Liberator tries to coerce your returned value into the proper
  content type; maps get turned into json or clojure as required, etc.

  The problem with this, naively, is that ring's responses are ALSO
  just bare maps. If you return a bare map to a text/html request,
  liberator tries to coerce the map into HTML.

  The liberator solution is a special wrapper type called RingResponse
  that gets passed through without diddling. This function handles the
  most common response type cases in one spot

  If you pass in an instance of RingResponse, to-response passes it
  through untouched.

  If you pass in a ring response map, it's wrapped in an instance of
  RingResponse and passed on (and ignored by liberator).

  else, liberator tries to coerce as before."
  [t req]
  (cond (instance? RingResponse t) t
        (r/response? t) (rep/ring-response t)
        :else (rep/ring-response (rep/as-response t req))))

(defn generic
  "If you pass a response back to liberator before it's parsed the
  content type it freaks out and says that it can't dispatch on
  null. This generic method calls the proper multimethod rendering on
  json, clojure, etc, all of that business, before passing the result
  back up the chain through liberator."
  [data req media-type]
  (to-response data (assoc-in req [:representation :media-type] media-type)))

(defn media-typed
  "Accepts a map of encoding -> handler (which can be a constant or a
  function) and returns a NEW handler that delegates properly based on
  the request's encoding. If no encoding is found, calls the handler
  under the :default key."
  [& ms]
  (let [m (apply merge ms)]
    (fn [req]
      (let [get-media #(get-in % [:representation :media-type])
            parsed-type (get-media req)
            media-type (or parsed-type
                           (get-media (l/negotiate-media-type req)))]
        (when-let [handler (get m media-type (:default m))]
          (if (fn? handler)
            (handler req)
            (if-not (= parsed-type media-type)
              (generic handler req media-type)
              (to-response handler req))))))))

(defn with-default
  "Duplicates the entry under the supplied media-type as the default
  in the supplied response map."
  [default-type m]
  (if-let [response (m default-type)]
    (assoc m :default response)
    m))

;; ## The Good Stuff

(def base-resource
  "Base for all resources.

   Due to the way liberator's resources merge, these base definitions
   define a bunch of content types, even if the resources that inherit
   from them don't. The defaults are here to provide reasonable text
   error messages, instead of returning big slugs of html."
  (let [not-found (comp rep/ring-response (route/not-found "Route not found!"))
        base {"text/html" not-found}]
    {:handle-not-acceptable
     (->> {"application/json" {:success false
                               :message "No acceptable resource available"}
           "text/plain" "No acceptable resource available."}
          (with-default "text/plain")
          (media-typed base))

     :handle-not-found
     (->> {"application/json" {:success false
                               :message "Resource not found."}
           "text/plain" "Resource not found."}
          (with-default "text/plain")
          (media-typed base))}))

;; ## Friend Integration

(defn roles
  "Returns an authorization function that checks if the authenticated
  user has the specified roles. (This is the usual friend behavior.)"
  [roles]
  (fn [id]
    (friend/authorized? roles id)))

(defn unauthorized!
  "Throws the proper unauthorized! slingshot error if authentication
  fails. This error is picked up upstream by friend's middleware."
  [handler req]
  (friend/throw-unauthorized (friend/identity req)
                             {::friend/wrapped-handler handler}))

(def friend-resource
  "Base resource that will handle authentication via friend's
  mechanisms. Provide an authorization function and you'll be good to
  go."
  (reduce into {}
          [base-resource
           {:handle-unauthorized
            (media-typed {"text/html" (fn [req]
                                        (unauthorized!
                                         (-> req :resource :allowed?)
                                         req))
                          "application/json"
                          {:success false
                           :message "Not authorized!"}
                          :default (constantly "Not authorized.")})}]))

;; ## Base Resources
;;
;; These functions and vars provide base resources that make it easier
;; to define new liberator resources.

(defn friend-auth
  "Returns a base resource that authenticates using the supplied
  auth-fn. Authorization failure will trigger Friend's default
  unauthorized response."
  [auth-fn]
  (reduce into {} [friend-resource {:authorized? auth-fn}]))

(defn role-auth
  "Returns a base resource that authenticates users against the
  supplied set of roles."
  [role-input]
  (friend-auth (comp (roles role-input) :request)))

(def authenticated-base
  "Returns a base resource that authenticates users against the
  supplied set of roles."
  (friend-auth (comp boolean friend/identity :request)))
