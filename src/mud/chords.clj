(ns mud.chords
  (:use [mud.core]
        [overtone.live]))

(defn chord-pattern
  "Write a pattern with list of notes to the buffers specified.
  Useful when creating chords and hence running multiple instances of synths
  with different buffers."
  [chord-bufs pattern]
  (let [chord-bufs (if (= :mud.chords/chord-group (type chord-bufs)) (:bufs chord-bufs) chord-bufs)]
    (dotimes [chord-idx (count chord-bufs)]
      (pattern! (nth chord-bufs chord-idx) (map #(if (> (count %1) chord-idx) (nth %1 chord-idx) 0) pattern))))
  pattern)

(def _chord-synth-buffer-cache_ (atom []))

(defn chord-synth [synth-name chord-size & args]
  "Create multiple instances of a synth so we can easily play chords"
  (let [chord-bufs (map (fn [_] (buffer 256 "chord note buf")) (range 0 chord-size))
        synth-instances (doall (map (fn [b] (apply synth-name (concat args [:note-buf b]))) chord-bufs))]
    (swap! _chord-synth-buffer-cache_ concat chord-bufs )
    (with-meta
      {:bufs chord-bufs :synths synth-instances}
      {:type ::chord-group})))

(defn stop-all-chord-synth-buffers [] (doseq [buf @_chord-synth-buffer-cache_] (buffer-free buf))
  (reset! _chord-synth-buffer-cache_ []))

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

(defn- chord->midi-notes [scale root current-chord]
  (let [current-chord (if (keyword? current-chord) (str (name current-chord)) current-chord)]
    (cond
     (and (not (integer? current-chord)) (re-find #"\*" current-chord))
     (let [[deg multipler] (clojure.string/split current-chord #"\*")
           multipler (Integer. (re-find  #"\d+" multipler))]
       (repeat multipler (chord->midi-notes scale root (keyword deg))))

     (or (integer? current-chord) (re-find #"^\d[abc]*$" current-chord))
     (let [[deg inversions] (if (integer? current-chord) [(str current-chord) "a"] (clojure.string/split current-chord #""))
           deg (Integer. (re-find  #"\d+" deg))
           invert (case inversions
                    nil nil
                    "a" nil
                    "b" [1]
                    "c" [1 2])]
       (if invert
         (nth (chords-with-inversion invert root scale 3) (- deg 1))
         (nth (chords-for root scale 3) (- deg 1))))

     :else
     (if (re-find #"[abc]+$" current-chord)
       (let [inversions  (str (last current-chord))
             chd (clojure.string/join (butlast current-chord))
             invert (case inversions
                      nil 0
                      "a" 0
                      "b" 1
                      "c" 2)]
         (chord root (keyword chd) invert))
       (chord root (keyword current-chord))))))

(defn root? [phrase]
  (not (if (integer? phrase)
         true
         (or (re-find #"sus|m\+5|m7\+5" (str (name phrase)))
             (re-find #"^\d" (str (name phrase)))))))

(defn- tokenise [in]
  "(tokenise )"
  (let [in (clojure.string/replace in "\n|\t" " ")
        multipliers (re-seq #"\[([^\]]+)\]\*(\d+)" in)
        multi-replacements (map (fn [[_ pattern multipler]] (map #(str %1 "*" multipler) (clojure.string/split pattern #"\s+"))) multipliers)
        _      (println :repl                (map vector multipliers multi-replacements))
        in (reduce
            (fn [accum [[original pattern multipler] replacement]]
              (clojure.string/replace accum original (clojure.string/join " " replacement)))
            in
            (map vector multipliers multi-replacements))]
    (println :pattern in)
    (clojure.string/split in #"\s+")))

(defn chords-seq
  "A concise way to express many chords.
  Example:
  (chords-seq :minor [:F2 :3c*2 :7sus4c*4])
  (chords-seq :minor [:F3 :m+5*8])
  (chords-seq :minor \"F3 [1c 2]*8 4b*6\")"
  [chords & [scale]]
  (if-not (string? chords)
    (reduce
     (fn [accu x] (if (sequential? (first x)) (apply conj accu (vec x)) (conj accu (vec x))))
     []
     (mapcat
      (fn [[[root] degs]]
        (let [scale (if (Character/isUpperCase (first root)) :major :minor)]
          (map #(chord->midi-notes scale root %) degs)))
      (partition 2 (partition-by root? chords))))
    (recur scale (tokenise chords))))
