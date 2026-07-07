(ns triapply-portal.views.layout
  (:require [hiccup2.core :as h]))

(defn page [title & content]
  (str "<!doctype html>"
   (h/html
    [:html {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1"}]
      [:title title]
      [:link {:rel "icon" :type "image/x-icon" :href "/favicon.ico"}]
      [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
      [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin true}]
      [:link {:rel "stylesheet"
              :href "https://fonts.googleapis.com/css2?family=Crimson+Text:wght@400;600;700&family=Libre+Franklin:ital,wght@0,100..900;1,100..900&family=Playfair+Display:wght@400;600;700&family=Roboto+Slab:wght@400;700;800&display=swap"}]
      [:link {:rel "stylesheet" :href "/css/site.css"}]]
     (into
      [:body {:class "min-h-screen bg-[#f8fbff] text-triangleDark font-crimson text-[18px]"}]
      content)])))
