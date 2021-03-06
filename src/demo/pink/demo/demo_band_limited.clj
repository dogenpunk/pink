(ns pink.demo.demo-band-limited
 (:require [pink.simple :refer :all]
             [pink.event :refer :all] 
             [pink.space :refer [pan]] 
             [pink.oscillators :refer :all]
             [pink.envelopes :refer [env xar adsr]]
             [pink.util :refer [mul sum let-s with-duration]]
             [pink.node :refer :all]
             [pink.filters :refer :all]
             [pink.delays :refer [adelay]]
             ))

(defn instr-saw
  [amp freq loc]
  (let-s [e (xar 0.01 1.0)] 
    (pan 
      (mul e
           (lpf18 (sum
                   (blit-saw freq)
                   (blit-saw (sum 0.873 freq))
                   (blit-saw (sum -0.95117 freq)))
                  (sum 4000 (mul e 2000)) 
                  0.6 0.1)
           ;(butterlp (blit-saw freq) 
           ;      (sum 100 (mul e 400)))
           
           )
      loc)))

(defn instr-square
  [amp freq loc]
  (let-s [e (xar 0.01 1.0)] 
    (pan 
      (mul e amp
           (butterlp (blit-square freq) 
                 (sum 100 (mul e 400))))
      loc)))

(defn instr-triangle
  [amp freq loc]
  (let-s [e (if (fn? amp) 
              amp
              (mul amp (env [0.0 0.0 0.1 1.0 3.0 1.0 0.1 0.0])))] 
    (->
      (blit-triangle freq) 
      ;(butterlp (sum 100 (mul e 400)))
      (mul e) 
      (pan loc))))

(defn vox-humana 
  [amp ^double freq ^double loc]
  (let  [pulse-freq (mul freq (sum 1.0004 (lfo 0.013 3.5 :triangle)))
         pulse-width (sum 0.625 (lfo 0.125 5.72 :triangle))
         saw-freq (mul freq (sum 1 (lfo 0.021 5.04 :triangle)))
         key-follow (+ 1 (Math/exp (/ (- freq 50.0) 10000.0))) ] 
    (let-s [e (if (fn? amp) 
                amp
                (mul amp (env [0.0 0.0 0.1 1.0 3.0 1.0 0.1 0.0])))] 
      (->
        (sum (blit-saw saw-freq) 
          (blit-pulse pulse-freq pulse-width)) 

        (butterlp (* key-follow 1986))
        (mul e 0.5) 
        (pan loc)))))


;(def a (instr-saw 0.1 440 0.0))
;(def b (blit-saw 440))
;(require '[no.disassemble :refer :all])
;(println (disassemble b))
;(require '[clojure.pprint :refer [pprint]])
;(pprint (a))

(comment

  (start-engine)

  (def root-node (audio-node :channels 2))
  (add-afunc root-node)

  ;(def root-node (create-node :channels 1))
  ;(def delayed-audio-node
  ;  (let-s [afn (node-processor root-node)]
  ;    (sum afn (adelay afn 0.25))))

  ;(engine-add-afunc e delayed-audio-node)

  (def my-score 
    (let [num-notes 10] 
      (node-events root-node 
                   (map #(event instr-saw (* % 0.25)  
                                (/ 0.75 (+ 1 %)) 
                                (* 220 (+ 1 %)) 
                                (- (* 2 (/ % (- num-notes 1))) 1)) 
                        (range num-notes)))))

  (add-events my-score) 

  (node-add-func
    root-node 
    (instr-saw 0.25 (env [0.0 220 0.1 200 0.0001 220 0.1 500]) 0.0))


  (def my-score2
    (let [num-notes 10] 
      (node-events root-node 
                   (map #(event instr-square (* % 0.5)  
                                (/ 0.75 (+ 1 %)) 
                                (* 65 (+ 1 %)) 
                                (- (* 2 (/ % (- num-notes 1)))  1)) 
                        (range num-notes)))))

  (add-events my-score2) 

  (node-add-func
    root-node 
    (instr-square 0.5 (env [0.0 200 0.05 40 0.4 40]) 0.0))


  (node-add-func 
    root-node
    (instr-square 0.5 440 0.0))

  (node-add-func 
    root-node
    (instr-triangle 0.5 1100 0.0))


  (add-afunc
    (with-duration 1.0
      (mul (adsr 0.01 0.0 1.0 2.0) 0.5
           (blit-triangle (env [0.0 200 4.0 800]) ))))

  (node-add-func 
    root-node
    (instr-triangle 0.5 
                    (env [0.0 200 4.0 800]) 0.0))

  (node-add-func 
    root-node
    (instr-triangle (mul 0.5 (xar 0.01 1.0)) (env [0.0 200 0.05 40 0.4 40]) 0.0))

  (add-afunc
    (with-duration 8.0
      (vox-humana (mul 0.5 (adsr 0.453 0.0 1.0 2.242)) 440 0.0)))
  
  (add-afunc
    (with-duration 8.0
      (vox-humana (mul 0.5 (adsr 0.453 0.0 1.0 2.242)) 880 0.0)))

  (def my-score3
    (let [num-notes 10] 
      (node-events root-node 
                   (map #(event instr-triangle (* % 0.5)  
                                (/ 0.75 (+ 1 %)) 
                                (* 65 (+ 1 %)) 
                                (- (* 2 (/ % (- num-notes 1)))  1)) 
                        (range num-notes)))))



  (add-events my-score3) 

  (stop-engine)


  )

