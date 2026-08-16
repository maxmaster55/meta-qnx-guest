SUMMARY = "Guest data disk (rootfs.img): payloads too large for the RAM-resident IFS"
DESCRIPTION = "A bare QNX6 filesystem the guest is handed as a virtio-blk disk \
and union-mounts at / early in boot. It carries the Qt cluster deploy tree (and, \
as they come online, the virtio graphics stack and the SOME/IP libraries) -- \
hundreds of megabytes that cannot live in an IFS, which is copied into guest RAM \
whole. Modelled on qnx_guests/images/guest-1/rootfs.build."
LICENSE = "CLOSED"

SRC_URI = "file://qnx-guest-rootfs.build.in"

inherit qnx-rootfs

S = "${WORKDIR}"

# Deployed as rootfs.img, which is the name the guest's .qvmconf loads and the
# host disk places beside it.
QNX_ROOTFS_NAME = "rootfs"
QNX_ROOTFS_TEMPLATE = "${S}/qnx-guest-rootfs.build.in"

# What rides on the disk. qt-cluster's deploy tree is the whole payload today;
# add qnx-screen-virtio for the graphics stack, and the SOME/IP libraries, as
# each is ready -- one word each, and the template already routes the graphics
# tree if it is present.
# qnx-screen-virtio carries drm-virtio, the guest's virtio-gpu driver, plus the
# EGL/GLES stack that goes with it. /scripts/graphics-virtio-start.sh execs
# drm-virtio by name, so without this the guest reports
#
#     drm-virtio: cannot execute - No such file or directory
#
# It is ~279MB unpacked -- virtio_dri.so alone is 67MB -- which is why it goes
# here and not in the IFS: an IFS is copied into guest RAM whole at boot. The
# guest union-mounts this disk at / early enough that drm-virtio is on PATH by
# the time the graphics script runs.
#
# font-dejavu puts .ttf files at /usr/lib/fonts, which is where Qt looks when
# nothing tells it otherwise -- so any Qt application on this guest has fonts,
# not just the one whose launcher deploys its own. Without it every glyph is
# drawn as a box and Qt says so once, early:
#
#     QFontDatabase: Cannot find font directory /usr/lib/fonts.
#
# It rides here rather than in the IFS because 5.4MB of font data in an image
# that is copied into guest RAM whole is 5.4MB of RAM.
# ssh-hostkeys supplies this guest's pre-generated ssh host key, so it keeps one
# identity from first boot and the host can pre-accept it. See the template.
QNX_ROOTFS_INSTALL = "qt-cluster qnx-screen-virtio font-dejavu ssh-hostkeys"

# Fixed at 8G rather than "auto".
#
# "auto" sized the image to its contents -- ~126 MB of qt-cluster, so about
# 750 MB once formatted -- which is the right answer for a disk that only ever
# holds what the build put there. This one does not: it is the guest's writable
# filesystem, where motor_recorder writes CSV recordings and captures
# accumulate, and a disk sized to its initial contents leaves nowhere for them
# to go.
#
# The cost is on the SD card, not in RAM: rootfs.img is a virtio block device
# the guest mounts, not something copied into guest memory the way the IFS is.
# It does have to fit alongside the other guest and the host.
#
# 2G, not 8G. The image is sparse -- an 8G filesystem holding ~500MB occupies
# ~485MB on disk -- so the figure is nearly free in the build now that the disk
# pipeline preserves holes (see qnx-disk.bbclass). What is NOT free is the
# flashing: dd writes the nominal size to the card whatever the holes say, and
# every extra gigabyte here is a gigabyte written on every flash of a
# development image.
#
# Raise it when the recordings actually need the room. If they need it
# permanently, the better shape is a separate volume created on the card at
# first boot rather than a bigger image -- mkqnx6fs has -x, "create an
# expandable filesystem", which works on block devices and so cannot be used by
# the file-based mkqnx6fsimg at build time.
# "=", not "?=". qnx-rootfs.bbclass sets QNX_ROOTFS_SIZE ?= "auto" and the
# inherit runs first, so a weak assignment here loses to it silently -- which
# it did: the image came out 756M, the auto size, with no room for recordings
# and no error to say so. Override per build in local.conf instead:
#
#     QNX_ROOTFS_SIZE:pn-qnx-guest-rootfs = "8G"
# 1G. Recordings do not live here any more -- /record is its own virtio-blk
# device backed by a file the host creates on the card (QNX_GUEST_RECORD_* in
# conf/qnx-guest-vdevs.inc), so this image only has to hold the OS payloads:
# the Qt cluster and the applications, about 500 MB formatted.
#
# That is the point of the split. Space here is copied into the host data
# partition by mkqnx6fsimg on every build, at roughly 100s per GB. Space on the
# recording volume is a sparse file made once on the card. Growing the
# recording area now costs nothing at build time, which is what it should have
# cost all along.
QNX_ROOTFS_SIZE = "1G"

# ---------------------------------------------------------------------------
# Why this number is the build's clock
# ---------------------------------------------------------------------------
# After the image pipeline stopped copying things it did not need to (see
# qnx-disk.bbclass), one task is 73% of an image rebuild:
#
#     200.9s  qnx-host-data : do_compile      <- mkqnx6fsimg
#      15.6s  qnx-sdp       : do_check_sdp
#      10.2s  qnx-host-image: do_mkifs
#       8.7s  qnx-host-disk : do_compile
#
# That task builds the host's data partition, and this rootfs.img is a FILE
# INSIDE it. mkqnx6fsimg therefore reads the whole thing -- holes included --
# and writes it into a new filesystem. Roughly 100s per GB, so 2G costs ~200s
# and 8G would cost ~800s. Nothing else in the build scales with this number
# any more; this one does, linearly.
#
# The fix, if the recording area genuinely needs to be large, is not a bigger
# number here. It is to stop embedding it:
#
#   - give the guest rootfs its own partition on the disk rather than making it
#     a file inside the host's filesystem, so diskimage concatenates two sparse
#     images instead of one filesystem copying another in; or
#   - create the recording volume on the card at first boot, where the cost is
#     paid once on the device instead of on every build. mkqnx6fs -x makes an
#     expandable filesystem, block devices only, which is why mkqnx6fsimg
#     cannot do it at build time.
#
# Both are real changes needing hardware testing. Until then, keep this small
# for development and raise it per build in local.conf.
QNX_ROOTFS_MIN = "192M"

do_configure[noexec] = "1"

