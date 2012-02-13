(ns clojure-gl.marching-cubes)


;; first an implementation of simplex noise (eg perlin noise)
;; based on the java reference implementation found at:
;; http://mrl.nyu.edu/~perlin/noise/

(def permutations
  [151 160 137 91 90 15 
   131 13 201 95 96 53 194 233 7 225 140 36 103 30 69 142 8 99 37 240 21 10 23 
   190  6 148 247 120 234 75 0 26 197 62 94 252 219 203 117 35 11 32 57 177 33 
   88 237 149 56 87 174 20 125 136 171 168  68 175 74 165 71 134 139 48 27 166 
   77 146 158 231 83 111 229 122 60 211 133 230 220 105 92 41 55 46 245 40 244 
   102 143 54  65 25 63 161  1 216 80 73 209 76 132 187 208  89 18 169 200 196 
   135 130 116 188 159 86 164 100 109 198 173 186  3 64 52 217 226 250 124 123 
   5 202 38 147 118 126 255 82 85 212 207 206 59 227 47 16 58 17 182 189 28 42 
   223 183 170 213 119 248 152  2 44 154 163  70 221 153 101 155 167  43 172 9 
   129 22 39 253  19 98 108 110 79 113 224 232 178 185  112 104 218 246 97 228 
   251 34 242 193 238 210 144 12 191 179 162 241  81 51 145 235 249 14 239 107 
   49 192 214  31 181 199 106 157 184  84 204 176 115 121 50 45 127  4 150 254 
   138 236 205 93 222 114 67 29 24 72 243 141 128 195 78 66 215 61 156 180
   151 160 137 91 90 15 
   131 13 201 95 96 53 194 233 7 225 140 36 103 30 69 142 8 99 37 240 21 10 23 
   190  6 148 247 120 234 75 0 26 197 62 94 252 219 203 117 35 11 32 57 177 33 
   88 237 149 56 87 174 20 125 136 171 168  68 175 74 165 71 134 139 48 27 166 
   77 146 158 231 83 111 229 122 60 211 133 230 220 105 92 41 55 46 245 40 244 
   102 143 54  65 25 63 161  1 216 80 73 209 76 132 187 208  89 18 169 200 196 
   135 130 116 188 159 86 164 100 109 198 173 186  3 64 52 217 226 250 124 123 
   5 202 38 147 118 126 255 82 85 212 207 206 59 227 47 16 58 17 182 189 28 42 
   223 183 170 213 119 248 152  2 44 154 163  70 221 153 101 155 167  43 172 9 
   129 22 39 253  19 98 108 110 79 113 224 232 178 185  112 104 218 246 97 228 
   251 34 242 193 238 210 144 12 191 179 162 241  81 51 145 235 249 14 239 107 
   49 192 214  31 181 199 106 157 184  84 204 176 115 121 50 45 127  4 150 254 
   138 236 205 93 222 114 67 29 24 72 243 141 128 195 78 66 215 61 156 180])

(defn fade [t]
  (* t t t (+ (* t (- (* t 6) 15))
              10)))

(defn lerp [t a b]
  (+ a (* t (- b a))))

(defn grad [hash x y z]
  (let [h (bit-and hash 15)
        u (if (< h 8) x y)
        v (if (< h 4)
            y
            (if (or (== h 12)
                    (== h 14))
              x
              z))]
    (+
     (if (== (bit-and h 1) 0) u (- u))
     (if (== (bit-and h 2) 0) v (- v)))))

(defn simplex-noise [x y z]
  (let [X (bit-and (int (Math/floor x)) 255)
        Y (bit-and (int (Math/floor y)) 255)
        Z (bit-and (int (Math/floor z)) 255)
        x (- x (Math/floor x))
        y (- y (Math/floor y))
        z (- z (Math/floor z))
        u (fade x)
        v (fade y)
        w (fade z)
        A (+ (permutations X) Y)
        AA (+ (permutations A) Z)
        AB (+ (permutations (+ A 1)) Z)
        B (+ (permutations (+ X 1)) Y)
        BA (+ (permutations B) Z)
        BB (+ (permutations (+ B 1)) Z)]
    
    (lerp w
          (lerp v
                (lerp u
                      (grad (permutations AA) x y z)
                      (grad (permutations BA) (- x 1) y z))
                (lerp u
                      (grad (permutations AB) x (- y 1) z)
                      (grad (permutations BB) (- x 1) (- y 1) z)))
          (lerp v
                (lerp u
                      (grad (permutations (+ AA 1)) x y (- z 1))
                      (grad (permutations (+ BA 1)) (- x 1) y (- z 1)))
                (lerp u
                      (grad (permutations (+ AB 1)) x (- y 1) (- z 1))
                      (grad (permutations (+ BB 1)) (- x 1) (- y 1) (- z 1)))))))



;; now the marching cubes algorithm
;; This is based on the C implementation found at:
;; http://paulbourke.net/geometry/polygonise/

(def edge-table
  [0x0    0x109  0x203  0x30a  0x406  0x50f  0x605  0x70c 
   0x80c  0x905  0xa0f  0xb06  0xc0a  0xd03  0xe09  0xf00 
   0x190  0x99   0x393  0x29a  0x596  0x49f  0x795  0x69c 
   0x99c  0x895  0xb9f  0xa96  0xd9a  0xc93  0xf99  0xe90 
   0x230  0x339  0x33   0x13a  0x636  0x73f  0x435  0x53c 
   0xa3c  0xb35  0x83f  0x936  0xe3a  0xf33  0xc39  0xd30 
   0x3a0  0x2a9  0x1a3  0xaa   0x7a6  0x6af  0x5a5  0x4ac 
   0xbac  0xaa5  0x9af  0x8a6  0xfaa  0xea3  0xda9  0xca0 
   0x460  0x569  0x663  0x76a  0x66   0x16f  0x265  0x36c 
   0xc6c  0xd65  0xe6f  0xf66  0x86a  0x963  0xa69  0xb60 
   0x5f0  0x4f9  0x7f3  0x6fa  0x1f6  0xff   0x3f5  0x2fc 
   0xdfc  0xcf5  0xfff  0xef6  0x9fa  0x8f3  0xbf9  0xaf0 
   0x650  0x759  0x453  0x55a  0x256  0x35f  0x55   0x15c 
   0xe5c  0xf55  0xc5f  0xd56  0xa5a  0xb53  0x859  0x950 
   0x7c0  0x6c9  0x5c3  0x4ca  0x3c6  0x2cf  0x1c5  0xcc  
   0xfcc  0xec5  0xdcf  0xcc6  0xbca  0xac3  0x9c9  0x8c0 
   0x8c0  0x9c9  0xac3  0xbca  0xcc6  0xdcf  0xec5  0xfcc 
   0xcc   0x1c5  0x2cf  0x3c6  0x4ca  0x5c3  0x6c9  0x7c0 
   0x950  0x859  0xb53  0xa5a  0xd56  0xc5f  0xf55  0xe5c 
   0x15c  0x55   0x35f  0x256  0x55a  0x453  0x759  0x650 
   0xaf0  0xbf9  0x8f3  0x9fa  0xef6  0xfff  0xcf5  0xdfc 
   0x2fc  0x3f5  0xff   0x1f6  0x6fa  0x7f3  0x4f9  0x5f0 
   0xb60  0xa69  0x963  0x86a  0xf66  0xe6f  0xd65  0xc6c 
   0x36c  0x265  0x16f  0x66   0x76a  0x663  0x569  0x460 
   0xca0  0xda9  0xea3  0xfaa  0x8a6  0x9af  0xaa5  0xbac 
   0x4ac  0x5a5  0x6af  0x7a6  0xaa   0x1a3  0x2a9  0x3a0 
   0xd30  0xc39  0xf33  0xe3a  0x936  0x83f  0xb35  0xa3c 
   0x53c  0x435  0x73f  0x636  0x13a  0x33   0x339  0x230 
   0xe90  0xf99  0xc93  0xd9a  0xa96  0xb9f  0x895  0x99c 
   0x69c  0x795  0x49f  0x596  0x29a  0x393  0x99   0x190 
   0xf00  0xe09  0xd03  0xc0a  0xb06  0xa0f  0x905  0x80c 
   0x70c  0x605  0x50f  0x406  0x30a  0x203  0x109  0x0])

(def tri-table
  [[-1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 8 3 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 1 9 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 8 3 9 8 1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 2 10 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 8 3 1 2 10 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [9 2 10 0 2 9 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [2 8 3 2 10 8 10 9 8 -1 -1 -1 -1 -1 -1 -1]
   [3 11 2 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 11 2 8 11 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 9 0 2 3 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 11 2 1 9 11 9 8 11 -1 -1 -1 -1 -1 -1 -1]
   [3 10 1 11 10 3 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 10 1 0 8 10 8 11 10 -1 -1 -1 -1 -1 -1 -1]
   [3 9 0 3 11 9 11 10 9 -1 -1 -1 -1 -1 -1 -1]
   [9 8 10 10 8 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 7 8 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 3 0 7 3 4 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 1 9 8 4 7 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 1 9 4 7 1 7 3 1 -1 -1 -1 -1 -1 -1 -1]
   [1 2 10 8 4 7 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [3 4 7 3 0 4 1 2 10 -1 -1 -1 -1 -1 -1 -1]
   [9 2 10 9 0 2 8 4 7 -1 -1 -1 -1 -1 -1 -1]
   [2 10 9 2 9 7 2 7 3 7 9 4 -1 -1 -1 -1]
   [8 4 7 3 11 2 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [11 4 7 11 2 4 2 0 4 -1 -1 -1 -1 -1 -1 -1]
   [9 0 1 8 4 7 2 3 11 -1 -1 -1 -1 -1 -1 -1]
   [4 7 11 9 4 11 9 11 2 9 2 1 -1 -1 -1 -1]
   [3 10 1 3 11 10 7 8 4 -1 -1 -1 -1 -1 -1 -1]
   [1 11 10 1 4 11 1 0 4 7 11 4 -1 -1 -1 -1]
   [4 7 8 9 0 11 9 11 10 11 0 3 -1 -1 -1 -1]
   [4 7 11 4 11 9 9 11 10 -1 -1 -1 -1 -1 -1 -1]
   [9 5 4 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [9 5 4 0 8 3 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 5 4 1 5 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [8 5 4 8 3 5 3 1 5 -1 -1 -1 -1 -1 -1 -1]
   [1 2 10 9 5 4 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [3 0 8 1 2 10 4 9 5 -1 -1 -1 -1 -1 -1 -1]
   [5 2 10 5 4 2 4 0 2 -1 -1 -1 -1 -1 -1 -1]
   [2 10 5 3 2 5 3 5 4 3 4 8 -1 -1 -1 -1]
   [9 5 4 2 3 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 11 2 0 8 11 4 9 5 -1 -1 -1 -1 -1 -1 -1]
   [0 5 4 0 1 5 2 3 11 -1 -1 -1 -1 -1 -1 -1]
   [2 1 5 2 5 8 2 8 11 4 8 5 -1 -1 -1 -1]
   [10 3 11 10 1 3 9 5 4 -1 -1 -1 -1 -1 -1 -1]
   [4 9 5 0 8 1 8 10 1 8 11 10 -1 -1 -1 -1]
   [5 4 0 5 0 11 5 11 10 11 0 3 -1 -1 -1 -1]
   [5 4 8 5 8 10 10 8 11 -1 -1 -1 -1 -1 -1 -1]
   [9 7 8 5 7 9 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [9 3 0 9 5 3 5 7 3 -1 -1 -1 -1 -1 -1 -1]
   [0 7 8 0 1 7 1 5 7 -1 -1 -1 -1 -1 -1 -1]
   [1 5 3 3 5 7 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [9 7 8 9 5 7 10 1 2 -1 -1 -1 -1 -1 -1 -1]
   [10 1 2 9 5 0 5 3 0 5 7 3 -1 -1 -1 -1]
   [8 0 2 8 2 5 8 5 7 10 5 2 -1 -1 -1 -1]
   [2 10 5 2 5 3 3 5 7 -1 -1 -1 -1 -1 -1 -1]
   [7 9 5 7 8 9 3 11 2 -1 -1 -1 -1 -1 -1 -1]
   [9 5 7 9 7 2 9 2 0 2 7 11 -1 -1 -1 -1]
   [2 3 11 0 1 8 1 7 8 1 5 7 -1 -1 -1 -1]
   [11 2 1 11 1 7 7 1 5 -1 -1 -1 -1 -1 -1 -1]
   [9 5 8 8 5 7 10 1 3 10 3 11 -1 -1 -1 -1]
   [5 7 0 5 0 9 7 11 0 1 0 10 11 10 0 -1]
   [11 10 0 11 0 3 10 5 0 8 0 7 5 7 0 -1]
   [11 10 5 7 11 5 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [10 6 5 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 8 3 5 10 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [9 0 1 5 10 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 8 3 1 9 8 5 10 6 -1 -1 -1 -1 -1 -1 -1]
   [1 6 5 2 6 1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 6 5 1 2 6 3 0 8 -1 -1 -1 -1 -1 -1 -1]
   [9 6 5 9 0 6 0 2 6 -1 -1 -1 -1 -1 -1 -1]
   [5 9 8 5 8 2 5 2 6 3 2 8 -1 -1 -1 -1]
   [2 3 11 10 6 5 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [11 0 8 11 2 0 10 6 5 -1 -1 -1 -1 -1 -1 -1]
   [0 1 9 2 3 11 5 10 6 -1 -1 -1 -1 -1 -1 -1]
   [5 10 6 1 9 2 9 11 2 9 8 11 -1 -1 -1 -1]
   [6 3 11 6 5 3 5 1 3 -1 -1 -1 -1 -1 -1 -1]
   [0 8 11 0 11 5 0 5 1 5 11 6 -1 -1 -1 -1]
   [3 11 6 0 3 6 0 6 5 0 5 9 -1 -1 -1 -1]
   [6 5 9 6 9 11 11 9 8 -1 -1 -1 -1 -1 -1 -1]
   [5 10 6 4 7 8 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 3 0 4 7 3 6 5 10 -1 -1 -1 -1 -1 -1 -1]
   [1 9 0 5 10 6 8 4 7 -1 -1 -1 -1 -1 -1 -1]
   [10 6 5 1 9 7 1 7 3 7 9 4 -1 -1 -1 -1]
   [6 1 2 6 5 1 4 7 8 -1 -1 -1 -1 -1 -1 -1]
   [1 2 5 5 2 6 3 0 4 3 4 7 -1 -1 -1 -1]
   [8 4 7 9 0 5 0 6 5 0 2 6 -1 -1 -1 -1]
   [7 3 9 7 9 4 3 2 9 5 9 6 2 6 9 -1]
   [3 11 2 7 8 4 10 6 5 -1 -1 -1 -1 -1 -1 -1]
   [5 10 6 4 7 2 4 2 0 2 7 11 -1 -1 -1 -1]
   [0 1 9 4 7 8 2 3 11 5 10 6 -1 -1 -1 -1]
   [9 2 1 9 11 2 9 4 11 7 11 4 5 10 6 -1]
   [8 4 7 3 11 5 3 5 1 5 11 6 -1 -1 -1 -1]
   [5 1 11 5 11 6 1 0 11 7 11 4 0 4 11 -1]
   [0 5 9 0 6 5 0 3 6 11 6 3 8 4 7 -1]
   [6 5 9 6 9 11 4 7 9 7 11 9 -1 -1 -1 -1]
   [10 4 9 6 4 10 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 10 6 4 9 10 0 8 3 -1 -1 -1 -1 -1 -1 -1]
   [10 0 1 10 6 0 6 4 0 -1 -1 -1 -1 -1 -1 -1]
   [8 3 1 8 1 6 8 6 4 6 1 10 -1 -1 -1 -1]
   [1 4 9 1 2 4 2 6 4 -1 -1 -1 -1 -1 -1 -1]
   [3 0 8 1 2 9 2 4 9 2 6 4 -1 -1 -1 -1]
   [0 2 4 4 2 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [8 3 2 8 2 4 4 2 6 -1 -1 -1 -1 -1 -1 -1]
   [10 4 9 10 6 4 11 2 3 -1 -1 -1 -1 -1 -1 -1]
   [0 8 2 2 8 11 4 9 10 4 10 6 -1 -1 -1 -1]
   [3 11 2 0 1 6 0 6 4 6 1 10 -1 -1 -1 -1]
   [6 4 1 6 1 10 4 8 1 2 1 11 8 11 1 -1]
   [9 6 4 9 3 6 9 1 3 11 6 3 -1 -1 -1 -1]
   [8 11 1 8 1 0 11 6 1 9 1 4 6 4 1 -1]
   [3 11 6 3 6 0 0 6 4 -1 -1 -1 -1 -1 -1 -1]
   [6 4 8 11 6 8 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [7 10 6 7 8 10 8 9 10 -1 -1 -1 -1 -1 -1 -1]
   [0 7 3 0 10 7 0 9 10 6 7 10 -1 -1 -1 -1]
   [10 6 7 1 10 7 1 7 8 1 8 0 -1 -1 -1 -1]
   [10 6 7 10 7 1 1 7 3 -1 -1 -1 -1 -1 -1 -1]
   [1 2 6 1 6 8 1 8 9 8 6 7 -1 -1 -1 -1]
   [2 6 9 2 9 1 6 7 9 0 9 3 7 3 9 -1]
   [7 8 0 7 0 6 6 0 2 -1 -1 -1 -1 -1 -1 -1]
   [7 3 2 6 7 2 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [2 3 11 10 6 8 10 8 9 8 6 7 -1 -1 -1 -1]
   [2 0 7 2 7 11 0 9 7 6 7 10 9 10 7 -1]
   [1 8 0 1 7 8 1 10 7 6 7 10 2 3 11 -1]
   [11 2 1 11 1 7 10 6 1 6 7 1 -1 -1 -1 -1]
   [8 9 6 8 6 7 9 1 6 11 6 3 1 3 6 -1]
   [0 9 1 11 6 7 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [7 8 0 7 0 6 3 11 0 11 6 0 -1 -1 -1 -1]
   [7 11 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [7 6 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [3 0 8 11 7 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 1 9 11 7 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [8 1 9 8 3 1 11 7 6 -1 -1 -1 -1 -1 -1 -1]
   [10 1 2 6 11 7 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 2 10 3 0 8 6 11 7 -1 -1 -1 -1 -1 -1 -1]
   [2 9 0 2 10 9 6 11 7 -1 -1 -1 -1 -1 -1 -1]
   [6 11 7 2 10 3 10 8 3 10 9 8 -1 -1 -1 -1]
   [7 2 3 6 2 7 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [7 0 8 7 6 0 6 2 0 -1 -1 -1 -1 -1 -1 -1]
   [2 7 6 2 3 7 0 1 9 -1 -1 -1 -1 -1 -1 -1]
   [1 6 2 1 8 6 1 9 8 8 7 6 -1 -1 -1 -1]
   [10 7 6 10 1 7 1 3 7 -1 -1 -1 -1 -1 -1 -1]
   [10 7 6 1 7 10 1 8 7 1 0 8 -1 -1 -1 -1]
   [0 3 7 0 7 10 0 10 9 6 10 7 -1 -1 -1 -1]
   [7 6 10 7 10 8 8 10 9 -1 -1 -1 -1 -1 -1 -1]
   [6 8 4 11 8 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [3 6 11 3 0 6 0 4 6 -1 -1 -1 -1 -1 -1 -1]
   [8 6 11 8 4 6 9 0 1 -1 -1 -1 -1 -1 -1 -1]
   [9 4 6 9 6 3 9 3 1 11 3 6 -1 -1 -1 -1]
   [6 8 4 6 11 8 2 10 1 -1 -1 -1 -1 -1 -1 -1]
   [1 2 10 3 0 11 0 6 11 0 4 6 -1 -1 -1 -1]
   [4 11 8 4 6 11 0 2 9 2 10 9 -1 -1 -1 -1]
   [10 9 3 10 3 2 9 4 3 11 3 6 4 6 3 -1]
   [8 2 3 8 4 2 4 6 2 -1 -1 -1 -1 -1 -1 -1]
   [0 4 2 4 6 2 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 9 0 2 3 4 2 4 6 4 3 8 -1 -1 -1 -1]
   [1 9 4 1 4 2 2 4 6 -1 -1 -1 -1 -1 -1 -1]
   [8 1 3 8 6 1 8 4 6 6 10 1 -1 -1 -1 -1]
   [10 1 0 10 0 6 6 0 4 -1 -1 -1 -1 -1 -1 -1]
   [4 6 3 4 3 8 6 10 3 0 3 9 10 9 3 -1]
   [10 9 4 6 10 4 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 9 5 7 6 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 8 3 4 9 5 11 7 6 -1 -1 -1 -1 -1 -1 -1]
   [5 0 1 5 4 0 7 6 11 -1 -1 -1 -1 -1 -1 -1]
   [11 7 6 8 3 4 3 5 4 3 1 5 -1 -1 -1 -1]
   [9 5 4 10 1 2 7 6 11 -1 -1 -1 -1 -1 -1 -1]
   [6 11 7 1 2 10 0 8 3 4 9 5 -1 -1 -1 -1]
   [7 6 11 5 4 10 4 2 10 4 0 2 -1 -1 -1 -1]
   [3 4 8 3 5 4 3 2 5 10 5 2 11 7 6 -1]
   [7 2 3 7 6 2 5 4 9 -1 -1 -1 -1 -1 -1 -1]
   [9 5 4 0 8 6 0 6 2 6 8 7 -1 -1 -1 -1]
   [3 6 2 3 7 6 1 5 0 5 4 0 -1 -1 -1 -1]
   [6 2 8 6 8 7 2 1 8 4 8 5 1 5 8 -1]
   [9 5 4 10 1 6 1 7 6 1 3 7 -1 -1 -1 -1]
   [1 6 10 1 7 6 1 0 7 8 7 0 9 5 4 -1]
   [4 0 10 4 10 5 0 3 10 6 10 7 3 7 10 -1]
   [7 6 10 7 10 8 5 4 10 4 8 10 -1 -1 -1 -1]
   [6 9 5 6 11 9 11 8 9 -1 -1 -1 -1 -1 -1 -1]
   [3 6 11 0 6 3 0 5 6 0 9 5 -1 -1 -1 -1]
   [0 11 8 0 5 11 0 1 5 5 6 11 -1 -1 -1 -1]
   [6 11 3 6 3 5 5 3 1 -1 -1 -1 -1 -1 -1 -1]
   [1 2 10 9 5 11 9 11 8 11 5 6 -1 -1 -1 -1]
   [0 11 3 0 6 11 0 9 6 5 6 9 1 2 10 -1]
   [11 8 5 11 5 6 8 0 5 10 5 2 0 2 5 -1]
   [6 11 3 6 3 5 2 10 3 10 5 3 -1 -1 -1 -1]
   [5 8 9 5 2 8 5 6 2 3 8 2 -1 -1 -1 -1]
   [9 5 6 9 6 0 0 6 2 -1 -1 -1 -1 -1 -1 -1]
   [1 5 8 1 8 0 5 6 8 3 8 2 6 2 8 -1]
   [1 5 6 2 1 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 3 6 1 6 10 3 8 6 5 6 9 8 9 6 -1]
   [10 1 0 10 0 6 9 5 0 5 6 0 -1 -1 -1 -1]
   [0 3 8 5 6 10 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [10 5 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [11 5 10 7 5 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [11 5 10 11 7 5 8 3 0 -1 -1 -1 -1 -1 -1 -1]
   [5 11 7 5 10 11 1 9 0 -1 -1 -1 -1 -1 -1 -1]
   [10 7 5 10 11 7 9 8 1 8 3 1 -1 -1 -1 -1]
   [11 1 2 11 7 1 7 5 1 -1 -1 -1 -1 -1 -1 -1]
   [0 8 3 1 2 7 1 7 5 7 2 11 -1 -1 -1 -1]
   [9 7 5 9 2 7 9 0 2 2 11 7 -1 -1 -1 -1]
   [7 5 2 7 2 11 5 9 2 3 2 8 9 8 2 -1]
   [2 5 10 2 3 5 3 7 5 -1 -1 -1 -1 -1 -1 -1]
   [8 2 0 8 5 2 8 7 5 10 2 5 -1 -1 -1 -1]
   [9 0 1 5 10 3 5 3 7 3 10 2 -1 -1 -1 -1]
   [9 8 2 9 2 1 8 7 2 10 2 5 7 5 2 -1]
   [1 3 5 3 7 5 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 8 7 0 7 1 1 7 5 -1 -1 -1 -1 -1 -1 -1]
   [9 0 3 9 3 5 5 3 7 -1 -1 -1 -1 -1 -1 -1]
   [9 8 7 5 9 7 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [5 8 4 5 10 8 10 11 8 -1 -1 -1 -1 -1 -1 -1]
   [5 0 4 5 11 0 5 10 11 11 3 0 -1 -1 -1 -1]
   [0 1 9 8 4 10 8 10 11 10 4 5 -1 -1 -1 -1]
   [10 11 4 10 4 5 11 3 4 9 4 1 3 1 4 -1]
   [2 5 1 2 8 5 2 11 8 4 5 8 -1 -1 -1 -1]
   [0 4 11 0 11 3 4 5 11 2 11 1 5 1 11 -1]
   [0 2 5 0 5 9 2 11 5 4 5 8 11 8 5 -1]
   [9 4 5 2 11 3 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [2 5 10 3 5 2 3 4 5 3 8 4 -1 -1 -1 -1]
   [5 10 2 5 2 4 4 2 0 -1 -1 -1 -1 -1 -1 -1]
   [3 10 2 3 5 10 3 8 5 4 5 8 0 1 9 -1]
   [5 10 2 5 2 4 1 9 2 9 4 2 -1 -1 -1 -1]
   [8 4 5 8 5 3 3 5 1 -1 -1 -1 -1 -1 -1 -1]
   [0 4 5 1 0 5 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [8 4 5 8 5 3 9 0 5 0 3 5 -1 -1 -1 -1]
   [9 4 5 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 11 7 4 9 11 9 10 11 -1 -1 -1 -1 -1 -1 -1]
   [0 8 3 4 9 7 9 11 7 9 10 11 -1 -1 -1 -1]
   [1 10 11 1 11 4 1 4 0 7 4 11 -1 -1 -1 -1]
   [3 1 4 3 4 8 1 10 4 7 4 11 10 11 4 -1]
   [4 11 7 9 11 4 9 2 11 9 1 2 -1 -1 -1 -1]
   [9 7 4 9 11 7 9 1 11 2 11 1 0 8 3 -1]
   [11 7 4 11 4 2 2 4 0 -1 -1 -1 -1 -1 -1 -1]
   [11 7 4 11 4 2 8 3 4 3 2 4 -1 -1 -1 -1]
   [2 9 10 2 7 9 2 3 7 7 4 9 -1 -1 -1 -1]
   [9 10 7 9 7 4 10 2 7 8 7 0 2 0 7 -1]
   [3 7 10 3 10 2 7 4 10 1 10 0 4 0 10 -1]
   [1 10 2 8 7 4 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 9 1 4 1 7 7 1 3 -1 -1 -1 -1 -1 -1 -1]
   [4 9 1 4 1 7 0 8 1 8 7 1 -1 -1 -1 -1]
   [4 0 3 7 4 3 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [4 8 7 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [9 10 8 10 11 8 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [3 0 9 3 9 11 11 9 10 -1 -1 -1 -1 -1 -1 -1]
   [0 1 10 0 10 8 8 10 11 -1 -1 -1 -1 -1 -1 -1]
   [3 1 10 11 3 10 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 2 11 1 11 9 9 11 8 -1 -1 -1 -1 -1 -1 -1]
   [3 0 9 3 9 11 1 2 9 2 11 9 -1 -1 -1 -1]
   [0 2 11 8 0 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [3 2 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [2 3 8 2 8 10 10 8 9 -1 -1 -1 -1 -1 -1 -1]
   [9 10 2 0 9 2 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [2 3 8 2 8 10 0 1 8 1 10 8 -1 -1 -1 -1]
   [1 10 2 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [1 3 8 9 1 8 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 9 1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [0 3 8 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]
   [-1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1]])

(def grid-vertices
  [[0 0 0]
   [1 0 0]
   [1 1 0]
   [0 1 0]
   [0 0 1]
   [1 0 1]
   [1 1 1]
   [0 1 1]])

(def vert-to-grid-indices
  [[0 1]
   [1 2]
   [2 3]
   [3 0]
   [4 5]
   [5 6]
   [6 7]
   [7 4]
   [0 4]
   [1 5]
   [2 6]
   [3 7]])

(defn- cube-index [grid isolevel]
  (let [value 0
        value (if (< (grid 0) isolevel) (bit-or value 1) value)
        value (if (< (grid 1) isolevel) (bit-or value 2) value)
        value (if (< (grid 2) isolevel) (bit-or value 4) value)
        value (if (< (grid 3) isolevel) (bit-or value 8) value)
        value (if (< (grid 4) isolevel) (bit-or value 16) value)
        value (if (< (grid 5) isolevel) (bit-or value 32) value)
        value (if (< (grid 6) isolevel) (bit-or value 64) value)
        value (if (< (grid 7) isolevel) (bit-or value 128) value)]
    value))

(defn vertex-interp [isolevel p1 p2 v1 v2]
  (cond
    (< (Math/abs (- isolevel v1)) 0.00001) p1
    (< (Math/abs (- isolevel v2)) 0.00001) p2

    :else
    (let [mu (/ (- isolevel v1) (- v2 v1))
          x (lerp mu (p1 0) (p2 0))
          y (lerp mu (p1 1) (p2 1))
          z (lerp mu (p1 2) (p2 2))]
      [x y z])))

(defn- vertex-position [vert-index grid isolevel]
  (let [grid-idxs (vert-to-grid-indices vert-index)
        p1 (grid-vertices (grid-idxs 0))
        v1 (grid (grid-idxs 0))
        p2 (grid-vertices (grid-idxs 1))
        v2 (grid (grid-idxs 1))]
    (vertex-interp isolevel p1 p2 v1 v2)))

(defn polygonise [grid isolevel]
  (let [index (cube-index grid isolevel)]
    (reduce
     (fn [result vert-index]
       (if (== vert-index -1)
         result
         (conj result (vertex-position vert-index grid isolevel))))
     []
     (tri-table index))))

(defn simplex-surface [isolevel xdim ydim zdim]
  (let [xstep (/ 2.0 xdim)
        ystep (/ 2.0 ydim)
        zstep (/ 2.0 zdim)
        scale-vert (fn [v o]
                     (let [v (map * v [xstep ystep zstep]) ; scale
                           v (map + v o)]                  ; offset
                       (into [] v)))]
    (reduce
     (fn [result [xidx yidx zidx]]
       (let [offset [(- (* xidx xstep) 1)
                     (- (* yidx ystep) 1)
                     (- (* zidx zstep) 1)]
             grid (into [] (map (fn [v]
                                  (let [v (scale-vert v offset)]
                                    (simplex-noise (v 0) (v 1) (v 2))))
                                grid-vertices))
             tris (map #(scale-vert % offset) (polygonise grid isolevel))]

         (if (empty? tris)
           result
           (apply conj result tris))))
     []
     (for [xidx (range xdim)
           yidx (range ydim)
           zidx (range zdim)]
       [xidx yidx zidx]))))
