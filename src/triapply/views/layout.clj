(ns triapply.views.layout
  (:require [hiccup2.core :as h]))

(defn page [title & content]
  (str
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
      [:script
       (h/raw
        "tailwind.config = {
           theme: {
             extend: {
               fontFamily: {
                 crimson: ['Crimson Text', 'serif'],
                 'roboto-slab': ['Roboto Slab', 'serif'],
                 playfair: ['Playfair Display', 'serif'],
                 libre: ['Libre Franklin', 'sans-serif']
               },
               colors: {
                 triangleBlue: '#2563EB',
                 triangleGray: '#E5E7EB',
                 triangleDark: '#1F2937',
                 triangleDanger: '#b4041e',
                 triangleSecondary: '#0C0A3E'
               }
             }
           }
         };")]
      [:style
       (h/raw
        ".font-crimson {
           font-family: 'Crimson Text', serif;
         }

         .font-roboto-slab {
           font-family: 'Roboto Slab', serif;
         }

         .font-playfair {
           font-family: 'Playfair Display', serif;
         }

         .font-libre {
           font-family: 'Libre Franklin', sans-serif;
         }

         .triangle-checkbox {
           appearance: none;
           width: 1.25rem;
           height: 1.25rem;
           display: inline-grid;
           place-content: center;
           border: 1px solid #2563EB;
           border-radius: 0.25rem;
           background: #fff;
           color: #2563EB;
         }

         .triangle-checkbox::before {
           content: '✓';
           font-size: 0.875rem;
           font-weight: 400;
           line-height: 1;
           transform: scale(0);
         }

         .triangle-checkbox:checked {
           background: #fff;
         }

         .triangle-checkbox:checked::before {
           transform: scale(1);
         }

         .triangle-checkbox:focus {
           outline: 2px solid rgba(37, 99, 235, 0.35);
           outline-offset: 2px;
         }")]
      [:script {:src "https://cdn.tailwindcss.com"}]]
     (into
      [:body {:class "min-h-screen bg-[#f8fbff] text-triangleDark font-crimson text-[18px]"}]
      content)])))
