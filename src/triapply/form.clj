(ns triapply.form
  "Data-driven definition of the application form.

  This is the single source of truth the renderer and the client-side
  engine both derive from. Today it's a literal; a future editor can
  swap `default-form` for config read from EDN/a database without
  touching the rendering code.

  Prose lives as hiccup vectors under `:intro`; interactive inputs are
  structured `:fields` maps so the client engine can toggle their
  `required`/visibility generically.")

(def default-form
  {:section-limit 5

   :sections
   [{:value "news-writing" :label "News Writing"}
    {:value "opinion-writing" :label "Opinion Writing"}
    {:value "arts-entertainment" :label "Arts & Entertainment Writing"}
    {:value "sports-writing" :label "Sports Writing"}
    {:value "comics-puzzles" :label "Comics Writing and Puzzles"}
    {:value "photography" :label "Photography"}
    {:value "graphic-design" :label "Graphic Design"}
    {:value "copy-editing" :label "Copy Editing"}
    {:value "distribution" :label "Distribution of Newspapers"}
    {:value "business-marketing" :label "Business and Marketing"}
    {:value "it-team" :label "IT Team"}
    {:value "video-creation" :label "Video Creation"}]

   ;; Each supplemental is shown when ANY selected section is in `:triggers`.
   :supplementals
   [{:id "writing"
     :triggers #{"news-writing" "opinion-writing" "arts-entertainment"
                 "sports-writing" "comics-puzzles"}
     :title "Writing sample"
     :intro [[:p {:class "text-sm leading-6 text-triangleDark/80"}
              "Please complete one of the writing prompts below. Answers to the prompts should be submitted in a separate PDF and be at least one page in length. If you are applying to multiple writing sections, write only one prompt for your top choice."]
             [:ol {:class "list-decimal space-y-2 pl-5 text-sm leading-6 text-triangleDark/80"}
              [:li "Write a news story about a current event that happened in Philadelphia."]
              [:li "Write a review. It could be a movie review, food review, TV review, or a review of anything that sparks your interest."]
              [:li "Write or supply something you have already written that pertains to news, opinion, arts and entertainment, or sports."]]
             [:br]
             [:p {:class "text-sm leading-6 text-triangleDark/80"}
              " If you chose or are interested in comics/humor writing, write something funny. It does not have to be an article. It could be a top 10 list, horoscope, an actual comic, or something we haven't thought of."]]
     :fields [{:type :file
               :id "writing-samples"
               :name "writing-samples"
               :label "Upload writing sample PDF"
               :required? true
               :multiple true
               :accept "application/pdf"}]}

    {:id "resume"
     :triggers #{"copy-editing" "business-marketing" "it-team"}
     :title "Resume"
     :intro [[:p {:class "text-sm leading-6 text-triangleDark/80"}
              "Please attach your resume as a PDF."]]
     :fields [{:type :file
               :id "resume"
               :name "resume"
               :label "Upload resume PDF"
               :required? true
               :accept "application/pdf"}]}

    {:id "portfolio"
     :triggers #{"photography" "graphic-design" "video-creation"}
     :title "Portfolio samples"
     :intro [[:p {:class "text-sm leading-6 text-triangleDark/80"}
              "If you selected Photography, Graphic Design, or Video Creation, please provide a few samples of your work as a portfolio in PDF form, or add a link if you have a website instead. The pieces you choose should represent your abilities as an artist."]]
     :fields [{:type :file
               :id "portfolio-files"
               :name "portfolio-files"
               :label "Upload portfolio PDFs"
               :required? true
               :multiple true
               :accept "application/pdf"}
              {:type :url
               :id "portfolio-link"
               :name "portfolio-link"
               :label "Portfolio site"
               :required? true
               :placeholder "https://mycoolphotos.net"}]}

    {:id "graphic-design"
     :triggers #{"graphic-design"}
     :fields [{:type :textarea
               :id "graphic-design-experience"
               :name "graphic-design-experience"
               :label "If you selected Graphic Design, what programs do you know and what experience do you have?"
               :required? true
               :rows 4}]}

    {:id "field-experience"
     :triggers #{"it-team" "business-marketing"}
     :fields [{:type :textarea
               :id "field-experience"
               :name "field-experience"
               :label "If you selected IT Team or Business and Marketing, do you have any experience in this field?"
               :required? true
               :rows 4}]}]})
