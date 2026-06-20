(ns triapply.views.apply
  (:require
   [hiccup2.core :as h]
   [triapply.views.layout :as layout]))

(def section-options
  [["news-writing" "News Writing"]
   ["opinion-writing" "Opinion Writing"]
   ["arts-entertainment" "Arts & Entertainment Writing"]
   ["sports-writing" "Sports Writing"]
   ["comics-puzzles" "Comics Writing and Puzzles"]
   ["photography" "Photography"]
   ["graphic-design" "Graphic Design"]
   ["copy-editing" "Copy Editing"]
   ["distribution" "Distribution of Newspapers"]
   ["business-marketing" "Business and Marketing"]
   ["it-team" "IT Team"]
   ["video-creation" "Video Creation"]])

(def google-field-class
  "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0")

(def file-field-class
  "mt-3 block w-full text-sm text-triangleDark file:mr-4 file:rounded-md file:border-0 file:bg-triangleBlue/10 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-triangleBlue hover:file:bg-triangleBlue/15")

(def card-class
  "max-w-2xl mx-auto bg-white rounded-xl border border-blue-600 p-8 sm:p-10 font-roboto-slab")

(def first-card-class
  (str card-class " mt-10"))

(def stacked-card-class
  (str card-class " mt-8"))

(def conditional-card-class
  (str "supplemental-section hidden " stacked-card-class " border-l-4 space-y-4"))

(def card-heading-class
  "inline-block font-roboto-slab text-2xl font-extrabold text-triangleDark border-b border-blue-600 pb-2")

(defn section-checkbox
  [[value label]]
  [:label {:class "flex items-center gap-3 text-base text-triangleDark"}
   [:input {:class "section-interest triangle-checkbox disabled:opacity-40"
            :type "checkbox"
            :name "section-interest"
            :value value}]
   label])

(defn apply-page []
  (layout/page
   "Apply to The Triangle"
   [:header {:class "mx-auto mt-10 max-w-2xl border-b-2 border-black pb-5 text-center"}
    [:img {:class "mx-auto w-full max-w-md"
           :src "/triangle-masthead.svg"
           :alt "The Triangle"}]]
   [:div {:class first-card-class}
    [:h1 {:class "font-playfair text-4xl font-bold mb-4"}
     "Triangle Spring 2026 Application"]
    [:p {:class "text-triangleDark/80"}
     "This application cycle will be from April 5th to April 19th.
      Please apply at your earliest convenience possible in the Spring Term,
      and we will determine the result of your application as soon as possible. "]
    [:br]
    [:h4 {:class "font-roboto-slab text-1xl font-extrabold uppercase mb-4"}
     "Application Process:"]
    [:ol {:class "list-decimal pl-5 space-y-2 text-triangleDark/80"}
     [:li "Submit your application materials"]
     [:li "Answer some short application questions"]
     [:li "Supply supplemental application materials if required (depends on position)"]
     [:li "Acceptance email with onboarding information or rejection email will be sent within two weeks of application"]]
    [:br]
    [:p {:class "text-triangleDark/80 italic"}
     "Note: If you have any issues or questions, you should contact the Staff Manager as soon as possible
      at apply@thetriangle.org."]
    [:br]
    [:h4 {:class "font-roboto-slab text-1xl font-extrabold uppercase mb-4"}
     "Expectations of The Triangle:"]
    [:p {:class "text-triangleDark/80"}
     "Actively contribute to The Triangle each term. Each section will have its own expectations,
      explained by your editor once accepted. Keep in mind, regardless of your assigned section,
      you are able to contribute to other sections that interest you."]
    [:br]
    [:h4 {:class "font-roboto-slab text-1xl font-extrabold uppercase mb-4"}
     "A few examples of current expectations for members include:"]
    [:ul {:class "list-decimal pl-5 space-y-2 text-triangleDark/80"}
     [:li "Writers: Writing and submitting an article that is published in The Triangle"]
     [:li "Photographers: Attending one event as the photographer,
           taking at least one photo that is used in a Triangle publication,
           and recording video content"]
     [:li "Copy Editors: Copy-editing three articles "]
     [:li "Graphic Design: Illustrating or helping illustrate one comic or graphic,
           or creating graphics for Instagram"]
     [:li "Video Editors: Assists in all aspects of video creation"]
     [:li "Helping with one print distribution cycle"]
     [:li "Help maintain a professional office environment and improve the newspaper"]
     [:li "Uphold journalistic standards and report only the truth"]
     [:li "Abide by the Bylaws set forth in the Triangle constitution"]]
    [:br]
    [:h4 {:class "font-roboto-slab text-1xl font-extrabold uppercase mb-4"}
     "General Body Meetings"]
    [:p {:class "text-triangleDark/80"}
     "The Triangle holds GBMs every Wednesday throughout the term at 6:30 p.m. at The Triangle
      Office, located at Suite 050 in the basement of Creese Student Center. As a member, you are
      expected to attend these meetings. If you have conflicts that do not allow you to make it to these
      meetings during the term (e.g., class, sports practices, rehearsals), please inform your section editor
      in advance of the meeting."]]
   [:form {:action "/submit-form-endpoint"
           :method "post"
           :enctype "multipart/form-data"
           :class "font-roboto-slab"}
    [:div {:class first-card-class}
     [:h2 {:class (str "mt-2 " card-heading-class)}
      "Tell us about yourself"]
     [:div {:class "mt-8 grid gap-x-6 gap-y-7 sm:grid-cols-2"}
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "name"}
        "Name"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "name"
                :name "name"
                :placeholder "J. Doe"
                :required true}]]
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "pronouns"} "Pronouns"]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "pronouns"
                :name "pronouns"
                :placeholder "They/Them"
                :required false}]]
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "birthdate"}
        "Birthdate"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "date"
                :id "birthdate"
                :name "birthdate"
                :required true}]]
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "phone-number"}
        "Phone Number"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "phone-number"
                :name "phone-number"
                :placeholder "8675309"
                :required true}]]
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "email"}
        "Drexel Email"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "email"
                :name "email"
                :placeholder "abc123@drexel.edu"
                :required true}]]
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "major"}
        "Major"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "major"
                :name "major"
                :placeholder "Journalism"
                :required true}]]
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "minor-con"} "Minor/Concentration"]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "minor-con"
                :name "minor-con"
                :placeholder "Creative Writing"
                :required false}]]
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "grad-year"}
        "Year of Graduation"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "grad-year"
                :name "grad-year"
                :placeholder "2029"
                :required true}]]
      [:div
       [:label {:class "block text-base font-medium text-triangleDark" :for "coop-cycle"}
        "Co-op Cycle"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "coop-cycle"
                :name "coop-cycle"
                :placeholder "Fall/Winter"
                :required true}]]
      [:div {:class "sm:col-span-2"}
       [:label {:class "block text-base font-medium text-triangleDark" :for "heard-from"}
        "How did you learn about The Triangle?"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "heard-from"
                :name "heard-from"
                :placeholder "I saw it in a dream"
                :required true}]]
      [:div {:class "sm:col-span-2"}
       [:label {:class "block text-base font-medium text-triangleDark" :for "other-clubs"}
        "What other clubs or organizations do you plan to be in?"
        [:span {:class "ml-1 text-red-600"} "*"]]
       [:input {:class "mt-3 block w-full border-0 border-b border-triangleGray bg-transparent px-0 py-2 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                :type "text"
                :id "other-clubs"
                :name "other-clubs"
                :placeholder "WKDU, TechServ"
                :required true}]]
      [:div {:class "sm:col-span-2"}
       [:fieldset
        [:legend {:class "text-base font-medium text-triangleDark"}
         "Do you have any experience with newspaper production or journalism?"
         [:span {:class "ml-1 text-red-600"} "*"]]
        [:div {:class "mt-4 space-y-4"}
         [:label {:class "flex items-center gap-3 text-base text-triangleDark"}
          [:input {:class "h-5 w-5 border-triangleGray text-triangleBlue focus:ring-triangleBlue"
                   :type "radio"
                   :id "prev-experience-yes"
                   :name "prev-experience"
                   :value "yes"
                   :required true}]
          "Yes"]
         [:label {:class "flex items-center gap-3 text-base text-triangleDark"}
          [:input {:class "h-5 w-5 border-triangleGray text-triangleBlue focus:ring-triangleBlue"
                   :type "radio"
                   :id "prev-experience-no"
                   :name "prev-experience"
                   :value "no"}]
          "No"]
         [:label {:class "flex items-center gap-3 text-base text-triangleDark"}
          [:input {:class "h-5 w-5 border-triangleGray text-triangleBlue focus:ring-triangleBlue"
                   :type "radio"
                   :id "prev-experience-other"
                   :name "prev-experience"
                   :value "other"}]
          [:span "Other:"]
          [:input {:class "block flex-1 border-0 border-b border-triangleGray bg-transparent px-0 py-1 text-base text-triangleDark placeholder:text-triangleDark/40 focus:border-triangleBlue focus:outline-none focus:ring-0"
                   :type "text"
                   :id "prev-experience-other-text"
                   :name "prev-experience-other"
                   :placeholder "I was an investigative journalist for The Onion"
                   :aria-label "Other previous experience"}]]]]]]]

    [:div {:class stacked-card-class}
     [:fieldset
      [:legend {:class card-heading-class}
       "Pick your top 5 sections"
       [:span {:class "ml-1 text-red-600"} "*"]]
      [:p {:class "mt-2 text-sm text-triangleDark/60"}
       "Choose up to 5 areas you are most interested in. These choices are non-binding."]
      [:p {:id "section-count"
           :class "mt-4 text-sm font-medium text-triangleDark/80"}
       "0 of 5 selected"]
      (into
       [:div {:class "mt-5 grid gap-4 sm:grid-cols-2"}]
       (map section-checkbox section-options))]]

    [:div {:class stacked-card-class}
     [:div
      [:label {:class "block text-base font-medium text-triangleDark" :for "top-two"}
       "For maximum clarity, please write out your top-two preferred sections you wish to join."
       [:span {:class "ml-1 text-red-600"} "*"]]
      [:input {:class google-field-class
               :type "text"
               :id "top-two"
               :name "top-two"
               :placeholder "In order of preference, I wish to join News Writing and Opinion Writing"
               :required true}]]]

    [:div {:class (str "supplemental-empty " stacked-card-class)}
     [:h2 {:class card-heading-class}
      "Supplemental materials"]
     [:p {:class "mt-2 text-sm text-triangleDark/60"}
      "Select sections above to see any required supplemental materials."]]

    [:section {:class conditional-card-class
               :data-supplemental "writing"}
     [:h2 {:class card-heading-class} "Writing sample"]
     [:p {:class "text-sm leading-6 text-triangleDark/80"}
      "Please complete one of the writing prompts below. Answers to the prompts should be submitted in a separate PDF and be at least one page in length. If you are applying to multiple writing sections, write only one prompt for your top choice."]
     [:ol {:class "list-decimal space-y-2 pl-5 text-sm leading-6 text-triangleDark/80"}
      [:li "Write a news story about a current event that happened in Philadelphia."]
      [:li "Write a review. It could be a movie review, food review, TV review, or a review of anything that sparks your interest."]
      [:li "Write or supply something you have already written that pertains to news, opinion, arts and entertainment, or sports."]]
     [:br]
     [:p {:class "text-sm leading-6 text-triangleDark/80"}
      " If you chose or are interested in comics/humor writing, write something funny. It does not have to be an article. It could be a top 10 list, horoscope, an actual comic, or something we haven't thought of."]
     [:label {:class "block text-base font-medium text-triangleDark" :for "writing-samples"}
      "Upload writing sample PDF"
      [:span {:class "ml-1 text-red-600"} "*"]]
     [:input {:class file-field-class
              :type "file"
              :id "writing-samples"
              :name "writing-samples"
              :accept "application/pdf"
              :multiple true
              :data-conditional-required true}]]

    [:section {:class conditional-card-class
               :data-supplemental "resume"}
     [:h2 {:class card-heading-class} "Resume"]
     [:p {:class "text-sm leading-6 text-triangleDark/80"}
      "Please attach your resume as a PDF."]
     [:label {:class "block text-base font-medium text-triangleDark" :for "resume"}
      "Upload resume PDF"
      [:span {:class "ml-1 text-red-600"} "*"]]
     [:input {:class file-field-class
              :type "file"
              :id "resume"
              :name "resume"
              :accept "application/pdf"
              :data-conditional-required true}]]

    [:section {:class conditional-card-class
               :data-supplemental "portfolio"}
     [:h2 {:class card-heading-class} "Portfolio samples"]
     [:p {:class "text-sm leading-6 text-triangleDark/80"}
      "If you selected Photography, Graphic Design, or Video Creation, please provide a few samples of your work as a portfolio in PDF form, or add a link if you have a website instead. The pieces you choose should represent your abilities as an artist."]
     [:label {:class "block text-base font-medium text-triangleDark" :for "portfolio-files"}
      "Upload portfolio PDFs"]
     [:input {:class file-field-class
              :type "file"
              :id "portfolio-files"
              :name "portfolio-files"
              :accept "application/pdf"
              :multiple true
              :data-conditional-required true}]
     [:label {:class "block text-base font-medium text-triangleDark" :for "portfolio-link"}
      "Portfolio site"
      [:span {:class "ml-1 text-red-600"} "*"]]
     [:input {:class google-field-class
              :type "url"
              :id "portfolio-link"
              :name "portfolio-link"
              :placeholder "https://mycoolphotos.net"
              :data-conditional-required true}]]

    [:section {:class conditional-card-class
               :data-supplemental "graphic-design"}
     [:label {:class "block text-base font-medium text-triangleDark" :for "graphic-design-experience"}
      "If you selected Graphic Design, what programs do you know and what experience do you have?"
      [:span {:class "ml-1 text-red-600"} "*"]]
     [:textarea {:class (str google-field-class " min-h-28 resize-y")
                 :id "graphic-design-experience"
                 :name "graphic-design-experience"
                 :rows 4
                 :data-conditional-required true}]]

    [:section {:class conditional-card-class
               :data-supplemental "field-experience"}
     [:label {:class "block text-base font-medium text-triangleDark" :for "field-experience"}
      "If you selected IT Team or Business and Marketing, do you have any experience in this field?"
      [:span {:class "ml-1 text-red-600"} "*"]]
     [:textarea {:class (str google-field-class " min-h-28 resize-y")
                 :id "field-experience"
                 :name "field-experience"
                 :rows 4
                 :data-conditional-required true}]]

    [:script
     (h/raw
      "document.addEventListener('DOMContentLoaded', () => {
       const sectionLimit = 5;
       const sectionBoxes = Array.from(document.querySelectorAll('.section-interest'));
       const sectionCount = document.getElementById('section-count');
       const supplementalEmpty = document.querySelector('.supplemental-empty');
       const supplementalSections = Array.from(document.querySelectorAll('.supplemental-section'));

       const writingSections = ['news-writing', 'opinion-writing', 'arts-entertainment', 'sports-writing', 'comics-puzzles'];
       const portfolioSections = ['photography', 'graphic-design', 'video-creation'];
       const resumeSections = ['copy-editing', 'business-marketing', 'it-team'];
       
       function selectedValues() {
           return sectionBoxes.filter((box) => box.checked).map((box) => box.value);
       }

       function hasAny(selected, values) {
           return values.some((value) => selected.includes(value));
       }

       function setSupplementalVisibility(name, show) {
           const section = document.querySelector(`[data-supplemental='${name}']`);
           if (!section) return;

           if (show) {
               section.classList.remove('hidden');
           } else {
               section.classList.add('hidden');
           }

           section.querySelectorAll('[data-conditional-required]').forEach((field) => {
               field.required = show;
               if (!show) field.value = '';
           });
       }

       function updateSupplementalSections() {
           const selected = selectedValues();

           if (hasAny(selected, writingSections)) {
               setSupplementalVisibility('writing', true);
           } else {
               setSupplementalVisibility('writing', false);
           }

           if (hasAny(selected, portfolioSections)) {
               setSupplementalVisibility('portfolio', true);
           } else {
               setSupplementalVisibility('portfolio', false);
           }

           if (hasAny(selected, resumeSections)) {
               setSupplementalVisibility('resume', true);
           } else {
               setSupplementalVisibility('resume', false);
           }

           if (selected.includes('graphic-design')) {
               setSupplementalVisibility('graphic-design', true);
           } else {
               setSupplementalVisibility('graphic-design', false);
           }

           if (selected.includes('it-team') || selected.includes('business-marketing')) {
               setSupplementalVisibility('field-experience', true);
           } else {
               setSupplementalVisibility('field-experience', false);
           }

           const anyVisible = supplementalSections.some((section) => !section.classList.contains('hidden'));
           supplementalEmpty.classList.toggle('hidden', anyVisible);
       }

       function updateSectionLimit() {
           const selected = sectionBoxes.filter((box) => box.checked).length;
           sectionCount.textContent = `${selected} of ${sectionLimit} selected`;
           sectionBoxes[0].setCustomValidity(selected === 0 ? 'Pick at least one section.' : '');
           sectionBoxes.forEach((box) => {
               box.disabled = !box.checked && selected >= sectionLimit;
           });
           updateSupplementalSections();
       }
       
       sectionBoxes.forEach((box) => box.addEventListener('change', updateSectionLimit));
       updateSectionLimit();
       });")]]))
