// =====================================================================
//  rifle_case_mount.scad   —  RIGID side mount (no fold) revision
//  Waveshare ESP32-S3-Touch-LCD-3.5 rifle display.
//
//  Ryan 2026-10-15: no longer wants it to fold; wants a solid rigid mount
//  that can reach OUT FURTHER so the display clears the side of an optic.
//
//  Same WATERPROOF + INSULATED sealed shell as the other revisions
//  (outward O-ring flange, foam-lined battery bay, Gore breather vent).
//  The mount is now a ONE-PIECE rigid arm + rail clamp. The arm bolts to
//  a solid pad on the case's +X short edge with 4 M4 bolts, so to change
//  the reach you reprint ONLY the small arm, never the sealed housing.
//  USB-C stays on the -X edge, clear of the arm and chargeable while mounted.
//
//  REACH is the one knob: `standoff` = clear gap from the case edge to the
//  rail. Bump it (or print the EXTENDED arm) if the display touches the optic.
//  Rail clamp keeps the proven dovetail jaw + a recoil lug into a cross-slot
//  + a low-profile M4 knurled thumb screw.
//
//  Measured board (2026-10-14): outline 92.5 x 60.9 x 13.23; lit/touch
//  74 x 49 centred; USB-C RIGHT short edge ~centred; LiPo 66.2x35.6x9.75.
//
//  Print PETG (or ASA). Non-measured sizes are ESTIMATES: print
//  clamp_test + the arm + seal_test before the full shells.
//
//  Axes: X across rifle, Y along bore (+Y muzzle), Z up. Screen faces
//  the shooter (-Y). Rail runs along Y; clamp on the +X side of the case.
// =====================================================================
part     = "assembly"; // assembly | exploded | bezel | tub | arm | clamp | jaw | clamp_test | seal_test
standoff = 44;         // clear reach from the case +X edge to the rail centre (mm). Bump to clear a bigger optic.

// ---- measured board ----
board      = [92.5, 60.9, 13.23];
lit        = [74, 49];
lit_off    = [0, 0];
glass_rise = 3.0;
port_z     = 0;
usb_slot   = [14, 9];
usb_plug_d = 4.0;

// ---- LiPo (measured) + insulation ----
// Battery is the one knob for the two case variants. [X along board width, Z
// vertical, Y thickness-into-case-depth]. The thickness drives case depth; the
// X x Z footprint must stay inside the board footprint (92.5 x 60.9) or the
// shell has to grow. Insulation = 2 mm closed-cell foam liner on ALL faces
// (the +2*foam_liner below) + the sealed shell trapping the ESP32 waste heat.
//   STD  3000 mAh (measured):   [66.2, 35.6, 9.75]
//   TEST 5000 mAh (Ryan's 115659 pack, confirmed 2026-10-19): [60, 54, 10]
// Export the 5000 case with:  -D 'lipo=[60,54,10]'.  NOTE: the 5000 pack is only
// 10 mm thick (≈ the 3000's 9.75), just a wider/taller 54x60 footprint, so the
// SHELL is externally identical (D_tub ≈ 39.5 both) — ONLY the tub's internal
// battery pocket differs. bezel, arm, carrier, jaw all interchange between the two
// cases (sub-0.25 mm delta). So the 5000 build = reprint mount5000_tub only.
// CONNECTOR MISMATCH: board battery socket = MX1.25 (1.25 mm, per Waveshare wiki);
// Ryan's 5000 pack ships with JST PH2.0 (2.0 mm) -> they DO NOT MATE. Either buy a
// 5000 1S pack with an MX1.25 lead, or re-terminate to MX1.25 matching the working
// 3000 pack's polarity. Reversed polarity kills the board — verify + pin first.
lipo       = [66.2, 35.6, 9.75];
foam_liner = 2.0;

// ---- compass (BNO085) + internal wiring clearance ----
// A clearance LAYER is added behind the board (wire_gap) so the J8 header
// connector + the 4 jumpers + the BNO085 all fit inside the sealed shell with
// NOTHING piercing the seal. The compass sits in a cradle at the -X end, as far
// as the shell allows from the +X rail/clamp steel (best spot for a magnetometer).
comp       = [27, 20, 3.5];   // BNO085 module envelope (generic GY- or Adafruit #4754 both fit); Z = PCB+parts
wire_gap   = 9;               // clearance layer behind the board (use right-angle/low-profile Dupont on J8)
crib_wall  = 1.6;             // compass cradle rib thickness

// ---- shell ----
clear = 0.6; wall = 2.8; r = 4;
front_th = 2.6; back_th = 2.8;
lip = 2.4; open_gap = 0.8; glass_gasket = 1.0;

// ---- perimeter seal flange ----
rim_out = 6.5; flange_th = 3.2;
gg_w = 2.0; gg_d = 1.15;
screw_d = 3.3; cs_d = 6.2; cs_h = 2.2;
insert_d = 4.2; insert_h = 6;        // now only sizes the seal-screw BOSS (not an insert)
insert_d_m4 = 5.6; insert_h_m4 = 7;  // (unused; carrier bolts self-tap now)
// 2026-10-19 SIMPLER/CHEAPER build (Ryan): screws self-tap into the printed plastic — NO heat-set
// inserts, NO soldering iron — and the perimeter seal is a FOAM gasket (weatherstrip on the flat
// flange) instead of an O-ring cord. Hardware = just M3/M4 machine screws (+ the clamp nut/screw).
selftap_m3 = 2.5;   // M3 self-tap pilot into PETG (~0.83x major); seal lid screws
selftap_m4 = 3.4;   // M4 self-tap pilot; carrier bolts into the solid +X pad
pad_tap_h  = 8;     // self-tap depth into the pad
vent_d = 6.2;

// derived shell
W = board[0]+2*clear+2*wall; H = board[1]+2*clear+2*wall;
D_board = board[2]; D_bat = lipo[2]+2*foam_liner+0.6; D_tub = D_board+wire_gap+D_bat+back_th;
FW = W+2*rim_out; FH = H+2*rim_out;
bx=(W-board[0])/2; bz=(H-board[1])/2;
ow=lit[0]+2*open_gap; oh=lit[1]+2*open_gap;
oxo=(W-ow)/2+lit_off[0]; ozo=(H-oh)/2+lit_off[1];
function screws()=[ [rim_out/2+1,FH*0.22],[rim_out/2+1,FH*0.78],
                    [FW-rim_out/2-1,FH*0.22],[FW-rim_out/2-1,FH*0.78],
                    [FW/2,rim_out/2+1],[FW/2,FH-rim_out/2-1] ];
OX=rim_out; OZ=rim_out;
$fn=48;
module rbox(s,rr){ hull() for(x=[rr,s[0]-rr],z=[rr,s[2]-rr]) translate([x,0,z]) rotate([-90,0,0]) cylinder(r=rr,h=s[1]); }
module ring_groove(cx,cz,w,h,depth,gw,rr){ linear_extrude(depth) difference(){
  offset(r=rr) offset(r=-rr) translate([cx-w/2,cz-h/2]) square([w,h]);
  offset(r=rr) offset(r=-rr) translate([cx-(w-2*gw)/2,cz-(h-2*gw)/2]) square([w-2*gw,h-2*gw]); } }

// ======================================================= MOUNT geometry
PADX   = OX+W;            // +X wall outer face (105.8) — where the arm bolts on
pad_d  = 9;              // mount-pad boss depth outboard
armY   = D_tub/2;        // arm centred on the body depth
armZ   = FH/2;           // arm centred on the body height
beam_z = 26;             // arm beam height (Z) for bending stiffness
beam_y = 12;             // arm beam thickness (Y)
CLX    = PADX + standoff; // rail centre X
bolt4_y = [D_tub*0.30, D_tub*0.70];
bolt4_z = [FH*0.34, FH*0.66];
m4_clear = 4.4; m4_head = 8.0;

// 4 mount-pad insert positions (on the +X pad face), as [y,z]
function padholes()=[ [bolt4_y[0],bolt4_z[0]],[bolt4_y[1],bolt4_z[0]],
                      [bolt4_y[0],bolt4_z[1]],[bolt4_y[1],bolt4_z[1]] ];

// ---- compass cradle: open tray on the -X interior wall (far from the +X rail steel) ----
// The BNO085 stands flat against the -X wall in the wire-gap layer (in front of the
// LiPo). Four thin ribs retain it; a notch lets the 4 jumpers exit toward J8. Secure
// with foam tape. This is the farthest point in the shell from the steel rail/clamp.
cp_w    = comp[1]+1.0;             // pocket Y extent (module width + fit)
cp_h    = comp[0]+1.0;             // pocket Z extent (module length + fit)
cp_proj = comp[2]+1.6;             // +X projection of the retaining ribs
cp_y    = D_board + 1.8;           // 1.8 mm behind the board, inside the wire-gap layer
cp_z0   = OZ + (H-cp_h)/2;         // centred in Z
module comp_cradle(){
  translate([OX+wall, cp_y, cp_z0]) difference(){
    cube([cp_proj, cp_w, cp_h]);                                                         // block fused to the -X wall
    translate([-0.1, crib_wall, crib_wall]) cube([cp_proj+0.2, cp_w-2*crib_wall, cp_h-2*crib_wall]); // hollow -> 4 ribs, open +X
    translate([-0.1, cp_w/2-3.5, cp_h-crib_wall-0.1]) cube([cp_proj+0.2, 7, crib_wall+0.3]);         // wire notch out the top toward J8
  }
}

// ----------------------------------------------------------- bezel (front)
module bezel(){ difference(){
  union(){ translate([0,-front_th,0]) rbox([FW,front_th,FH],r+rim_out*0.4);
           translate([OX,-0.01,OZ]) rbox([W,lip+0.01,H],r); }
  translate([OX+oxo,-front_th-1,OZ+ozo]) cube([ow,front_th+2,oh]);
  translate([OX+oxo-1.4,-0.01,OZ+ozo-1.4]) cube([ow+2.8,lip+1,oh+2.8]);
  translate([OX+oxo-3.0,lip-glass_gasket,OZ+ozo-3.0]) cube([ow+6,glass_gasket+0.2,oh+6]);
  for(s=screws()) translate([s[0],-front_th-1,s[1]]) rotate([-90,0,0]){ cylinder(d=screw_d,h=lip+front_th+3); cylinder(d=cs_d,h=cs_h+front_th); }
} }

// ----------------------------------------------------------- tub (back) + +X mount pad
module tub(){ difference(){
  union(){
    translate([OX,0,OZ]) rbox([W,D_tub,H],r);
    rbox([FW,flange_th,FH],r+rim_out*0.4);
    for(s=screws()) translate([s[0],flange_th,s[1]]) rotate([-90,0,0]) cylinder(d=insert_d+3.2,h=insert_h+1);
    // solid mount-pad boss on the +X short edge (OUTSIDE the cavity)
    hull(){
      translate([PADX-6, bolt4_y[0]-7, bolt4_z[0]-7]) cube([0.1, (bolt4_y[1]-bolt4_y[0])+14, (bolt4_z[1]-bolt4_z[0])+14]);
      translate([PADX+pad_d-2, bolt4_y[0]-7, bolt4_z[0]-7]) cube([2, (bolt4_y[1]-bolt4_y[0])+14, (bolt4_z[1]-bolt4_z[0])+14]);
    }
  }
  // cavity
  translate([OX+wall,-0.01,OZ+wall]) rbox([W-2*wall,D_tub-back_th+0.01,H-2*wall],r-wall/2);
  translate([OX+bx,-0.01,OZ+bz]) cube([board[0],D_board+0.01,board[1]]);
  // perimeter O-ring groove removed — seal is now a FOAM gasket on the flat flange (simpler build)
  for(s=screws()) translate([s[0],-0.1,s[1]]) rotate([-90,0,0]) cylinder(d=selftap_m3,h=flange_th+insert_h-2);  // self-tap pilot through flange + boss
  translate([(FW-(lipo[0]+2*foam_liner))/2,D_board+wire_gap+0.3,(FH-(lipo[1]+2*foam_liner))/2]) cube([lipo[0]+2*foam_liner,lipo[2]+2*foam_liner,lipo[1]+2*foam_liner]);
  // USB-C on the -X short edge (opposite the arm)
  uz=(FH-usb_slot[0])/2+port_z;
  translate([OX-1,D_board*0.5-usb_slot[1]/2,uz]) cube([wall+2,usb_slot[1],usb_slot[0]]);
  translate([OX-2,D_board*0.5-usb_slot[1]/2-2,uz-2]) cube([usb_plug_d+2,usb_slot[1]+4,usb_slot[0]+4]);
  // breather vent (bottom)
  translate([FW/2,D_tub*0.65,OZ-1]) cylinder(d=vent_d,h=wall+2,$fn=40);
  // 4 self-tap pilots into the solid pad from the +X face (carrier bolts; no inserts)
  for(p=padholes()) translate([PADX+pad_d+0.1,p[0],p[1]]) rotate([0,-90,0]) cylinder(d=selftap_m4,h=pad_tap_h);
}
  for(xx=[OX+bx+3,OX+bx+board[0]-3-4]) translate([xx,D_board-0.8,OZ+bz+2]) cube([4,0.8,board[1]-4]);
  comp_cradle();   // compass tray at the -X end (unioned after the cavity cut so it survives)
}

// =============================== rigid arm + proven captured-jaw rail clamp
rail_w=21.2; rail_top=15.6; rail_h=2.8; slot_w=5.23; clamp_l=32; bolt_d=4.4; gap=0.25;
// ---- ROBUST clamp hardware (ask: the thumb screw must never crack the plastic) ----
// The screw now tensions between a CAPTURED STEEL HEX NUT and a STEEL WASHER under the
// knurled head, so the plastic only ever sees broad-faced compression -- never threads
// cut into plastic, never a point load. Clamp body is also beefed downward for meat.
nut_af   = 7.2;    // M4 steel hex nut across-flats (+ fit)
nut_th   = 3.4;    // nut pocket depth (standard M4 nut ~3.2)
washer_d = 9.0;    // M4 washer OD seat under the head
washer_th= 1.2;

// ---- QUICK-RELEASE dovetail (ask: pull the display off in the field for rucking) ----
// Rail clamp + arm stay on the rifle. The sealed case carries a dovetail tongue (on a
// small `carrier` plate bolted to its +X pad) that slides DOWN into the arm's socket and
// locks with one ball-lock pin. Pull the pin -> lift the display off. Recoil (Y) rides
// the dovetail flanks, the pin stops lift, a floor stops drop. Cold-proof + tool-free
// (no printed snap-latch to go brittle in the cold).
qr          = true;    // false = arm bolts solid to the case pad (the old rigid way)
car_th      = 6;       // carrier plate thickness (X)
dt_proj     = 7;       // dovetail projection (+X)
dt_root_w   = 11;      // Y width at the narrow root (= socket mouth)
dt_tip_w    = 17;      // Y width at the wide tip (captured behind the mouth)
dt_len      = 30;      // Z slide engagement length
dt_gap      = 0.35;    // slide clearance
sock_side   = 3.2;     // socket Y side-wall (the capturing lips)
sock_back   = 3.2;     // socket +X back wall
sock_bot    = 3.0;     // socket floor (bottom stop) thickness
pin_d       = 4.4;     // ball-lock quick-release pin hole (along Y)
pin_inset   = 7;       // pin centre below the socket top
car_face    = PADX + pad_d;              // pad outboard face (carrier bolts here)
tongue_root = car_face + car_th;         // tongue root X (= socket mouth)
tongue_tip  = tongue_root + dt_proj;
socket_y0   = armY - dt_tip_w/2 - sock_side;
socket_y1   = armY + dt_tip_w/2 + sock_side;
socket_x1   = tongue_tip + sock_back;
dt_z0       = armZ - dt_len/2;           // bottom stop
dt_z1       = armZ + dt_len/2;           // open top
pinx        = (tongue_root + tongue_tip)/2;
pinz        = dt_z1 - pin_inset;
module dt_tongue2d(e=0){ offset(delta=e) polygon([
  [tongue_root, armY-dt_root_w/2],[tongue_tip, armY-dt_tip_w/2],
  [tongue_tip, armY+dt_tip_w/2],[tongue_root, armY+dt_root_w/2] ]); }
module dt_socket_block(){ translate([tongue_root, socket_y0, dt_z0-sock_bot]) cube([socket_x1-tongue_root, socket_y1-socket_y0, dt_len+sock_bot]); }
module dt_cavity(){ translate([0,0,dt_z0]) linear_extrude(dt_len+60) dt_tongue2d(dt_gap); }   // floor at dt_z0, open top
module dt_pin(){ translate([pinx, socket_y0-1, pinz]) rotate([-90,0,0]) cylinder(d=pin_d, h=socket_y1-socket_y0+2, $fn=32); }
// carrier: the small plate that stays bolted to the sealed case (+X pad) and carries the
// dovetail tongue + pin hole. Reprint ONLY this + the arm to change reach; case is sealed.
module carrier(){ difference(){
  union(){
    translate([car_face, socket_y0+0.4, dt_z0-sock_bot]) cube([car_th, (socket_y1-socket_y0)-0.8, dt_len+sock_bot]);  // backing plate
    translate([0,0,dt_z0]) linear_extrude(dt_len) dt_tongue2d(0);                                                     // the dovetail tongue
  }
  for(p=padholes()){ translate([car_face-1,p[0],p[1]]) rotate([0,90,0]) cylinder(d=m4_clear,h=car_th+2,$fn=32);
                     translate([car_face+car_th-2.4,p[0],p[1]]) rotate([0,90,0]) cylinder(d=m4_head,h=3.0,$fn=32); }
  dt_pin();
} }
clz = armZ;                      // rail dovetail TOP reference, at arm-centre height
jw  = rail_w/2 + 7;
zb  = clz - 2*rail_h;            // bottom of the dovetail
jaw_top   = clz + 2;
clamp_top = clz + 3 + wall;      // top of the clamp body
bolt_z    = clz - rail_h - 0.8;  // clamp bolt, just below the rail top
module rail_profile(e=0){ translate([CLX,clz]) polygon([[-rail_top/2-e,e],[rail_top/2+e,e],[rail_w/2+e,-rail_h],[rail_top/2+e,-rail_h*2-e],[-rail_top/2-e,-rail_h*2-e],[-rail_w/2-e,-rail_h]]); }
module below_rail(e=0){ translate([CLX-rail_top/2-e,zb-20]) square([rail_top+2*e,20.01]); }
module jaw_pocket(){ translate([CLX+rail_top/2-1,zb-1]) square([jw-rail_top/2+2,jaw_top-zb+1]); }
module xz(len){ translate([0,armY+len/2,0]) rotate([90,0,0]) linear_extrude(len) children(); }  // extrude lands at y'∈[-len,0]; +len/2 centres it on armY
module recoil_key(){ translate([CLX-2.5,armY-(slot_w-0.3)/2,clz-2.6]) cube([5.0,slot_w-0.3,3.8]); }
module clamp_bolts(){
  translate([CLX+jw+2,armY,bolt_z]) rotate([0,-90,0]) cylinder(d=bolt_d,h=2*jw+6,$fn=32);                         // M4 shaft clearance all the way through
  translate([CLX+jw+0.2,armY,bolt_z]) rotate([0,-90,0]) cylinder(d=washer_d,h=washer_th+0.4,$fn=40);              // washer seat: head bears on a STEEL washer
  translate([CLX-rail_top/2,armY,bolt_z]) rotate([0,-90,0]) cylinder(d=nut_af/cos(30),h=nut_th+0.3,$fn=6);        // CAPTURED steel hex nut (threads in steel)
  translate([CLX-rail_top/2-nut_th,armY-nut_af/2,bolt_z]) cube([nut_th+0.3,nut_af,clamp_top-bolt_z+2]);           // drop-in slot so the nut can't spin/pull
}

// rifle-side arm: quick-release dovetail socket (qr) OR solid pad bolts, a stiff beam,
// the captured-jaw rail clamp, and the recoil lug. reach = `standoff`; to change it,
// reprint ONLY this arm + the carrier (the sealed housing never changes).
module mount(){
  union(){
    difference(){
      union(){
        if(qr) dt_socket_block();
        translate([qr?socket_x1-3:PADX, armY-beam_y/2, armZ-beam_z/2]) cube([(CLX-rail_top/2+3)-(qr?socket_x1-3:PADX), beam_y, beam_z]);  // stiff beam
        translate([CLX-jw, armY-clamp_l/2, zb-4]) cube([2*jw, clamp_l, clamp_top-(zb-4)]);                                                 // beefed clamp body (down for the captured nut)
      }
      if(qr){ dt_cavity(); dt_pin(); }                                   // dovetail socket + quick-release pin
      else for(p=padholes()) translate([PADX-1,p[0],p[1]]) rotate([0,90,0]){ cylinder(d=m4_clear,h=6); translate([0,0,3.5]) cylinder(d=m4_head,h=beam_z); }
      xz(clamp_l+2){ rail_profile(gap); below_rail(gap); jaw_pocket(); }  // dovetail channel + jaw pocket
      clamp_bolts();
    }
    recoil_key();   // unioned after the channel cut so the lug survives into the cross-slot
  }
}
module jaw(){ difference(){
  xz(clamp_l) difference(){ offset(delta=-gap) jaw_pocket(); rail_profile(gap); below_rail(gap); translate([CLX+jw,zb-5]) square([10,20]); }
  clamp_bolts();
} }

// hardware + context (renders only)
module clamp_thumbscrew(){ bz=clz-rail_h-0.8; color("#9aa3ab") translate([CLX+jw-1,armY,bz]) rotate([0,90,0]) union(){ cylinder(d=4.2,h=3); translate([0,0,3]) cylinder(d=12,h=4,$fn=24);} }
module rail_stub(len=90){ color("#34383d") difference(){
  xz(len){ translate([CLX,clz]) polygon([[-rail_top/2,0],[rail_top/2,0],[rail_w/2,-rail_h],[rail_top/2,-2*rail_h],[-rail_top/2,-2*rail_h],[-rail_w/2,-rail_h]]);
           translate([CLX-rail_w/2-2,clz-2*rail_h-9]) square([rail_w+4,9.01]); }
  for(yy=[armY-38:10:armY+38]) translate([CLX-rail_w/2-1,yy-2.67,clz-rail_h-0.1]) cube([rail_w+2,5.35,rail_h+0.2]); } }
module optic(obj=56,tube=30,ringH=36){ // ghost scope tube + objective bell above the rail (render only)
  oz = clz + ringH;                       // scope centreline sits ringH above the rail top
  color("#20242a"){
    translate([CLX, armY-70, oz]) rotate([-90,0,0]) cylinder(d=tube,h=140,$fn=56);   // main tube
    translate([CLX, armY+36, oz]) rotate([-90,0,0]) cylinder(d1=tube,d2=obj,h=14,$fn=56); // bell taper
    translate([CLX, armY+50, oz]) rotate([-90,0,0]) cylinder(d=obj,h=26,$fn=56);          // objective bell
  } }

module ghost(){
  color("#1f6b46") translate([OX+bx,0,OZ+bz]) cube([board[0],board[2]-glass_rise,board[1]]);
  color("#111416") translate([OX+bx+(board[0]-lit[0])/2+lit_off[0],-glass_rise,OZ+bz+(board[1]-lit[1])/2+lit_off[1]]) cube([lit[0],glass_rise+0.2,lit[1]]);
  color("#3a4a8c") translate([(FW-lipo[0])/2,D_board+wire_gap+0.3+foam_liner,(FH-lipo[1])/2]) cube([lipo[0],lipo[2],lipo[1]]);
  // BNO085 compass sitting in its cradle at the -X end
  color("#12a07a") translate([OX+wall+0.6, cp_y+crib_wall+0.3, cp_z0+crib_wall+0.3]) cube([comp[2], comp[1]-0.6, comp[0]-0.6]);
}
module display_group(){ color("#8d9892") bezel(); ghost(); color("#a4ada7") tub(); }

// ----------------------------------------------------------- output
if(part=="bezel") rotate([-90,0,0]) bezel();
else if(part=="tub") tub();
else if(part=="mount") mount();
else if(part=="jaw") jaw();
else if(part=="carrier") carrier();
else if(part=="clamp_test"){ intersection(){ union(){ mount(); jaw(); } translate([CLX-jw-1,armY-clamp_l/2-1,clz-24]) cube([2*jw+2,clamp_l+2,46]); } }
else if(part=="seal_test"){ intersection(){ union(){ bezel(); tub(); } translate([-1,-front_th-1,-1]) cube([FW+2,front_th+flange_th+lip+5,FH+2]); } }
else if(part=="section"){   // clamp cut at the bolt plane: shows the captured hex nut + washer seat + drop-in slot
  intersection(){ union(){ mount(); color("#70797f") jaw(); }
                  translate([CLX-jw-2, armY, zb-6]) cube([2*jw+4, 30, clamp_top-zb+14]); } }
else if(part=="wiring_check"){   // horizontal cut through the compass: shows board -> wire-gap -> LiPo depth + the compass cradle
  intersection(){ union(){ color("#a4ada7") tub(); ghost(); }
                  translate([-1,-1,cp_z0+cp_h/2-8]) cube([FW+2, D_tub+2, 16]); } }
else if(part=="qr_check"){   // display + carrier slide onto the rifle-mounted arm/clamp; pull the pin to lift it off for rucking
  display_group();
  color("#c6a15a") carrier();
  color("#9aa6ad") mount(); color("#70797f") jaw();
  clamp_thumbscrew(); rail_stub();
}
else if(part=="exploded"){
  display_group();
  color("#c6a15a") translate([14,0,0]) carrier();
  color("#9aa6ad") translate([34,0,0]) mount();
  color("#70797f") translate([54,0,0]) jaw();
}
else if(part=="optic_check"){   // top-down: shows the display clearing the side of a scope objective
  display_group();
  color("#c6a15a") carrier(); color("#9aa6ad") mount(); color("#70797f") jaw();
  clamp_thumbscrew(); rail_stub(); optic();
}
else { // assembly
  display_group();
  color("#c6a15a") carrier();
  color("#9aa6ad") mount(); color("#70797f") jaw();
  clamp_thumbscrew(); rail_stub();
}
