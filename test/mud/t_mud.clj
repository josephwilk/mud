(ns mud.t-mud
  (:use [mud.core][mud.chords][midje.sweet]
        [overtone.music.pitch]))

(facts "degrees-seq"
  (fact "as a list or string"
    (let [expected-degrees (concat
                            (degrees->pitches [:i :iii] :minor :F4)
                            (degrees->pitches [:vi :vii] :minor :A3)
                            (degrees->pitches [:vii :vii] :minor :F3))]

      (degrees-seq [:f4 1 3 :a3 6 7 :f3 7 7] :minor) => expected-degrees
      (degrees-seq [:f4 13 :a3 67 :f3 77] :minor)    =>  expected-degrees
      (degrees-seq "f4 13 a3 67 f3 77")              => expected-degrees))

  (fact "case indicating major/minor"
    (degrees-seq [:F4 1 3 :A3 6 7 :f3 7 7]) => (concat
                                                (degrees->pitches [:i :iii] :major :F4)
                                                (degrees->pitches [:vi :vii] :major :A3)
                                                (degrees->pitches [:vii :vii] :minor :F3)))

  (future-fact "Duplicate degrees with []*n"
    (degrees-seq "f2[1 2 4]*2") => (degrees->pitches [:i :i :ii :ii :iv :iv] :minor :F2)))

(facts "chords-seq"
  (fact "with pattern as a list"
    (chords-seq [:B2 :3 "3" 3]) => [(chord-degree :iii :B2 :major 3) (chord-degree :iii :B2 :major 3) (chord-degree :iii :B2 :major 3)])

  (fact "with pattern as a string"
    (chords-seq "B2 3 3 3") => [(chord-degree :iii :B2 :major 3) (chord-degree :iii :B2 :major 3) (chord-degree :iii :B2 :major 3)])

  (fact "with a pattern with multiplers"
    (chords-seq [:B2 :3*2 :7sus4*3]) => [(chord-degree :iii :B2 :major 3)
                                         (chord-degree :iii :B2 :major 3)
                                         (chord :B2 :7sus4)
                                         (chord :B2 :7sus4)
                                         (chord :B2 :7sus4)]

    (chords-seq [:b3 :m+5*4]) => [(chord :b3 :m+5) (chord :b3 :m+5) (chord :b3 :m+5) (chord :b3 :m+5)]

    (chords-seq "b3[1c 2]*2 4b*3") => [(invert-chord (chord-degree :i :b3 :minor 3) 3)
                                       (invert-chord (chord-degree :i :b3 :minor 3) 3)
                                       (chord-degree :ii :b3 :minor 3) (chord-degree :ii :b3 :minor 3)
                                       (nth (chords-with-inversion [1 2] :b3 :minor 3) 3)
                                       (nth (chords-with-inversion [1 2] :b3 :minor 3) 3)
                                       (nth (chords-with-inversion [1 2] :b3 :minor 3) 3)])

  (fact "squished [] patterns"
    (map
     sort
     (chords-seq "b2[1c2b3sus4b]*2")) => [(invert-chord (chord-degree :i :b2 :minor 3) 3) (invert-chord (chord-degree :i :b2 :minor 3) 3)
                                          (invert-chord (chord-degree :ii :b2 :minor 3) 2) (invert-chord (chord-degree :ii :b2 :minor 3) 2)
                                          (invert-chord (chord-degree :iii :b2 :minor 3) 0) (invert-chord (chord-degree :iii :b2 :minor 3) 0)
                                          (invert-chord (chord :b2 :sus4) 2) (invert-chord (chord :b2 :sus4) 2)])

  (fact "with a pattern with funky chords"
    (chords-seq "b2 sus4c 7sus4b m+5a m7+5b") => [(invert-chord (chord :b2 :sus4) 3)
                                                  (invert-chord (chord :b2 :7sus4) 2)
                                                  (invert-chord (chord :b2 :m+5) 1)
                                                  (invert-chord (chord :b2 :m7+5) 2)])

  (fact "repeat `[]` without any arity"
    (chords-seq "f2 [1a 2b]") => (chords-seq "f2 [1a 2b]*1"))
)

(fact "chords with inversions"
  (nth (chords-with-inversion [] :b3 :minor 3) 0) =>  (chord-degree :i :b3 :minor 3)

  ;;Should we sort within chords-with-inversions?
  (sort (nth (chords-with-inversion [1]     :b3 :minor 3) 0)) => (invert-chord (chord-degree :i :b3 :minor 3) 1)
  (sort (nth (chords-with-inversion [1 2]   :b3 :minor 3) 0)) => (invert-chord (chord-degree :i :b3 :minor 3) 2)
  (sort (nth (chords-with-inversion [1 2 3] :b3 :minor 3) 0)) => (invert-chord (chord-degree :i :b3 :minor 3) 3))
