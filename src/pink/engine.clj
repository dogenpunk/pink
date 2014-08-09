(ns pink.engine
  "Audio Engine Code"
  (:require [pink.config :refer :all]
            [pink.util :refer :all])
  (:import (java.nio ByteBuffer)
           (java.util Arrays)
           (javax.sound.sampled AudioFormat AudioSystem SourceDataLine)))


(def buffer-size 256)
(def write-buffer-size (/ buffer-size 2))
(def frames (quot write-buffer-size *ksmps*))

(defmacro limit [num]
  `(if (> ~(tag-double num) Short/MAX_VALUE)
    Short/MAX_VALUE 
    (if (< ~(tag-double num) Short/MIN_VALUE)
      Short/MIN_VALUE 
      (short ~num))))

;;;; Engine

(def engines (ref []))

(defn engine-create 
  "Creates an audio engine"
  [& {:keys [sample-rate nchnls block-size] 
      :or {sample-rate 44100 nchnls 1 block-size 64}}] 
  (let  [e {:status (ref :stopped)
            :clear (ref false)
            :audio-funcs (ref [])
            :pending-funcs (ref [])
            :sample-rate sample-rate
            :nchnls nchnls
            :ksmps block-size 
            }]
    (dosync (alter engines conj e))
    e))

;; should do initialization of f on separate thread?
(defn engine-add-afunc [engine f]
  (dosync (alter (engine :pending-funcs) conj f)))

(defn engine-remove-afunc [engine f]
  (println "removing audio function")) 

;;;; JAVASOUND CODE

(defn open-line [audio-format]
  (let [#^SourceDataLine line (AudioSystem/getSourceDataLine audio-format)]
    (doto line 
    (.open audio-format)
    (.start))))

(defmacro doubles->byte-buffer 
  "Write output from doubles array into ByteBuffer"
  [dbls buf]
  `(let [len# (alength ~dbls)]
    (loop [y# 0]
      (when (< y# len#)
        (.putShort ~buf (limit (* Short/MAX_VALUE (aget ~dbls y#))))  
        (recur (unchecked-inc y#))))))

(defmacro run-audio-funcs [afs buffer]
  `(loop [[x# & xs#] ~afs 
          ret# []]
    (if x# 
      (let [b# (x#)]
        (if b#
          (do 
            (map-d ~buffer + b# ~buffer)
            (recur xs# (conj ret# x#)))
          (recur xs# ret#)))
     ret#))) 


(defn process-buffer
  [afs ^doubles outbuffer ^ByteBuffer buffer]
  (Arrays/fill ^doubles outbuffer 0.0)
  (let [newfs (run-audio-funcs afs outbuffer)]
    (doubles->byte-buffer outbuffer buffer)
    newfs))

(defn buf->line [^ByteBuffer buffer ^SourceDataLine line]
  (.write line (.array buffer) 0 buffer-size)
  (.clear buffer))

(defn engine-run [engine]
  (let [af (AudioFormat. (:sample-rate engine) 16 (:nchnls engine) true true)
        #^SourceDataLine line (open-line af)        
        outbuf (double-array *ksmps*)
        buf (ByteBuffer/allocate buffer-size)
        audio-funcs (engine :audio-funcs)
        pending-funcs (engine :pending-funcs)
        clear-flag (engine :clear)
        bufnum (atom -1)
        ]
    (loop [frame-count 0]
      (if (= @(engine :status) :running)
        (let [f-count (rem (inc frame-count) frames)
              afs  (binding [*current-buffer-num* 
                             (swap! bufnum unchecked-inc-int)]
                (process-buffer @audio-funcs outbuf buf))]  
          (dosync
            (if @clear-flag
              (do
                (ref-set audio-funcs [])
                (ref-set pending-funcs [])
                (ref-set clear-flag false))
              (if (empty? @pending-funcs)
                (ref-set audio-funcs afs)
                (do
                  (ref-set audio-funcs (concat afs @pending-funcs))
                  (ref-set pending-funcs [])))))
          (when (zero? f-count)
            (buf->line buf line))
          (recur (long f-count)))
        (do
          (println "stopping...")
          (doto line
            (.flush)
            (.close)))))))

(defn engine-start [engine]
  (when (= @(engine :status) :stopped)
    (dosync (ref-set (engine :status) :running))
    (.start (Thread. ^Runnable (partial engine-run engine)))))

(defn engine-stop [engine]
  (when (= @(engine :status) :running)
    (dosync (ref-set (engine :status) :stopped))))

(defn engine-clear [engine]
  (if (= @(engine :status) :running)
    (dosync 
      (ref-set (engine :clear) true))
    (dosync 
      (ref-set (engine :audio-funcs) [])
      (ref-set (engine :pending-funcs) []))))
    
                            
(defn engine-status [engine]
  @(:status engine))

(defn engine-kill-all
  "Kills all engines and clears them"
  []
  (dosync
    (loop [[a & b] @engines]
      (when a
        (engine-clear a)
        (recur b)
        ))))

(defn engines-clear
  "Kills all engines and clears global engines list. Useful for development in REPL, but user must be 
  careful after clearing not to use existing engines."
  []
  (engine-kill-all)
  (dosync (ref-set engines [])))

(defn run-audio-block 
  "TODO: Fix this function and document..."
  [a-block & {:keys [sample-rate nchnls block-size] 
              :or {sample-rate 44100 nchnls 1 block-size 64}}]
  (let [af (AudioFormat. sample-rate 16 nchnls true true)
        #^SourceDataLine line (open-line af) 
        buffer (ByteBuffer/allocate buffer-size)
        write-buffer-size (/ buffer-size 2)
        frames (quot write-buffer-size *ksmps*)]
    (loop [x 0]
      (if (< x frames)
        (if-let [buf ^doubles (a-block)]
          (do
            (doubles->byte-buffer buf buffer)
            (recur (unchecked-inc x)))
          (do
            (.write line (.array buffer) 0 buffer-size)
            (.clear buffer)))
        (do
          (.write line (.array buffer) 0 buffer-size)
          (.clear buffer)
          (recur 0))))
    (.flush line)
    (.close line)))


;; javasound stuff

(defn print-java-sound-info
  []
  (let [mixers (AudioSystem/getMixerInfo)
        cnt (alength mixers)]
   
    (println "Mixers Found: " cnt)
    (loop [indx 0]
      (when (< indx cnt)
        (let [mixer ^Mixer$Info (aget mixers indx)] 
          (println "Mixer " indx " :" mixer)
          (recur (unchecked-inc-int indx))
          )))))

;(print-java-sound-info)
