"""Import original icons from saved head-unit APKs; never redraw their artwork.

Usage: python tools/import-oem-icons.py --source-root PATH --aapt2 PATH
Pillow is used only to record transparent bounds. PNG/WebP bytes are copied unchanged.
Native Android vectors retain their original viewport and path data.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    "hvac": "hvac-current-20261018/SVHvac-current.apk",
    "launcher": "launcher-20261019/launcher.apk",
    "systemui": "systemui-20261019/SystemUI.apk",
}
# App semantic name -> original package/resource. No approximate hand-drawn substitutes.
ICONS = {
    "Home": ("launcher", "mipmap/card_ic_home"),
    "Trips": ("launcher", "drawable/ic_clock_black_24dp"),
    "Automations": ("launcher", "drawable/ic_widget"),
    "Edit": ("launcher", "mipmap/set_edit"),
    "Delete": ("systemui", "mipmap/icon_list_deleted"),
    "MusicNote": ("systemui", "mipmap/icon_dock_music_n_dark"),
    "Navigation": ("systemui", "mipmap/icon_dock_nav_n_dark"),
    "DirectionsCar": ("systemui", "mipmap/icon_dock_car_n_dark"),
    "Apps": ("launcher", "drawable/ic_apps"),
    "Settings": ("launcher", "drawable/ic_setting"),
    "Add": ("launcher", "mipmap/card_add_n"),
    "Remove": ("launcher", "mipmap/card_delete_n"),
    "Close": ("systemui", "drawable/ic_clear_black_24dp"),
    "ChevronRight": ("systemui", "drawable/ic_keyboard_arrow_right_black_24dp"),
    "ChevronLeft": ("systemui", "drawable/ic_keyboard_arrow_left_black_24dp"),
    "ChevronUp": ("systemui", "drawable/ic_menu_arrow_up_black_24dp"),
    "Expand": ("systemui", "drawable/ic_menu_arrow_down_black_24dp"),
    "Fan": ("systemui", "mipmap/third_icon_wind"),
    "Power": ("hvac", "mipmap/btn_hvac_on"),
    "Snowflake": ("hvac", "mipmap/wimd_n"),
    "Thermometer": ("systemui", "mipmap/third_icon_temp"),
    "Fuel": ("cluster", "indicator/LED_FuelLevel-Normal.png"),
    "Battery": ("cluster", "indicator/LED_Charging-Red.png"),
    "Hazard": ("systemui", "mipmap/icon_fault_33"),
    "Cloud": ("launcher", "mipmap/ic_w_cloudy"),
    "FavoriteBorder": ("launcher", "mipmap/card_ic_favorite_n"),
    "Star": ("launcher", "mipmap/card_ic_favorite_s"),
    "PlayArrow": ("launcher", "mipmap/card_ic_play_n"),
    "Pause": ("launcher", "mipmap/card_ic_pause_n"),
    "SkipNext": ("launcher", "mipmap/card_ic_next_song_n"),
    "SkipPrevious": ("launcher", "mipmap/card_ic_previous_song_n"),
    "TurnRight": ("launcher", "mipmap/navi_03"),
    "TurnLeft": ("launcher", "mipmap/navi_02"),
    "Straight": ("launcher", "mipmap/navi_09"),
    "UTurn": ("launcher", "mipmap/navi_08"),
    "Roundabout": ("launcher", "mipmap/navi_105"),
    "Destination": ("launcher", "mipmap/navi_15"),
    "Search": ("launcher", "drawable/ic_allapps_search"),
    "Refresh": ("systemui", "mipmap/icon_clean_loading"),
    "Bluetooth": ("systemui", "mipmap/icon_bt_elec_low_dark"),
    "Warning": ("systemui", "mipmap/icon_fault_33"),
    "DarkMode": ("systemui", "mipmap/light_mini"),
    "LightMode": ("systemui", "mipmap/light_max"),
    "Seat": ("systemui", "mipmap/icon_dock_driver_dark"),
    "SeatHeating": ("hvac", "drawable/btn_master_driver_hot_off"),
    "SeatVentilation": ("hvac", "drawable/btn_master_driver_wind_off"),
    "Heat": ("hvac", "mipmap/heating_n"),
    "AirFace": ("hvac", "drawable/icon_blow_face_f"),
    "AirFeet": ("hvac", "drawable/icon_blow_foot_f"),
    "Windshield": ("hvac", "drawable/icon_blow_defrost_n"),
    "Waves": ("hvac", "mipmap/btn_front_defrost_on"),
    "RearDefrost": ("hvac", "mipmap/rear_defrost_s"),
    "WindshieldHeat": ("hvac", "mipmap/btn_front_heat_on"),
    "Loop": ("hvac", "mipmap/icon_circulate_in_s"),
    "Steering": ("hvac", "mipmap/icon_wheelhot_n"),
    "SpeedCamera": ("systemui", "mipmap/icon_dock_360"),
    "Cameraswitch": ("systemui", "mipmap/icon_dock_360"),
    "SwapHoriz": ("systemui", "mipmap/icon_rear_mirror_fold"),
    "Door": ("systemui", "mipmap/icon_fault_24"),
    "Hood": ("systemui", "mipmap/icon_fault_169"),
    "Trunk": ("systemui", "mipmap/icon_fault_162"),
}
for level in range(5):
    ICONS[f"Signal{level}"] = ("systemui", f"mipmap/icon_signal{level}_dark")
for level in range(1, 5):
    ICONS[f"Wifi{level}"] = ("systemui", f"mipmap/icon_wifi{level}_dark")
ICONS["WifiOff"] = ("systemui", "mipmap/icon_wifi_abnormal_dark")
ALIASES = {"Air": "Fan", "Favorite": "Star", "AirlineSeatReclineNormal": "SeatHeating", "Wifi": "Wifi4"}
# These original multicolour symbols need their internal contrast, including +/- backplates.
COLOUR = {"Add", "Remove", "Cloud", "Door", "Hood", "Trunk", "Warning", "Hazard"}
ANDROID = "http://schemas.android.com/apk/res/android"


def resource_index(text):
    entries = {}
    current = None
    for line in text.splitlines():
        if line.startswith("    resource "):
            current = None
        match = re.match(r"    resource (0x\w+) ((?:drawable|mipmap)/\S+)", line)
        if match:
            current = match[2]
            entries[current] = {"id": match[1], "variants": []}
        match = re.match(r"      \(([^)]*)\) \(file\) (\S+)", line)
        if current and match:
            entries[current]["variants"].append({"config": match[1], "path": match[2]})
    return entries


def native_vector(aapt, apk, archive_path):
    tree = subprocess.check_output([str(aapt), "dump", "xmltree", str(apk), "--file", archive_path], encoding="utf-8")
    if not re.search(r"E: vector \(", tree):
        raise ValueError(f"Expected a native vector, got {archive_path}")
    viewport = []
    for key in ("viewportWidth", "viewportHeight"):
        viewport.append(re.search(rf":{key}\(\w+\)=([\d.]+)", tree)[1])
    ET.register_namespace("android", ANDROID)
    vector = ET.Element("vector", {
        f"{{{ANDROID}}}width": f"{viewport[0]}dp", f"{{{ANDROID}}}height": f"{viewport[1]}dp",
        f"{{{ANDROID}}}viewportWidth": viewport[0], f"{{{ANDROID}}}viewportHeight": viewport[1],
    })
    paths = re.findall(r':pathData\(\w+\)="([^"]+)"', tree)
    if not paths or "E: group" in tree:
        raise ValueError(f"Unsupported native vector structure: {archive_path}")
    for data in paths:
        ET.SubElement(vector, "path", {f"{{{ANDROID}}}fillColor": "#FF000000", f"{{{ANDROID}}}pathData": data})
    ET.indent(vector)
    return ET.tostring(vector, encoding="utf-8", xml_declaration=True), paths


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    args = parser.parse_args()
    sources = {}
    for label, relative in SOURCES.items():
        apk = args.source_root / relative
        table = subprocess.check_output([str(args.aapt2), "dump", "resources", str(apk)], encoding="utf-8")
        sources[label] = {"apk": apk, "index": resource_index(table)}
    provenance = {"sources": {label: {"file": entry["apk"].name, "sha256": hashlib.sha256(entry["apk"].read_bytes()).hexdigest()} for label, entry in sources.items()}, "icons": {}}
    lines = ["// Generated by tools/import-oem-icons.py from original head-unit resources.",
             "package ru.jeland.os.ui.theme", "", "import ru.jeland.os.R", "", "object JIcons {"]
    for name, (source, resource) in ICONS.items():
        paths = None
        if source == "cluster":
            data = (ROOT / "app/src/main/assets/cluster/oem" / resource).read_bytes()
            archive_path = resource
            config = "existing OEM cluster asset"
        else:
            entry = sources[source]
            variants = entry["index"][resource]["variants"]
            variant = next((v for v in variants if not v["config"].startswith("night")), variants[0])
            archive_path, config = variant["path"], variant["config"]
            with zipfile.ZipFile(entry["apk"]) as archive:
                data = archive.read(archive_path)
        original_digest = hashlib.sha256(data).hexdigest()
        filename = "oem_" + re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
        suffix = Path(archive_path).suffix
        bounds = None
        if suffix == ".xml":
            data, paths = native_vector(args.aapt2, sources[source]["apk"], archive_path)
            folder = "drawable"
        else:
            folder = "drawable-nodpi"
            image = Image.open(io.BytesIO(data)).convert("RGBA")
            bounds = image.getchannel("A").point(lambda a: 255 if a > 4 else 0).getbbox()
            if bounds is None:
                raise ValueError(f"Empty OEM asset: {name}")
        output = ROOT / "app/src/main/res" / folder / (filename + suffix)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(data)
        crop = f", source=IconSource({','.join(map(str, bounds))})" if bounds else ""
        colour = ", preserveColors=true" if name in COLOUR else ""
        lines.append(f"    val {name}=AppIcon(R.drawable.{filename}{crop}{colour})")
        provenance["icons"][name] = {"source": source, "resource": resource, "archive_path": archive_path, "configuration": config,
            "original_sha256": original_digest, "file": str(output.relative_to(ROOT)).replace("\\", "/"),
            "sha256": hashlib.sha256(data).hexdigest(), "bytes_unchanged": paths is None, "source_bounds": bounds}
        if paths is not None:
            provenance["icons"][name]["native_path_sha256"] = hashlib.sha256("\n".join(paths).encode()).hexdigest()
    # User-requested generated replacement: keep it separate from the original OEM provenance.
    custom_file = "app/src/main/res/drawable-nodpi/ic_panoramic_shade_generated_v1.png"
    custom_data = (ROOT / custom_file).read_bytes()
    custom_image = Image.open(io.BytesIO(custom_data)).convert("RGBA")
    custom_bounds = custom_image.getchannel("A").point(lambda a: 255 if a > 4 else 0).getbbox()
    if custom_bounds is None:
        raise ValueError("Empty custom panoramic shade icon")
    lines.append(f"    val Shade=AppIcon(R.drawable.ic_panoramic_shade_generated_v1, source=IconSource({','.join(map(str, custom_bounds))}))")
    provenance["custom_icons"] = {"Shade": {"source": "imagegen-built-in", "file": custom_file,
        "sha256": hashlib.sha256(custom_data).hexdigest(), "source_bounds": custom_bounds,
        "generation_metadata": "icons/custom/panoramic-shade.json"}}
    for name, target in ALIASES.items():
        lines.append(f"    val {name}={target}")
    lines += ["}", ""]
    (ROOT / "app/src/main/java/ru/jeland/os/ui/theme/JelandIcons.kt").write_text("\n".join(lines), encoding="utf-8")
    manifest = ROOT / "app/src/main/assets/icons/oem/provenance.json"
    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(ICONS)} original OEM resources, one custom shade icon and {len(ALIASES)} aliases")


if __name__ == "__main__":
    main()
