"""Render a few orthographic-ish views of one OBJ so orientation/facade can be
verified visually (block 9 A4 evidence).

Usage:
  blender.exe -b --python tools\\render_probe.py -- <src.obj> <out_prefix>
Writes <out_prefix>_front/iso/top/side.png.
"""
import math
import sys

import bpy

argv = sys.argv[sys.argv.index("--") + 1:]
src, prefix = argv[0], argv[1]

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.wm.obj_import(filepath=src)
meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
if not meshes:
    raise SystemExit("FATAL: no mesh")

xs, ys, zs = [], [], [], 
for o in meshes:
    for v in o.data.vertices:
        w = o.matrix_world @ v.co
        xs.append(w.x)
        ys.append(w.y)
        zs.append(w.z)
cx = (min(xs) + max(xs)) / 2
cy = (min(ys) + max(ys)) / 2
cz = (min(zs) + max(zs)) / 2
rad = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)) / 2

scene = bpy.context.scene
scene.render.engine = "BLENDER_WORKBENCH"
scene.render.resolution_x = 560
scene.render.resolution_y = 560
scene.render.image_settings.file_format = "PNG"

# In Blender the OBJ importer maps file-Y (up) -> Blender-Z, so Blender-Z is height.
bpy.ops.object.light_add(type="SUN", location=(cx + 10, cy - 10, cz + 20))
bpy.context.object.data.energy = 3.0

bpy.ops.object.empty_add(location=(cx, cy, cz))
target = bpy.context.object
bpy.ops.object.camera_add(location=(cx, cy - rad * 4, cz))
cam = bpy.context.object
con = cam.constraints.new(type="TRACK_TO")
con.target = target
con.track_axis = "TRACK_NEGATIVE_Z"
con.up_axis = "UP_Y"
scene.camera = cam

views = {
    "front": (0.0, -1.0, 0.10),    # looking along +Y (Blender) = from file -Z... see notes
    "back": (0.0, 1.0, 0.10),
    "side": (1.0, 0.0, 0.10),
    "iso": (0.8, -0.8, 0.45),
    "top": (0.0, 0.0, 1.0),
}
for name, d in views.items():
    n = math.sqrt(sum(c * c for c in d))
    cam.location = (cx + d[0] / n * rad * 4,
                    cy + d[1] / n * rad * 4,
                    cz + d[2] / n * rad * 4)
    scene.render.filepath = "%s_%s.png" % (prefix, name)
    bpy.ops.render.render(write_still=True)
    print("RENDERED %s" % scene.render.filepath)
print("bounds x[%.4f..%.4f] y[%.4f..%.4f] z[%.4f..%.4f]" %
      (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
