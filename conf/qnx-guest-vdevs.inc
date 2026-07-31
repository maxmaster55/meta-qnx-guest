# qnx-guest-vdevs.inc -- the addresses the guest and the host must agree on.
#
#     require conf/qnx-guest-vdevs.inc
#
# A guest under qvm has no PCI and no device tree to enumerate, so every virtual
# device is at an address someone chose, and BOTH sides have to be told the same
# number: the host writes it into the .qvmconf, the guest passes it to a driver
# on a command line. Nothing checks that they match. A guest whose driver is
# pointed at the wrong address does not fail loudly -- it attaches to nothing and
# the device silently never appears.
#
# That is not hypothetical. The virtio-gpu stanza was once dropped from the
# .qvmconf while /scripts/graphics-virtio-start.sh went on asking for a GPU at
# 0x1c0e0000, and an entropy vdev was given that address instead. The guest
# booted, reported no error, and rendered nothing.
#
# ---------------------------------------------------------------------------
# The rule for what belongs in this file
# ---------------------------------------------------------------------------
# A value goes here when MORE THAN ONE FILE has to agree on it. Nothing else.
#
# Addresses used only inside qnx-guest.qvmconf -- the pl011, the shmem region,
# the watchdog, the entropy source, the guest-to-guest link -- stay written out
# in that file, where the interrupt map at the top of it is the documentation.
# Lifting them here would add indirection without removing a way to be wrong.
#
# ---------------------------------------------------------------------------
# Interrupt map
# ---------------------------------------------------------------------------
# Reproduced from qnx-guest.qvmconf so that a value changed here can be checked
# against the whole allocation without opening another file. gic:46 is the
# highest in use; 47 up are free.
#
#   gic:37  pl011                 0x1c090000
#   gic:38  shmem                 0x1c050000
#   gic:39  virtio-gpu            0x1c0e0000   <- here
#   gic:40  virtio-net host       0x1c0c0000
#   gic:41  virtio-blk qvmdisk0   0x1c0d0000
#   gic:42  virtio-console        0x20000000   <- here
#   gic:43  virtio-net guest      0x20040000
#   gic:44  pass SPI0             mem:0x1f00050000  (host irq 179)
#   gic:45  virtio-blk rootfs     0x1c0b0000   <- here
#   gic:46  virtio-entropy        0x1c0a0000

# ---------------------------------------------------------------------------
# Console
# ---------------------------------------------------------------------------
# Agreed between: qnx-guest.qvmconf (vdev virtio-console) and the `devc-virtio`
# line in qnx-guest.build.in, which takes them as one "loc,intr" argument.
QNX_GUEST_CONSOLE_LOC ?= "0x20000000"
QNX_GUEST_CONSOLE_INTR ?= "42"
QNX_GUEST_CONSOLE ?= "${QNX_GUEST_CONSOLE_LOC},${QNX_GUEST_CONSOLE_INTR}"

# ---------------------------------------------------------------------------
# Data disk (rootfs.img)
# ---------------------------------------------------------------------------
# Agreed between: qnx-guest.qvmconf (vdev virtio-blk, name virtio-blk_rootfs)
# and the `devb-virtio ... smem=,irq=` line in .rootfs-mount.sh.
#
# Get this wrong and /dev/vblk0 never appears, so nothing mounts at / and the
# guest comes up without Qt, without the graphics stack, and without an
# explanation beyond "data disk did not mount".
QNX_GUEST_ROOTFS_LOC ?= "0x1c0b0000"
QNX_GUEST_ROOTFS_INTR ?= "45"

# ---------------------------------------------------------------------------
# The paravirtual GPU
# ---------------------------------------------------------------------------
# Agreed between: qnx-guest.qvmconf (vdev virtio-gpu) and
# /scripts/graphics-virtio-start.sh, which passes them to drm-virtio as
#
#     -o virtio.device=<size>@<loc>:<intr>
#
# CAREFUL: that script lives in the qnx-host-conf repository and hardcodes
# 0x1c0e0000 and 39. Changing these two values without changing it there gives a
# guest with a GPU the driver does not look for -- no /dev/dri/card0, no Screen,
# no cluster, and nothing on the console that names the cause.
QNX_GUEST_GPU_LOC ?= "0x1c0e0000"
QNX_GUEST_GPU_INTR ?= "39"

# ---------------------------------------------------------------------------
# Scanout
# ---------------------------------------------------------------------------
# The panel size, and the widest-reaching value here -- FOUR places must agree:
#
#   1. qnx-guest.qvmconf          scanout-width / scanout-height   (from here)
#   2. graphics-virtio-mmio.conf  video-mode, in the guest         (conf repo)
#   3. graphics-host-rpi5.conf    display 1 video-mode, on the host(conf repo)
#   4. the physical panel
#
# vdev-virtio-gpu scans the guest out 1:1 with no scaling, so a mismatch does
# not stretch or letterbox -- it puts the guest in a corner of the display, or
# crops it, which reads as a broken application rather than a wrong number.
#
# Only (1) comes from here. Changing the panel means editing the two .conf files
# in the qnx-host-conf repository as well.
QNX_GUEST_SCANOUT_DISPLAY ?= "1"
QNX_GUEST_SCANOUT_WIDTH ?= "1024"
QNX_GUEST_SCANOUT_HEIGHT ?= "600"

# ---------------------------------------------------------------------------
# Shared memory
# ---------------------------------------------------------------------------
# Agreed between: qnx-guest.qvmconf (vdev shmem) and /scripts/shmem-start.sh,
# which passes both to shmem-guest as positional arguments.
QNX_GUEST_SHMEM_LOC ?= "0x1c050000"
QNX_GUEST_SHMEM_INTR ?= "38"

# ---------------------------------------------------------------------------
# Watchdog
# ---------------------------------------------------------------------------
# Agreed between: qnx-guest.qvmconf (vdev wdt-sp805) and the two watchdog
# scripts, which reach the device by absolute address -- wdtkick -a <loc> to
# start it, and `io 32 <loc>+8 0` to stop it. No interrupt: the SP805 model
# resets the guest rather than raising one.
QNX_GUEST_WDT_LOC ?= "0x1c0f0000"

# The SP805's control register, which watchdog-stop.sh clears to disable the
# timer. Derived rather than written out: base + 8 as a second literal is a
# second thing to get wrong, and textual concatenation is worse still --
# "<loc>8" reads as 0x1c0f00008, one hex digit too long, which io would happily
# write to an address the watchdog is not at.
QNX_GUEST_WDT_CTRL ?= "${@'0x%08x' % (int(d.getVar('QNX_GUEST_WDT_LOC'), 16) + 8)}"

# ---------------------------------------------------------------------------
# Scratch block device
# ---------------------------------------------------------------------------
# Agreed between: qnx-guest.qvmconf (vdev virtio-blk, name virtio-blk_qvmdisk0)
# and /scripts/block-start.sh, which passes them to devb-virtio as smem= and
# irq=. Backed by the RAM disk the host creates on its own boot line.
QNX_GUEST_SCRATCH_LOC ?= "0x1c0d0000"
QNX_GUEST_SCRATCH_INTR ?= "41"

# ---------------------------------------------------------------------------
# SPI
# ---------------------------------------------------------------------------
# The guest's interrupt for the passed-through SPI0. Agreed between
# qnx-guest.qvmconf (`intr gic:44=179`, which maps this onto the host's 179) and
# the irq= line in the guest's own /var/spi.conf.
#
# Note that the two sides of that mapping are different numbers, and that the
# guest's spi.conf is NOT the host's: same silicon, different input_clock,
# clock_rate, word_width and cpha. Copying one over the other gets a bus that
# initialises and transfers nothing.
QNX_GUEST_SPI_INTR ?= "44"

# ---------------------------------------------------------------------------
# Memory
# ---------------------------------------------------------------------------
# Guest RAM, as qvm's "base,size". Used only by the .qvmconf today, and here
# because it is the value most likely to be tuned and the least obvious where to
# tune it.
#
# 2G is not a measured requirement; it is the reference project's figure, kept
# because the Qt runtime and the virgl graphics buffers want headroom and
# shrinking it is a change worth testing on hardware on its own.
QNX_GUEST_RAM ?= "0x80000000,2G"
