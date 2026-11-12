#!/usr/bin/env python3
"""
Generates the Mining Island structures for Iskut:
 - data/structures/mine_terrain.vstruct
 - data/structures/mine_props.vstruct
 - data/structures/mine_dock.vstruct

Origin of the island is (0, 1.18, 220) in world coordinates.
All coordinates inside these .vstruct files are local relative to this anchor.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
S = ROOT / "data" / "structures"
S.mkdir(parents=True, exist_ok=True)

def write_vstruct(filename, name, cuboids):
    lines = [
        f"# Handcrafted Mining Island component: {name}",
        "SKYVOXEL_STRUCTURE 1",
        f"NAME {name}",
    ]
    seen_ids = set()
    for c in cuboids:
        cid, mat, x, y, z, w, h, d, solid = c[:9]
        yaw = c[9] if len(c) > 9 else None
        assert cid not in seen_ids, f"Duplicate ID: {cid}"
        seen_ids.add(cid)
        solid_str = "true" if solid else "false"
        if yaw is not None:
            lines.append(f"CUBOID {cid} {mat} {x:.3f} {y:.3f} {z:.3f} {w:.3f} {h:.3f} {d:.3f} {solid_str} {yaw:.1f}")
        else:
            lines.append(f"CUBOID {cid} {mat} {x:.3f} {y:.3f} {z:.3f} {w:.3f} {h:.3f} {d:.3f} {solid_str}")
    lines.append("")
    (S / filename).write_text("\n".join(lines))
    print(f"Wrote {len(cuboids)} cuboids to {filename}")

def build_terrain():
    c = []
    # -------------------------------------------------------------
    # UNDERWATER FOUNDATION (sea level is local y = -0.18)
    # -------------------------------------------------------------
    # Deep slate base down to y = -8.0
    c.append(("found_deep_slate", "slate", 0.0, -6.5, 8.0, 48.0, 3.0, 56.0, True))
    c.append(("found_mid_stone", "stone_dark", 0.0, -4.0, 8.0, 56.0, 3.0, 64.0, True))
    c.append(("found_upper_stone", "stone", 0.0, -1.8, 8.0, 62.0, 2.0, 70.0, True))

    # Underwater shelves around perimeter
    c.append(("shelf_w_under", "stone_dark", -30.0, -1.0, 8.0, 6.0, 1.8, 60.0, True))
    c.append(("shelf_e_under", "stone_dark", 30.0, -1.0, 8.0, 6.0, 1.8, 60.0, True))
    c.append(("shelf_s_under", "stone_dark", 0.0, -1.0, 40.0, 58.0, 1.8, 6.0, True))

    # Wet sand ring just above sea level (y = -0.05)
    c.append(("shore_sand_main", "sand", 0.0, -0.05, 5.0, 58.0, 0.25, 62.0, True))
    c.append(("shore_wet_w", "wet_sand", -29.0, -0.10, 5.0, 4.0, 0.22, 60.0, True))
    c.append(("shore_wet_e", "wet_sand", 29.0, -0.10, 5.0, 4.0, 0.22, 60.0, True))
    c.append(("shore_wet_s", "wet_sand", 0.0, -0.10, 37.0, 56.0, 0.22, 4.0, True))
    c.append(("shore_wet_n_w", "wet_sand", -18.0, -0.10, -23.0, 20.0, 0.22, 4.0, True))
    c.append(("shore_wet_n_e", "wet_sand", 18.0, -0.10, -23.0, 20.0, 0.22, 4.0, True))

    # Foam rim along coastline (visual only)
    c.append(("coast_foam_n_w", "foam", -20.0, -0.16, -24.5, 22.0, 0.02, 2.0, False))
    c.append(("coast_foam_n_e", "foam", 20.0, -0.16, -24.5, 22.0, 0.02, 2.0, False))
    c.append(("coast_foam_w", "foam", -31.5, -0.16, 5.0, 2.0, 0.02, 64.0, False))
    c.append(("coast_foam_e", "foam", 31.5, -0.16, 5.0, 2.0, 0.02, 64.0, False))
    c.append(("coast_foam_s", "foam", 0.0, -0.16, 39.5, 60.0, 0.02, 2.0, False))

    # -------------------------------------------------------------
    # LOWER LANDING TERRACE (y = 0.0 to 1.0)
    # -------------------------------------------------------------
    # North landing apron connecting with dock (z = -24 to -14)
    c.append(("landing_apron_base", "cobble", 0.0, 0.05, -19.0, 14.0, 0.40, 10.0, True))
    c.append(("landing_apron_paved", "stone", 0.0, 0.25, -19.0, 10.0, 0.20, 8.0, True))
    c.append(("landing_retaining_w", "stone_dark", -6.0, 0.50, -19.0, 1.2, 0.80, 8.0, True))
    c.append(("landing_retaining_e", "stone_dark", 6.0, 0.50, -19.0, 1.2, 0.80, 8.0, True))

    # Broad stone staircase up to quarry plateau: 4 steps from y=0.3 to y=2.0
    stair_z = -14.0
    steps = [
        ("dock_stair_0", 0.0, 0.50, stair_z + 0.8, 8.0, 0.45, 1.2),
        ("dock_stair_1", 0.0, 0.90, stair_z + 2.0, 8.0, 0.45, 1.2),
        ("dock_stair_2", 0.0, 1.30, stair_z + 3.2, 8.0, 0.45, 1.2),
        ("dock_stair_3", 0.0, 1.70, stair_z + 4.4, 8.0, 0.45, 1.2),
    ]
    for sid, sx, sy, sz, sw, sh, sd in steps:
        c.append((sid, "stone", sx, sy, sz, sw, sh, sd, True))
    # Stair balustrades
    c.append(("stair_balustrade_w", "stone_dark", -4.4, 1.25, stair_z + 2.6, 0.8, 1.6, 5.5, True))
    c.append(("stair_balustrade_e", "stone_dark", 4.4, 1.25, stair_z + 2.6, 0.8, 1.6, 5.5, True))
    c.append(("stair_post_nw", "stone_light", -4.4, 1.5, stair_z + 0.5, 1.0, 2.0, 1.0, True))
    c.append(("stair_post_ne", "stone_light", 4.4, 1.5, stair_z + 0.5, 1.0, 2.0, 1.0, True))
    c.append(("stair_post_sw", "stone_light", -4.4, 2.3, stair_z + 4.8, 1.0, 1.6, 1.0, True))
    c.append(("stair_post_se", "stone_light", 4.4, 2.3, stair_z + 4.8, 1.0, 1.6, 1.0, True))

    # -------------------------------------------------------------
    # MAIN QUARRY PLATEAU (y = 2.0)
    # -------------------------------------------------------------
    # Plateau solid body
    c.append(("plateau_sub_dirt", "dirt", 0.0, 0.90, 6.0, 52.0, 1.6, 42.0, True))
    c.append(("plateau_stone_body", "stone", 0.0, 1.60, 6.0, 52.0, 0.8, 42.0, True))

    # Surface paving: a mix of stone, cobble, slate, and dirt patches
    c.append(("quarry_floor_main", "stone", 0.0, 1.95, 6.0, 50.0, 0.15, 40.0, True))
    c.append(("quarry_floor_cobble_w", "cobble", -12.0, 2.00, 4.0, 16.0, 0.16, 22.0, True))
    c.append(("quarry_floor_dirt_path", "dirt", 0.0, 2.00, 2.0, 6.0, 0.16, 18.0, True))
    c.append(("quarry_floor_slate_patch", "slate", 10.0, 2.00, 12.0, 14.0, 0.16, 12.0, True))

    # Sunken Quarry Pit (excavation pit at X = 8 to 22, Z = -6 to 8)
    # Lower floor y = 0.8
    c.append(("pit_floor", "slate", 14.0, 0.75, 1.0, 14.0, 0.3, 14.0, True))
    c.append(("pit_rock_layer", "stone_dark", 14.0, 0.45, 1.0, 14.0, 0.4, 14.0, True))
    # Pit cliff walls
    c.append(("pit_wall_n", "stone_dark", 14.0, 1.35, -5.8, 14.0, 1.3, 1.2, True))
    c.append(("pit_wall_s", "stone_dark", 14.0, 1.35, 7.8, 14.0, 1.3, 1.2, True))
    c.append(("pit_wall_e", "stone_dark", 20.8, 1.35, 1.0, 1.2, 1.3, 13.0, True))
    c.append(("pit_wall_w", "stone_dark", 7.2, 1.35, 1.0, 1.2, 1.3, 13.0, True))
    # Pit ramp leading out to quarry floor
    c.append(("pit_ramp_0", "cobble", 7.2, 1.00, -2.0, 2.4, 0.4, 2.5, True))
    c.append(("pit_ramp_1", "cobble", 6.0, 1.40, -2.0, 2.4, 0.5, 2.5, True))
    c.append(("pit_ramp_2", "stone", 4.8, 1.80, -2.0, 2.4, 0.5, 2.5, True))

    # Perimeter retaining walls & defensive battlement on plateau edges
    # West cliff edge wall
    c.append(("wall_w_base", "stone_dark", -25.5, 2.30, 6.0, 1.0, 0.6, 38.0, True))
    c.append(("wall_w_cap", "stone_light", -25.5, 2.65, 6.0, 1.3, 0.15, 38.0, True))
    # East cliff edge wall
    c.append(("wall_e_base", "stone_dark", 25.5, 2.30, 6.0, 1.0, 0.6, 38.0, True))
    c.append(("wall_e_cap", "stone_light", 25.5, 2.65, 6.0, 1.3, 0.15, 38.0, True))
    # North cliff edge wall (split around stairs)
    c.append(("wall_n_left_base", "stone_dark", -15.0, 2.30, -13.5, 19.0, 0.6, 1.0, True))
    c.append(("wall_n_left_cap", "stone_light", -15.0, 2.65, -13.5, 19.0, 0.15, 1.3, True))
    c.append(("wall_n_right_base", "stone_dark", 15.0, 2.30, -13.5, 19.0, 0.6, 1.0, True))
    c.append(("wall_n_right_cap", "stone_light", 15.0, 2.65, -13.5, 19.0, 0.15, 1.3, True))

    # -------------------------------------------------------------
    # MOUNTAIN RIDGE & MINE CAVERN ENTRANCE (Z = 20 to 38, Y = 2.0 to 7.0)
    # -------------------------------------------------------------
    # High mountain rock massif
    c.append(("mountain_tier_1", "stone_dark", 0.0, 3.25, 28.0, 48.0, 2.5, 16.0, True))
    c.append(("mountain_tier_2", "stone", 0.0, 5.00, 30.0, 42.0, 2.0, 13.0, True))
    c.append(("mountain_peak", "stone_light", 0.0, 6.50, 31.0, 34.0, 1.5, 9.0, True))
    c.append(("mountain_ridge_w", "slate", -18.0, 4.25, 26.0, 12.0, 3.5, 12.0, True))
    c.append(("mountain_ridge_e", "slate", 18.0, 4.25, 26.0, 12.0, 3.5, 12.0, True))

    # Mine Tunnel Portal Chamber:
    # Portal Opening at Z = 20 to 32, X = -6 to +6, Height = 4.0
    # Tunnel floor: slate
    c.append(("tunnel_floor", "slate", 0.0, 1.95, 27.0, 8.0, 0.15, 14.0, True))
    # Tunnel left wall
    c.append(("tunnel_wall_left", "stone_dark", -4.5, 3.30, 27.0, 1.5, 2.8, 14.0, True))
    # Tunnel right wall
    c.append(("tunnel_wall_right", "stone_dark", 4.5, 3.30, 27.0, 1.5, 2.8, 14.0, True))
    # Tunnel ceiling
    c.append(("tunnel_ceiling", "stone", 0.0, 4.85, 27.0, 10.5, 0.6, 14.0, True))
    # Back wall of mine tunnel
    c.append(("tunnel_back_wall", "stone_dark", 0.0, 3.30, 33.5, 8.0, 2.8, 1.5, True))

    # Overhang rock ledge above mine portal
    c.append(("portal_overhang", "stone", 0.0, 4.85, 20.0, 12.0, 0.8, 2.4, True))
    c.append(("portal_overhang_cap", "stone_light", 0.0, 5.35, 19.8, 12.8, 0.2, 2.2, True))

    # West Mountain Stairs (leading from quarry floor y=2.0 up to western overlook y=4.5)
    w_steps = [
        ("w_stair_0", -19.0, 2.30, 17.0, 3.5, 0.45, 1.2),
        ("w_stair_1", -19.0, 2.70, 18.2, 3.5, 0.45, 1.2),
        ("w_stair_2", -19.0, 3.10, 19.4, 3.5, 0.45, 1.2),
        ("w_stair_3", -19.0, 3.50, 20.6, 3.5, 0.45, 1.2),
        ("w_stair_4", -19.0, 3.90, 21.8, 3.5, 0.45, 1.2),
        ("w_stair_5", -19.0, 4.30, 23.0, 3.5, 0.45, 1.2),
    ]
    for wid, wx, wy, wz, ww, wh, wd in w_steps:
        c.append((wid, "stone", wx, wy, wz, ww, wh, wd, True))
    c.append(("w_overlook_platform", "cobble", -19.0, 4.25, 26.0, 5.0, 0.4, 5.0, True))
    c.append(("w_overlook_fence", "wood", -21.3, 4.90, 26.0, 0.3, 0.9, 5.0, True))

    # Coastal Boulders around island base
    boulders = [
        ("boulder_nw_0", "stone_dark", -27.0, 0.4, -20.0, 3.2, 1.8, 3.0),
        ("boulder_nw_1", "stone", -26.9, 1.5, -20.0, 2.0, 1.0, 2.0),
        ("boulder_ne_0", "stone_dark", 26.0, 0.4, -20.0, 3.5, 1.7, 3.2),
        ("boulder_ne_1", "stone", 26.1, 1.4, -19.9, 2.2, 0.9, 2.1),
        ("boulder_w_0", "stone_dark", -30.0, 0.5, 0.0, 3.8, 2.0, 4.0),
        ("boulder_w_1", "stone", -30.0, 1.7, 0.0, 2.4, 1.1, 2.5),
        ("boulder_e_0", "stone_dark", 30.0, 0.5, 10.0, 3.6, 2.1, 3.8),
        ("boulder_e_1", "stone", 29.9, 1.8, 10.0, 2.3, 1.0, 2.3),
        ("boulder_s_0", "stone_dark", -14.0, 0.4, 38.0, 4.0, 1.9, 3.5),
        ("boulder_s_1", "stone_dark", 16.0, 0.5, 38.0, 3.8, 2.0, 3.6),
    ]
    for bid, bmat, bx, by, bz, bw, bh, bd in boulders:
        c.append((bid, bmat, bx, by, bz, bw, bh, bd, True))

    # Offshore sea stack near south-east
    c.append(("mine_seastack_base", "stone_dark", 36.0, 0.5, 32.0, 4.5, 3.5, 4.5, True))
    c.append(("mine_seastack_mid", "stone", 36.0, 2.8, 32.0, 3.2, 1.8, 3.2, True))
    c.append(("mine_seastack_top", "stone_light", 36.0, 4.0, 32.0, 2.0, 1.0, 2.0, True))
    c.append(("mine_seastack_foam", "foam", 36.0, -0.16, 32.0, 6.0, 0.02, 6.0, False))

    return c

def build_props():
    c = []
    # -------------------------------------------------------------
    # MINE PORTAL TIMBER FRAMING
    # -------------------------------------------------------------
    # Entrance Timber Arch (at Z = 20.0)
    c.append(("portal_timber_post_l", "wood", -3.6, 3.35, 20.2, 0.5, 2.8, 0.5, True))
    c.append(("portal_timber_post_r", "wood", 3.6, 3.35, 20.2, 0.5, 2.8, 0.5, True))
    c.append(("portal_timber_lintel", "wood_light", 0.0, 4.60, 20.2, 7.8, 0.5, 0.6, True))
    c.append(("portal_timber_brace_l", "wood", -2.8, 4.30, 20.2, 0.3, 0.5, 0.5, True))
    c.append(("portal_timber_brace_r", "wood", 2.8, 4.30, 20.2, 0.3, 0.5, 0.5, True))

    # Interior Support Arch 1 (at Z = 24.5)
    c.append(("arch1_post_l", "wood", -3.6, 3.35, 24.5, 0.45, 2.8, 0.45, True))
    c.append(("arch1_post_r", "wood", 3.6, 3.35, 24.5, 0.45, 2.8, 0.45, True))
    c.append(("arch1_lintel", "wood_light", 0.0, 4.55, 24.5, 7.6, 0.45, 0.5, True))

    # Interior Support Arch 2 (at Z = 29.0)
    c.append(("arch2_post_l", "wood", -3.6, 3.35, 29.0, 0.45, 2.8, 0.45, True))
    c.append(("arch2_post_r", "wood", 3.6, 3.35, 29.0, 0.45, 2.8, 0.45, True))
    c.append(("arch2_lintel", "wood_light", 0.0, 4.55, 29.0, 7.6, 0.45, 0.5, True))

    # -------------------------------------------------------------
    # MINECART RAILS
    # -------------------------------------------------------------
    # Rails running from Z = 31 (inside tunnel) out to Z = -2 (near pit)
    # Track sleepers (wooden cross-ties) spaced every 2 units
    sleeper_z_vals = [30.0, 28.0, 26.0, 24.0, 22.0, 20.0, 18.0, 16.0, 14.0, 12.0, 10.0, 8.0, 6.0, 4.0, 2.0, 0.0, -2.0]
    for idx, sz in enumerate(sleeper_z_vals):
        c.append((f"rail_tie_{idx}", "wood", 0.0, 2.04, sz, 2.2, 0.07, 0.35, True))

    # Left and Right iron rails
    # Rail segments in tunnel (Z = 20 to 31)
    c.append(("rail_left_tunnel", "slate", -0.7, 2.10, 25.5, 0.14, 0.08, 11.0, True))
    c.append(("rail_right_tunnel", "slate", 0.7, 2.10, 25.5, 0.14, 0.08, 11.0, True))
    # Rail segments outside across quarry (Z = -2 to 20)
    c.append(("rail_left_quarry", "slate", -0.7, 2.10, 9.0, 0.14, 0.08, 22.0, True))
    c.append(("rail_right_quarry", "slate", 0.7, 2.10, 9.0, 0.14, 0.08, 22.0, True))

    # Buffer stop at rail end (Z = -2.8)
    c.append(("buffer_post_l", "stone_dark", -0.7, 2.45, -2.8, 0.3, 0.8, 0.3, True))
    c.append(("buffer_post_r", "stone_dark", 0.7, 2.45, -2.8, 0.3, 0.8, 0.3, True))
    c.append(("buffer_beam", "wood", 0.0, 2.60, -2.8, 2.0, 0.4, 0.3, True))

    # -------------------------------------------------------------
    # MINECARTS
    # -------------------------------------------------------------
    # Cart 1: Outside mine entrance on rails (at Z = 14.0), filled with gold ore
    cart_z = 14.0
    c.append(("cart1_bottom", "wood_light", 0.0, 2.18, cart_z, 1.2, 0.12, 1.8, True))
    c.append(("cart1_wheel_fl", "stone_dark", -0.7, 2.12, cart_z - 0.6, 0.16, 0.22, 0.35, True))
    c.append(("cart1_wheel_fr", "stone_dark", 0.7, 2.12, cart_z - 0.6, 0.16, 0.22, 0.35, True))
    c.append(("cart1_wheel_bl", "stone_dark", -0.7, 2.12, cart_z + 0.6, 0.16, 0.22, 0.35, True))
    c.append(("cart1_wheel_br", "stone_dark", 0.7, 2.12, cart_z + 0.6, 0.16, 0.22, 0.35, True))
    c.append(("cart1_side_l", "wood", -0.65, 2.60, cart_z, 0.14, 0.75, 1.8, True))
    c.append(("cart1_side_r", "wood", 0.65, 2.60, cart_z, 0.14, 0.75, 1.8, True))
    c.append(("cart1_end_f", "wood", 0.0, 2.60, cart_z - 0.9, 1.2, 0.75, 0.14, True))
    c.append(("cart1_end_b", "wood", 0.0, 2.60, cart_z + 0.9, 1.2, 0.75, 0.14, True))
    # Loaded gold ore in Cart 1
    c.append(("cart1_ore_gold", "gold", 0.0, 2.65, cart_z, 1.0, 0.55, 1.5, True))

    # Cart 2: Parked inside tunnel (at Z = 27.0), filled with crystals
    cart2_z = 27.0
    c.append(("cart2_bottom", "wood_light", 0.0, 2.18, cart2_z, 1.2, 0.12, 1.8, True))
    c.append(("cart2_wheel_fl", "stone_dark", -0.7, 2.12, cart2_z - 0.6, 0.16, 0.22, 0.35, True))
    c.append(("cart2_wheel_fr", "stone_dark", 0.7, 2.12, cart2_z - 0.6, 0.16, 0.22, 0.35, True))
    c.append(("cart2_wheel_bl", "stone_dark", -0.7, 2.12, cart2_z + 0.6, 0.16, 0.22, 0.35, True))
    c.append(("cart2_wheel_br", "stone_dark", 0.7, 2.12, cart2_z + 0.6, 0.16, 0.22, 0.35, True))
    c.append(("cart2_side_l", "wood", -0.65, 2.60, cart2_z, 0.14, 0.75, 1.8, True))
    c.append(("cart2_side_r", "wood", 0.65, 2.60, cart2_z, 0.14, 0.75, 1.8, True))
    c.append(("cart2_end_f", "wood", 0.0, 2.60, cart2_z - 0.9, 1.2, 0.75, 0.14, True))
    c.append(("cart2_end_b", "wood", 0.0, 2.60, cart2_z + 0.9, 1.2, 0.75, 0.14, True))
    c.append(("cart2_ore_crystal", "crystal", 0.0, 2.65, cart2_z, 1.0, 0.55, 1.5, True))

    # Cart 3: Near the quarry buffer stop (at X = 2.5, Z = 0.0) turned slightly
    cart3_x, cart3_z = 3.2, 0.5
    c.append(("cart3_bottom", "wood_light", cart3_x, 2.18, cart3_z, 1.2, 0.12, 1.8, True))
    c.append(("cart3_side_l", "wood", cart3_x - 0.65, 2.60, cart3_z, 0.14, 0.75, 1.8, True))
    c.append(("cart3_side_r", "wood", cart3_x + 0.65, 2.60, cart3_z, 0.14, 0.75, 1.8, True))
    c.append(("cart3_end_f", "wood", cart3_x, 2.60, cart3_z - 0.9, 1.2, 0.75, 0.14, True))
    c.append(("cart3_end_b", "wood", cart3_x, 2.60, cart3_z + 0.9, 1.2, 0.75, 0.14, True))
    c.append(("cart3_rubble", "slate", cart3_x, 2.55, cart3_z, 1.0, 0.45, 1.5, True))

    # -------------------------------------------------------------
    # TIMBER MINING CRANE OVERLOOKING QUARRY PIT
    # -------------------------------------------------------------
    # Located at corner of pit: X = 5.5, Z = -3.5
    crane_x, crane_z = 5.5, -3.5
    c.append(("crane_base", "stone_dark", crane_x, 2.25, crane_z, 2.2, 0.5, 2.2, True))
    c.append(("crane_mast_1", "wood", crane_x - 0.7, 4.40, crane_z - 0.7, 0.4, 4.0, 0.4, True))
    c.append(("crane_mast_2", "wood", crane_x + 0.7, 4.40, crane_z - 0.7, 0.4, 4.0, 0.4, True))
    c.append(("crane_mast_3", "wood", crane_x - 0.7, 4.40, crane_z + 0.7, 0.4, 4.0, 0.4, True))
    c.append(("crane_mast_4", "wood", crane_x + 0.7, 4.40, crane_z + 0.7, 0.4, 4.0, 0.4, True))
    c.append(("crane_mast_cap", "wood_light", crane_x, 6.50, crane_z, 2.0, 0.3, 2.0, True))
    # Boom arm extending into quarry pit towards +X (+8.0 units)
    c.append(("crane_boom", "wood", crane_x + 3.0, 6.80, crane_z + 2.0, 7.5, 0.4, 0.4, True, 25.0))
    # Counterweight
    c.append(("crane_counterweight", "slate", crane_x - 1.2, 5.80, crane_z - 1.0, 1.2, 1.4, 1.2, True))
    # Hanging hoist cable & bucket into pit
    c.append(("crane_cable", "stone_dark", crane_x + 5.5, 4.50, crane_z + 3.2, 0.1, 4.0, 0.1, False))
    c.append(("crane_bucket", "wood_light", crane_x + 5.5, 2.20, crane_z + 3.2, 1.2, 0.8, 1.2, True))
    c.append(("crane_bucket_gold", "gold", crane_x + 5.5, 2.50, crane_z + 3.2, 0.8, 0.4, 0.8, True))

    # -------------------------------------------------------------
    # EXPOSED ORE VEINS & CRYSTAL CLUSTERS
    # -------------------------------------------------------------
    # Gold veins in quarry pit wall
    c.append(("ore_gold_pit_1", "gold", 14.0, 1.2, 7.2, 2.2, 0.8, 0.4, True))
    c.append(("ore_gold_pit_2", "gold", 20.2, 1.0, 1.0, 0.4, 0.9, 2.0, True))
    c.append(("ore_gold_pit_3", "gold", 11.5, 1.4, -5.2, 1.8, 0.6, 0.4, True))

    # Glowing Azure Crystal clusters in mine cave walls
    c.append(("ore_crystal_cave_l", "crystal", -3.8, 3.2, 26.0, 0.5, 1.2, 1.4, True))
    c.append(("ore_crystal_cave_r", "crystal", 3.8, 3.0, 27.5, 0.5, 1.5, 1.6, True))
    c.append(("ore_cyan_cave_back", "cyan", 0.0, 3.4, 32.7, 1.8, 1.4, 0.4, True))
    c.append(("ore_crystal_cliff_w", "crystal", -25.0, 3.2, 14.0, 0.4, 1.2, 1.8, True))

    # -------------------------------------------------------------
    # MINING WORKBENCH, TOOL SHED & ANVIL
    # -------------------------------------------------------------
    # Blacksmith repair bench on west quarry terrace
    c.append(("anvil_block", "stone_dark", -9.0, 2.35, 8.0, 1.0, 0.7, 0.6, True))
    c.append(("anvil_horn", "slate", -9.0, 2.80, 8.0, 1.4, 0.25, 0.5, True))
    c.append(("workbench_table", "wood", -11.5, 2.40, 8.0, 2.4, 0.8, 1.2, True))
    c.append(("workbench_top", "wood_light", -11.5, 2.85, 8.0, 2.6, 0.15, 1.4, True))
    c.append(("workbench_tools", "gold", -11.2, 3.05, 8.0, 0.5, 0.25, 0.4, True))

    # Stacked crates & barrels around quarry
    crates = [
        ("crate_q_0", -8.5, 2.50, -4.0, 1.2, 1.0, 1.2),
        ("crate_q_1", -9.8, 2.50, -4.0, 1.2, 1.0, 1.2),
        ("crate_q_2", -9.0, 3.45, -4.0, 1.0, 0.9, 1.0),
        ("crate_q_3", -14.0, 2.50, 2.0, 1.4, 1.2, 1.4),
        ("crate_q_4", -14.0, 3.65, 2.0, 1.1, 1.0, 1.1),
        ("crate_q_5", 14.0, 1.20, -3.0, 1.2, 0.9, 1.2),
    ]
    for cid, cx, cy, cz, cw, ch, cd in crates:
        c.append((cid, "wood_light", cx, cy, cz, cw, ch, cd, True))

    barrels = [
        ("barrel_q_0", -11.5, 2.55, -4.0, 1.0, 1.2, 1.0),
        ("barrel_q_1", -11.5, 2.55, -2.8, 1.0, 1.2, 1.0),
        ("barrel_q_2", 8.0, 1.25, 4.0, 1.0, 1.2, 1.0),
        ("barrel_q_3", 9.2, 1.25, 4.0, 1.0, 1.2, 1.0),
    ]
    for bid, bx, by, bz, bw, bh, bd in barrels:
        c.append((bid, "wood", bx, by, bz, bw, bh, bd, True))

    # -------------------------------------------------------------
    # MINING LANTERN POSTS
    # -------------------------------------------------------------
    lamps = [
        ("lamp_stairs_w", -4.4, 2.0, -9.0),
        ("lamp_stairs_e", 4.4, 2.0, -9.0),
        ("lamp_quarry_mid", -5.0, 2.0, 2.0),
        ("lamp_pit_edge", 5.5, 2.0, -1.0),
        ("lamp_portal_l", -4.2, 2.0, 18.5),
        ("lamp_portal_r", 4.2, 2.0, 18.5),
        ("lamp_cave_in", 0.0, 2.0, 29.0),
        ("lamp_overlook", -18.0, 4.25, 24.5),
    ]
    for lid, lx, ly, lz in lamps:
        c.append((f"{lid}_base", "stone_dark", lx, ly + 0.25, lz, 0.7, 0.5, 0.7, True))
        c.append((f"{lid}_post", "wood", lx, ly + 1.85, lz, 0.35, 2.7, 0.35, True))
        c.append((f"{lid}_head", "gold", lx, ly + 3.45, lz, 0.8, 0.7, 0.8, True))
        c.append((f"{lid}_cap", "wood_light", lx, ly + 3.90, lz, 1.1, 0.2, 1.1, True))

    return c

def build_dock():
    c = []
    # -------------------------------------------------------------
    # MINING DOCK / HARBOR (extends North from Z = -23 out to Z = -45)
    # Origin of dock aligns with island anchor: dock center X = 0.0
    # Placed at sea level + 0.18 (world y = 1.18, so deck top is local y = 0.0)
    # -------------------------------------------------------------
    deck_top = 0.0
    plank_h = 0.30
    plank_y = deck_top - plank_h * 0.5  # -0.15

    # Main pier runway: Z = -23.0 to -45.0, width 6.0
    # 22 planks of depth 1.0 each
    for i in range(22):
        pz = -23.5 - i * 1.0
        mat = "wood_light" if (i % 2 == 0) else "wood"
        c.append((f"dock_plank_{i}", mat, 0.0, plank_y, pz, 6.0, plank_h, 1.0, True))

    # T-head landing platform at the outer tip: Z = -45.0 to -48.0, width 14.0
    for i in range(3):
        pz = -45.5 - i * 1.0
        mat = "wood" if (i % 2 == 0) else "wood_light"
        c.append((f"dock_head_plank_{i}", mat, 0.0, plank_y, pz, 14.0, plank_h, 1.0, True))

    # Heavy support beams running under the planks (local y = -0.6)
    c.append(("dock_beam_w", "wood", -2.5, -0.60, -34.0, 0.5, 0.6, 22.0, True))
    c.append(("dock_beam_e", "wood", 2.5, -0.60, -34.0, 0.5, 0.6, 22.0, True))
    c.append(("dock_beam_c", "wood", 0.0, -0.60, -34.0, 0.5, 0.6, 22.0, True))
    # Outer T-head crossbeams
    c.append(("dock_crossbeam_front", "wood", 0.0, -0.60, -46.5, 14.0, 0.6, 0.5, True))

    # Submerged Timber Pilings into sea floor (local y = -1.5, height = 5.0)
    piling_z_list = [-25.0, -29.0, -33.0, -37.0, -41.0, -45.0]
    for idx, pz in enumerate(piling_z_list):
        # Left pile
        c.append((f"pile_l_{idx}", "wood", -3.1, -1.50, pz, 0.7, 5.0, 0.7, True))
        c.append((f"pile_cap_l_{idx}", "wood_light", -3.1, 1.05, pz, 0.9, 0.2, 0.9, True))
        # Right pile
        c.append((f"pile_r_{idx}", "wood", 3.1, -1.50, pz, 0.7, 5.0, 0.7, True))
        c.append((f"pile_cap_r_{idx}", "wood_light", 3.1, 1.05, pz, 0.9, 0.2, 0.9, True))

    # Outer T-head outer wing pilings
    c.append(("pile_head_far_l", "wood", -6.7, -1.50, -46.5, 0.7, 5.0, 0.7, True))
    c.append(("pile_head_cap_far_l", "wood_light", -6.7, 1.05, -46.5, 0.9, 0.2, 0.9, True))
    c.append(("pile_head_far_r", "wood", 6.7, -1.50, -46.5, 0.7, 5.0, 0.7, True))
    c.append(("pile_head_cap_far_r", "wood_light", 6.7, 1.05, -46.5, 0.9, 0.2, 0.9, True))

    # Mooring posts / bollards along the pier edges (deck level y = 0.0, post height = 0.9)
    bollards = [
        ("bollard_w_1", -2.8, -28.0),
        ("bollard_w_2", -2.8, -36.0),
        ("bollard_w_3", -2.8, -44.0),
        ("bollard_e_1", 2.8, -28.0),
        ("bollard_e_2", 2.8, -36.0),
        ("bollard_e_3", 2.8, -44.0),
        ("bollard_head_l", -6.5, -47.5),
        ("bollard_head_r", 6.5, -47.5),
    ]
    for bname, bx, bz in bollards:
        c.append((f"{bname}_post", "wood", bx, 0.45, bz, 0.45, 0.9, 0.45, True))
        c.append((f"{bname}_cap", "wood_light", bx, 0.95, bz, 0.60, 0.15, 0.60, True))

    # Pier Lanterns
    dock_lamps = [
        ("dock_lamp_0", -2.7, -25.0),
        ("dock_lamp_1", 2.7, -25.0),
        ("dock_lamp_2", -2.7, -37.0),
        ("dock_lamp_3", 2.7, -37.0),
        ("dock_lamp_4", -6.2, -47.0),
        ("dock_lamp_5", 6.2, -47.0),
    ]
    for dlname, dlx, dlz in dock_lamps:
        c.append((f"{dlname}_base", "stone_dark", dlx, 0.25, dlz, 0.7, 0.5, 0.7, True))
        c.append((f"{dlname}_post", "wood", dlx, 2.00, dlz, 0.35, 3.0, 0.35, True))
        c.append((f"{dlname}_head", "gold", dlx, 3.85, dlz, 0.8, 0.7, 0.8, True))
        c.append((f"{dlname}_cap", "wood_light", dlx, 4.30, dlz, 1.1, 0.2, 1.1, True))

    # Cargo on the dock: crates and barrels waiting for transport
    c.append(("dock_crate_0", "wood_light", 2.0, 0.50, -42.0, 1.2, 1.0, 1.2, True))
    c.append(("dock_crate_1", "wood_light", 2.0, 1.40, -42.0, 0.9, 0.8, 0.9, True))
    c.append(("dock_crate_2", "wood_light", -2.0, 0.50, -45.0, 1.1, 1.0, 1.1, True))
    c.append(("dock_barrel_0", "wood", 1.8, 0.55, -44.0, 0.9, 1.1, 0.9, True))
    c.append(("dock_barrel_1", "wood", -1.9, 0.55, -46.5, 0.9, 1.1, 0.9, True))

    return c

if __name__ == "__main__":
    terrain = build_terrain()
    write_vstruct("mine_terrain.vstruct", "Mine_Terrain", terrain)

    props = build_props()
    write_vstruct("mine_props.vstruct", "Mine_Props", props)

    dock = build_dock()
    write_vstruct("mine_dock.vstruct", "Mine_Dock", dock)
