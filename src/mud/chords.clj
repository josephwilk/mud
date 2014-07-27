(ns mud.chords
  (:use mud.core))

(defn chord-ctl
  "Like `ctl` but for chord groups (multiple synths)"
  [chord-group & args] (doseq [synth (:synths chord-group)] (apply ctl synth args)))

(defn chord-pattern
  "Write a pattern with list of notes to the buffers specified.
  Useful when creating chords and hence running multiple instances of synths
  with different buffers."
  [chord-bufs pattern]
  (let [chord-bufs (if (= :mud.chords/chord-group (type chord-bufs)) (:bufs chord-bufs) chord-bufs)]
    (dotimes [chord-idx (count chord-bufs)]
      (pattern! (nth chord-bufs chord-idx) (map #(if (> (count %1) chord-idx) (nth %1 chord-idx) 0) pattern))))
  pattern)

(def chord-synth-buffer-cache (atom []))

(defn chord-synth [synth-name chord-size & args]
  "Create multiple instances of a synth so we can easily play chords"
  (let [chord-bufs (map (fn [_] (buffer 256 "chord note buf")) (range 0 chord-size))
        synth-instances (doall (map (fn [b] (apply synth-name (concat args [:note-buf b]))) chord-bufs))]
    (swap! chord-synth-buffer-cache concat chord-bufs )
    (with-meta
      {:bufs chord-bufs :synths synth-instances}
      {:type ::chord-group})))

(defn stop-all-chord-synth-buffers [] (doseq [buf @chord-synth-buffer-cache] (buffer-free buf))
  (reset! chord-synth-buffer-cache []))

(defn note-in-chords
  "Fetch the `pos` note in every chord defined by `note` and `scale`"
  [pos note scale]
  (map #(nth % (dec pos)) (map (fn [d] (chord-degree d note scale 4)) [:i :ii :iii :iv :v :vi :vii])))

(defn chords-for
  "Fetch all midi notes for all chords in `scale` and octave `note`"
  ([note scale] (chords-for note scale 4))
  ([note scale no-notes] (map #(chord-degree %1 note scale no-notes) [:i :ii :iii :iv :v :vi :vii])))

(defn chords-with-inversion
  "Invert a chord"
  ([inversions note scale no-notes] (chords-with-inversion inversions note scale :up 3))
  ([inversions note scale dir no-notes]
     (let [offset (case dir
                    :up 12
                    :down -12)]
       (map (fn [m]
              (reduce
               (fn [new-m inversion]
                 (assoc-in (vec new-m) [(dec inversion)] (+ offset (nth m (dec inversion)))))
               (vec m)
               inversions))
            (chords-for note scale no-notes)))))
