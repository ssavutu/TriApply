(ns triapply.views.apply
  (:require [triapply.views.layout :as layout]))

(defn apply-page []
  (layout/page
   "Apply to The Triangle"

   [:div
    {:class "max-w-2xl mx-auto mt-12 bg-white p-8 rounded-xl shadow"}

    [:h1
     {:class "text-3xl font-bold mb-6"}
     "Apply to The Triangle"]

    [:form
     [:input
      {:class "w-full border rounded p-2 mb-4"
       :type "text"
       :placeholder "Name"}]]]))
