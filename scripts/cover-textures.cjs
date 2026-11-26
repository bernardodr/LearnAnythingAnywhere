#!/usr/bin/env node
// Makes the cover materials in app/assets/covers/ (leather.png, linen.png) from CC0 photographed materials, so they can
// be rebuilt or retuned. See app/assets/covers/README.md for where the materials come from and the settings used.
//
// A material from ambientCG (https://ambientcg.com, CC0) comes as maps; this reads two of them: the colour map (the
// material's light and dark grain) and the OpenGL normal map (its relief, lit here from the top left like the covers).
// Broad blotches are blurred out (wrapping around the edges, so the tile stays seamless) and the rest becomes one
// overlay PNG of grey-and-alpha pixels: black where the material is darker than average, white where it's lighter, each
// as strong as the grain. Laid over any cloth colour with plain alpha compositing (no blend modes, which the web app
// can't do), it adds the material's grain, relief and sheen, the same on iPhone, Android and the web.
//
// It uses jimp-compact and pngjs, which come with Expo's own tools in app/node_modules (no extra dependency).
//
// Usage: node scripts/cover-textures.cjs <color.jpg> <normalGL.jpg> <out.png> <size> <detail> <relief> <blurPx> <darkGain> <lightGain>
//   size       the tile's width and height in pixels (512: shown at 256 points, sharp on 2x screens)
//   detail     weight of the colour map's grain;  relief  weight of the normal map's lit relief
//   blurPx     radius of the blur that removes broad blotches (0 keeps them)
//   darkGain, lightGain  how strongly one standard deviation of grain darkens or lightens the cloth (0 to 1)
const path = require('path');
const APP = path.join(__dirname, '..', 'app');
const Jimp = require(path.join(APP, 'node_modules/jimp-compact'));
const { PNG } = require(path.join(APP, 'node_modules/pngjs'));
const fs = require('fs');
(async () => {
  const [colorPath, normalPath, out, sizeA, detailA, reliefA, blurA, darkA, lightA] = process.argv.slice(2);
  const N = +sizeA, detailW = +detailA, reliefW = +reliefA, blurR = +blurA, darkGain = +darkA, lightGain = +lightA;
  const color = (await Jimp.read(colorPath)).resize(N, N, Jimp.RESIZE_BICUBIC);
  const normal = (await Jimp.read(normalPath)).resize(N, N, Jimp.RESIZE_BICUBIC);
  const lum = new Float32Array(N * N), shade = new Float32Array(N * N);
  // Light from the top left (OpenGL normals: x right, y up, z towards the viewer).
  const L = [-0.55, 0.55, 0.63]; const ln = Math.hypot(...L); L.forEach((v, i) => (L[i] = v / ln));
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const i = y * N + x, c = Jimp.intToRGBA(color.getPixelColor(x, y)), n = Jimp.intToRGBA(normal.getPixelColor(x, y));
    lum[i] = (0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b) / 255;
    const nx = n.r / 127.5 - 1, ny = n.g / 127.5 - 1, nz = n.b / 127.5 - 1, nl = Math.hypot(nx, ny, nz) || 1;
    shade[i] = (nx * L[0] + ny * L[1] + nz * L[2]) / nl - L[2];
  }
  // Take out broad blotches (a wrap-around box blur, so the tile stays seamless), keeping the fine grain.
  const blur = (src, r) => {
    let a = src;
    for (let pass = 0; pass < 3; pass++) {
      const h = new Float32Array(N * N), v = new Float32Array(N * N);
      for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) { let s = 0; for (let k = -r; k <= r; k++) s += a[y * N + ((x + k + N) % N)]; h[y * N + x] = s / (2 * r + 1); }
      for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) { let s = 0; for (let k = -r; k <= r; k++) s += h[((y + k + N) % N) * N + x]; v[y * N + x] = s / (2 * r + 1); }
      a = v;
    }
    return a;
  };
  const broad = blurR > 0 ? blur(lum, blurR) : null;
  const mean = lum.reduce((s, v) => s + v, 0) / lum.length;
  const v = new Float32Array(N * N);
  let sd = 0;
  for (let i = 0; i < N * N; i++) { v[i] = detailW * (broad ? lum[i] - broad[i] + 0.25 * (broad[i] - mean) : lum[i] - mean) + reliefW * shade[i]; sd += v[i] * v[i]; }
  sd = Math.sqrt(sd / (N * N)) || 1;
  const png = new PNG({ width: N, height: N });
  for (let i = 0; i < N * N; i++) {
    const z = v[i] / sd; // in standard deviations, so every material is tuned on the same scale
    const dark = z < 0, a = Math.min(255, Math.round(Math.abs(z) * (dark ? darkGain : lightGain) * 255));
    const g = dark ? 0 : 255;
    png.data[i * 4] = g; png.data[i * 4 + 1] = g; png.data[i * 4 + 2] = g; png.data[i * 4 + 3] = a;
  }
  fs.writeFileSync(out, PNG.sync.write(png, { colorType: 4, inputColorType: 6 }));
  console.log(out, fs.statSync(out).size, 'bytes');
})();
