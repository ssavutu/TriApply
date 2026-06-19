(ns triapply.views.layout
  (:require [hiccup2.core :as h]))

(defn page [title & content]
  (str
   (h/html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1"}]
      [:title title]
      [:script {:src "https://cdn.tailwindcss.com"}]]
     (into
      [:body {:class "min-h-screen bg-slate-50 text-slate-950"}]
      content)])))
