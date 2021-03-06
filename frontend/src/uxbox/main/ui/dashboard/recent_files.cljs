;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns uxbox.main.ui.dashboard.recent-files
  (:require
   [lentes.core :as l]
   [rumext.alpha :as mf]
   [uxbox.builtins.icons :as i]
   [uxbox.common.exceptions :as ex]
   [uxbox.main.constants :as c]
   [uxbox.main.data.dashboard :as dsh]
   [uxbox.main.store :as st]
   [uxbox.main.exports :as exports]
   [uxbox.main.refs :as refs]
   [uxbox.main.ui.modal :as modal]
   [uxbox.main.ui.keyboard :as kbd]
   [uxbox.main.ui.confirm :refer [confirm-dialog]]
   [uxbox.util.dom :as dom]
   [uxbox.util.i18n :as i18n :refer [t tr]]
   [uxbox.util.router :as rt]
   [uxbox.util.time :as dt]
   [uxbox.main.ui.dashboard.grid :refer [grid]])
  )

;; --- Component: Content

(def projects-ref
  (-> (l/key :projects)
      (l/derive st/state)))

(def recent-file-ids-ref
  (-> (l/key :recent-file-ids)
      (l/derive st/state)))

(def files-ref
  (-> (l/key :files)
      (l/derive st/state)))

;; --- Component: Recent files

(mf/defc recent-files-header
  [{:keys [profile] :as props}]
  (let [locale (i18n/use-locale)]
    [:header#main-bar.main-bar
     [:h1.dashboard-title "Recent"]
     [:a.btn-dashboard {:on-click #(st/emit! dsh/create-project)}
      (t locale "dashboard.header.new-project")]]))

(mf/defc recent-project
  [{:keys [project files first? locale] :as props}]
  (let [project-id (:id project)]
    [:div.recent-files-row
     {:class-name (when first? "first")}
     [:div.recent-files-row-title
      [:h2.recent-files-row-title-name (:name project)]
      [:span.recent-files-row-title-info (str (:file-count project) " files")]
      (let [time (-> (:modified-at project)
                     (dt/timeago {:locale locale}))]
        [:span.recent-files-row-title-info (str ", " time)])]
     [:& grid {:id (:id project)
               :files files
               :hide-new? true}]]))


(mf/defc recent-files-page
  [{:keys [team-id] :as props}]
  (let [projects (->> (mf/deref projects-ref)
                      (vals)
                      (sort-by :modified-at)
                      (reverse))
        files (mf/deref files-ref)
        recent-file-ids (mf/deref recent-file-ids-ref)
        locale (i18n/use-locale)
        setup  #(st/emit! (dsh/initialize-recent team-id))]

    (-> (mf/deps team-id)
        (mf/use-effect #(st/emit! (dsh/initialize-recent team-id))))

    (when (and projects recent-file-ids)
      [:*
       [:& recent-files-header]
       [:section.recent-files-page
        (for [project projects]
          [:& recent-project {:project project
                              :locale locale
                              :key (:id project)
                              :files (->> (get recent-file-ids (:id project))
                                          (map #(get files %))
                                          (filter identity)) ;; avoid failure if a "project only" files list is in global state
                              :first? (= project (first projects))}])]])))

