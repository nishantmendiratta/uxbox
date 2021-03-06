;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2015-2017 Juan de la Cruz <delacruzgarciajuan@gmail.com>
;; Copyright (c) 2015-2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.main.ui.workspace
  (:require
   [beicon.core :as rx]
   [lentes.core :as l]
   [rumext.alpha :as mf]
   [uxbox.builtins.icons :as i]
   [uxbox.main.constants :as c]
   [uxbox.main.data.history :as udh]
   [uxbox.main.data.workspace :as dw]
   [uxbox.main.refs :as refs]
   [uxbox.main.store :as st]
   [uxbox.main.streams :as ms]
   [uxbox.main.ui.confirm]
   [uxbox.main.ui.keyboard :as kbd]
   [uxbox.main.ui.messages :refer [messages-widget]]
   [uxbox.main.ui.workspace.viewport :refer [viewport]]
   [uxbox.main.ui.workspace.colorpalette :refer [colorpalette]]
   [uxbox.main.ui.workspace.context-menu :refer [context-menu]]
   [uxbox.main.ui.workspace.header :refer [header]]
   [uxbox.main.ui.workspace.rules :refer [horizontal-rule vertical-rule]]
   [uxbox.main.ui.workspace.scroll :as scroll]
   [uxbox.main.ui.workspace.sidebar :refer [left-sidebar right-sidebar]]
   [uxbox.main.ui.workspace.sidebar.history :refer [history-dialog]]
   [uxbox.main.ui.workspace.left-toolbar :refer [left-toolbar]]
   [uxbox.util.data :refer [classnames]]
   [uxbox.util.dom :as dom]
   [uxbox.util.geom.point :as gpt]
   [uxbox.util.rdnd :as rdnd]))

;; --- Workspace

(defn- on-scroll
  [event]
  (let [target (.-target event)
        top (.-scrollTop target)
        left (.-scrollLeft target)]
    (st/emit! (ms/->ScrollEvent (gpt/point left top)))))

(defn- on-wheel
  [event frame]
  (when (kbd/ctrl? event)
    ;; global ctrl+wheel browser zoom must be disabled (see main/ui/workspace/wiewport.cljs)
    (let [dom (mf/ref-val frame)
          scroll-position (scroll/get-current-position-absolute dom)
          mouse-point @ms/mouse-position]
      (if (pos? (.-deltaY event))
        (st/emit! dw/decrease-zoom)
        (st/emit! dw/increase-zoom))
      (scroll/scroll-to-point dom mouse-point scroll-position))))

(mf/defc workspace-content
  [{:keys [page file layout] :as params}]
  (let [frame (mf/use-ref nil)
        left-sidebar? (not (empty? (keep layout [:layers :sitemap :document-history :libraries])))
        right-sidebar? (not (empty? (keep layout [:icons :drawtools :element-options])))
        classes (classnames
                 :no-tool-bar-right (not right-sidebar?)
                 :no-tool-bar-left (not left-sidebar?))]
    [:*
     (when (:colorpalette layout)
       [:& colorpalette {:left-sidebar? left-sidebar?}])

     [:main.main-content
      [:& context-menu {}]
      [:section.workspace-content
       {:class classes
        :on-scroll on-scroll
        :on-wheel #(on-wheel % frame)}

       [:& history-dialog]

       ;; Rules
       (when (contains? layout :rules)
         [:*
          [:& horizontal-rule]
          [:& vertical-rule]])

       [:section.workspace-viewport {:id "workspace-viewport"
                                     :ref frame}
        [:& viewport {:page page :file file}]]]

      [:& left-toolbar {:page page
                        :layout layout}]

      ;; Aside
      (when left-sidebar?
        [:& left-sidebar {:file file :page page :layout layout}])
      (when right-sidebar?
        [:& right-sidebar {:page page :layout layout}])]]))


(mf/defc workspace
  [{:keys [project-id file-id page-id] :as props}]

  (-> (mf/deps file-id page-id)
      (mf/use-effect
       (fn []
         (st/emit! (dw/initialize project-id file-id page-id))
         #(st/emit! (dw/finalize project-id file-id page-id)))))

  (-> (mf/deps file-id)
      (mf/use-effect
       (fn []
         (st/emit! (dw/initialize-ws file-id))
         #(st/emit! (dw/finalize-ws file-id)))))

  (-> (mf/deps file-id)
      (mf/use-effect
       (fn []
         (st/emit! dw/initialize-shortcuts)
         #(st/emit! ::dw/finalize-shortcuts))))

  (mf/use-effect #(st/emit! dw/initialize-layout))

  (let [file (mf/deref refs/workspace-file)
        page (mf/deref refs/workspace-page)
        layout (mf/deref refs/workspace-layout)]
    [:> rdnd/provider {:backend rdnd/html5}
     [:& messages-widget]
     [:& header {:page page
                 :file file
                 :layout layout}]

     (when page
       [:& workspace-content {:file file
                              :page page
                              :layout layout}])]))
