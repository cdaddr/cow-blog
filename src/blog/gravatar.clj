(ns blog.gravatar)

(defn md5sum
  "Calculates MD5 checksum for a string."
  [s]
  (let [md5 (doto (java.security.MessageDigest/getInstance "MD5")
              (.reset)
              (.update (.getBytes (str s))))]
    (.toString (BigInteger. 1 (.digest md5)) 16)))

(defn gravatar
  "Given a string, returns a gravatar URI."
  [x]
  (let [md5 (md5sum x)]
    (str "http://gravatar.com/avatar/" md5 ".jpg?d=identicon&s=60")))
