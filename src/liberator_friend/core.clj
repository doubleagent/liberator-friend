(ns liberator-friend.core
  "Core namespace for the liberator-friend post."
  (:gen-class)
  (:require [cemerick.friend.credentials :as creds]
            [compojure.handler :refer [api]]
            [compojure.core :as compojure :refer (GET defroutes)]
            [liberator-friend.middleware.auth :as auth]
            [liberator-friend.resources :as r]
            [liberator.core :as l :refer [defresource]]
            [ring.middleware.reload :as rl]))

;; ## "The Database"

(def users
  "dummy in-memory user database."
  {"root" {:username "root"
           :password (creds/hash-bcrypt "admin_password")
           :roles #{:admin}}
   "jane" {:username "jane"
           :password (creds/hash-bcrypt "user_password")
           :roles #{:user}}})

;; ## Site Resources

(defresource admin-resource
  (r/role-auth #{:admin})
  :allowed-methods [:get]
  :available-media-types ["text/plain"]
  :handle-ok "Welcome, admin!")

(defresource user-resource
  (r/role-auth #{:user})
  :allowed-methods [:get]
  :available-media-types ["text/plain"]
  :handle-ok "Welcome, user!")

(defresource authenticated-resource
  r/authenticated-base
  :allowed-methods [:get]
  :available-media-types ["text/plain"]
  :handle-ok "Come on in. You're authenticated.")

;; ## Compojure Routes

(defroutes site-routes
  (GET "/" [] "Welcome to the liberator-friend demo site!")
  (GET "/admin" [] admin-resource)
  (GET "/authenticated" [] authenticated-resource)
  (GET "/user" [] user-resource))

(def site
  "Main handler for the example Compojure site."
  (-> site-routes
      (auth/friend-middleware users)
      (api)))
