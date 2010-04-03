(ns ccw.compile)

(defn all []
  (dorun   
    (map
      compile
      ['ccw.reload-clojure
       'ccw.ClojureProjectNature
       'ccw.debug.clientrepl
       'ccw.debug.serverrepl
       'ccw.editors.antlrbased.PareditAutoEditStrategy
       'ccw.editors.antlrbased.ClojureFormat
       'ccw.editors.antlrbased.StacktraceHyperlink
       'ccw.editors.antlrbased.ExpandSelectionUpAction
       'ccw.editors.antlrbased.ExpandSelectionLeftAction
       'ccw.editors.antlrbased.ExpandSelectionRightAction])))

