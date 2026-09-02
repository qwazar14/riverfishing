# -*- coding: utf-8 -*-
"""§latin: the scientific name, written into every fish profile as "latin".

    py tools/add_latin.py            # writes the field; prints species with no name yet

One table, in this file, so a wrong guess is one line to correct. A profile that has no entry here is
left alone and listed, never given a placeholder.
"""
import io, json, os, glob, re

LATIN = {
    "arapaima": "Arapaima gigas", "asp": "Leuciscus aspius", "barracuda": "Sphyraena barracuda",
    "beluga": "Huso huso", "bleak": "Alburnus alburnus", "blue_bream": "Ballerus ballerus",
    "blue_marlin": "Makaira nigricans", "bluefish": "Pomatomus saltatrix", "bluegill": "Lepomis macrochirus",
    "bream": "Abramis brama", "bull_shark": "Carcharhinus leucas", "bullseye_snakehead": "Channa marulius",
    "burbot": "Lota lota", "carp": "Cyprinus carpio",
    "carp_koi_asagi": "Cyprinus rubrofuscus 'Asagi'", "carp_koi_bekko": "Cyprinus rubrofuscus 'Bekko'",
    "carp_koi_kohaku": "Cyprinus rubrofuscus 'Kohaku'", "carp_koi_showa_sanke": "Cyprinus rubrofuscus 'Showa Sanshoku'",
    "carp_koi_tancho_sanke": "Cyprinus rubrofuscus 'Tancho Sanke'",
    "catfish": "Silurus glanis", "channel_catfish": "Ictalurus punctatus", "char": "Salvelinus fontinalis",
    "chub": "Squalius cephalus", "cod": "Gadus morhua", "common_dace": "Leuciscus leuciscus",
    "conger": "Conger conger", "crucian_carp": "Carassius gibelio", "eel": "Anguilla anguilla",
    "flounder": "Platichthys flesus", "frilled_shark": "Chlamydoselachus anguineus", "garfish": "Belone belone",
    "golden_crucian": "Carassius carassius", "golden_dorado": "Salminus brasiliensis",
    "goliath_grouper": "Epinephelus itajara", "gorchak": "Rhodeus amarus", "grass_carp": "Ctenopharyngodon idella",
    "grayling": "Thymallus thymallus", "gudgeon": "Gobio gobio", "halibut": "Hippoglossus hippoglossus",
    "herring": "Clupea harengus", "ide": "Leuciscus idus", "jack_crevalle": "Caranx hippos",
    "kutum": "Rutilus kutum", "largemouth_bass": "Micropterus salmoides", "lenok": "Brachymystax lenok",
    "mackerel": "Scomber scombrus", "mahi": "Coryphaena hippurus", "mako": "Isurus oxyrinchus",
    "mayan_cichlid": "Mayaheros urophthalmus", "mirror_carp": "Cyprinus carpio var. specularis",
    "naked_carp": "Cyprinus carpio var. nudus", "nase": "Chondrostoma nasus", "oscar": "Astronotus ocellatus",
    "peacock_bass": "Cichla ocellaris", "perch": "Perca fluviatilis", "pike": "Esox lucius",
    "pink_salmon": "Oncorhynchus gorbuscha", "piraiba": "Brachyplatystoma filamentosum",
    "rainbow_trout": "Oncorhynchus mykiss", "ray": "Amblyraja radiata", "roach": "Rutilus rutilus",
    "rotan": "Perccottus glenii", "round_goby": "Neogobius melanostomus", "rudd": "Scardinius erythrophthalmus",
    "ruffe": "Gymnocephalus cernua", "sabrefish": "Pelecus cultratus", "sailfish": "Istiophorus platypterus",
    "saithe": "Pollachius virens", "salmon": "Salmo salar", "sculpin": "Cottus gobio",
    "seabass": "Dicentrarchus labrax", "silver_carp": "Hypophthalmichthys molitrix", "smelt": "Osmerus eperlanus",
    "snook": "Centropomus undecimalis", "sterlet": "Acipenser ruthenus", "striped_bass": "Morone saxatilis",
    "sturgeon": "Acipenser sturio", "swordfish": "Xiphias gladius", "taimen": "Hucho taimen",
    "tarpon": "Megalops atlanticus", "tench": "Tinca tinca", "trout": "Salmo trutta fario",
    "tubenose_goby": "Proterorhinus semilunaris", "verkhovka": "Leucaspius delineatus", "vimba": "Vimba vimba",
    "volga_zander": "Sander volgensis", "wahoo": "Acanthocybium solandri", "white_bream": "Blicca bjoerkna",
    "white_eye_bream": "Ballerus sapa", "whitefish": "Coregonus lavaretus", "wild_carp": "Cyprinus carpio",
    "yellowfin_tuna": "Thunnus albacares", "zander": "Sander lucioperca",
}

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(REPO, "common", "src", "main", "resources", "data", "riverfishing", "fish_profiles")
missing, written = [], 0
for f in sorted(glob.glob(os.path.join(DIR, "*.json"))):
    sp = os.path.basename(f)[:-5]
    if sp not in LATIN:
        missing.append(sp)
        continue
    raw = io.open(f, encoding="utf-8").read()
    if '"latin": "%s"' % LATIN[sp] in raw:
        continue
    # textual: one line after "display", so the diff is one line and the file's own layout survives
    raw = re.sub(r'^\s*"latin":.*\n', "", raw, flags=re.M)
    m = re.search(r'^(\s*)"display":.*\n', raw, flags=re.M)
    line = '%s"latin": "%s",\n' % (m.group(1) if m else "  ", LATIN[sp])
    raw = raw[:m.end()] + line + raw[m.end():] if m else raw.replace("{\n", "{\n" + line, 1)
    io.open(f, "w", encoding="utf-8", newline="\n").write(raw)
    written += 1
print("written %d, unchanged %d" % (written, len(LATIN) - written - len(missing)))
if missing:
    print("no latin name yet: " + ", ".join(missing))
