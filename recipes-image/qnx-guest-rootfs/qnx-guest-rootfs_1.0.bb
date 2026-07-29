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
QNX_ROOTFS_INSTALL = "qt-cluster qnx-screen-virtio"

# ~126 MB of qt-cluster today; "auto" grows the image if the graphics stack is
# added later rather than failing with "does not fit".
QNX_ROOTFS_SIZE = "auto"
QNX_ROOTFS_MIN = "192M"

do_configure[noexec] = "1"

