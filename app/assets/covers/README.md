# Cover materials

The leather and linen that book covers and spines are made of (`docs/DESIGN.md`, "Books"; `Cover.tsx`'s `Material`).
Forest green and terracotta books are leather; ochre and dusty blue are linen.

| File | Material | Source | Settings |
|---|---|---|---|
| `leather.png` | Leather 030 | [ambientCG](https://ambientcg.com/view?id=Leather030), 1K JPG | `512 1 1 40 0.2 0.16` |
| `linen.png` | Fabric 036 | [ambientCG](https://ambientcg.com/view?id=Fabric036), 1K JPG | `512 1 0.6 24 0.18 0.16` |

**Licence:** every ambientCG asset is [CC0 1.0 Universal](https://docs.ambientcg.com/license/): free to use, change and
ship, with no credit required. We credit it here anyway.

**What the files are:** overlays, not pictures. Each is a seamless 512-pixel tile of grey-and-alpha pixels (black where
the material is darker, white where it's lighter), shown at 256 points over the cover's own colour. That keeps one file
per material for every cloth colour, and needs no blend modes, so it looks the same on iPhone, Android and the web.

**To rebuild or retune:** download the material's "1K-JPG" zip from ambientCG, then run
`node scripts/cover-textures.cjs <..._Color.jpg> <..._NormalGL.jpg> app/assets/covers/<name>.png <settings above>`
(the script explains each setting).
