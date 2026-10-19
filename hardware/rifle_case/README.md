# Rifle Display Case — print files

Sealed, insulated case + quick-release rail mount for the Waveshare
ESP32-S3-Touch-LCD-3.5 rifle display (3000 mAh). Simple build: screws self-tap
straight into the printed plastic — **no heat-set inserts, no soldering iron** —
and the seal is a **foam gasket** (weatherstrip), not an O-ring.

Source model: `rifle_case_mount.scad` (OpenSCAD). STLs are in `stl/`.

## Print first (test the fit)

Print these two coupons before the full shells and check the clamp grips your
rail and the bezel/tub screw holes line up:

- `stl/clamp_test.stl` — a slice of the rail clamp
- `stl/seal_test.stl` — a corner of the bezel + tub seal

## Print per case (build two of everything)

| File | What it is |
|------|------------|
| `stl/mount_tub.stl`     | main sealed body (holds board + battery + compass) |
| `stl/mount_bezel.stl`   | front frame over the screen |
| `stl/mount_arm_std.stl` | rail arm, standard reach (clears a ~56 mm objective) |
| `stl/mount_arm_ext.stl` | rail arm, extended reach (print this instead for a bigger optic) |
| `stl/carrier.stl`       | quick-release dovetail plate (bolts to the case) |
| `stl/mount_jaw.stl`     | rail-clamp jaw |

**Material:** PETG (cold-tough, water-resistant). ASA if you want extra sun/heat
resistance. Not PLA. ~0.2 mm layers, 3+ walls, 30–40% infill are plenty.

## Hardware (per the simple build)

- **Seal screws:** 6× M3 machine screws per case — self-tap into the bezel/tub bosses.
- **Carrier bolts:** 4× M4 machine screws per case — self-tap into the solid pad.
- **Rail clamp:** 1× M4×40 screw (or knurled thumb screw) + 1× M4 hex nut per case.
- **Quick-release:** 1× ~3/16" (≈4–5 mm) ball-lock / hitch pin per case.
- **Seal + insulation:** thin closed-cell foam weatherstrip (gasket on the flange +
  liner around the battery) and double-sided VHB tape for the clear screen cover.
- **Compass wiring:** right-angle male-to-female Dupont jumpers to the board's J8 header.

No brass inserts and no soldering iron needed.

## Changing things in the SCAD

- `part = "..."` selects what to export (`tub`, `bezel`, `mount`, `carrier`, `jaw`,
  `clamp_test`, `seal_test`, plus `assembly`/`exploded`/check views).
- `standoff` = reach from the case to the rail (44 standard, 72 extended).
- `lipo = [X, Z, thickness]` = battery size, if you ever change packs.
