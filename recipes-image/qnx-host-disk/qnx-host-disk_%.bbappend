# Put this layer's guest onto the host's data partition, at the same paths the
# QNX hypervisor project uses:
#
#   /guests/guest-1/qnx-guest.ifs
#   /guests/guest-1/qnx-guest.qvmconf
#
# The IFS is renamed on the way in, because the .qvmconf loads it by a relative
# name ("load qnx-guest.ifs") and therefore expects it beside itself.
#
# This is a bbappend rather than an edit to the host disk recipe on purpose.
# meta-qnx-guest already depends on meta-qnx-hyp for the shared application
# recipes; having the host disk reach back for a guest image would make those
# two layers depend on each other. Appending here keeps the arrow pointing one
# way, and means a build without this layer simply produces a disk with no
# guests rather than failing.

FILESEXTRAPATHS:prepend := "${THISDIR}/../qnx-guest-image/files:"

SRC_URI += "file://qnx-guest.qvmconf"

QNX_GUEST_NAME ?= "guest-1"

# Everything the guest needs lands in /guests/<name>/, the directory qvm is
# launched from on the host:
#   qnx-guest.ifs       the guest image
#   qnx-guest.qvmconf   its vdev configuration
#   rootfs.img          the data disk the qvmconf attaches (Qt + large payloads)
#
# The data partition is built by mkqnx6fsimg, which resolves these sources
# itself, so absolute paths are used rather than bare names.
QNX_DISK_DATA_EXTRA += "\
/guests/${QNX_GUEST_NAME}/qnx-guest.ifs = ${DEPLOY_DIR_IMAGE}/qnx-guest.ifs\n\
/guests/${QNX_GUEST_NAME}/qnx-guest.qvmconf = ${WORKDIR}/qnx-guest.qvmconf\n\
/guests/${QNX_GUEST_NAME}/rootfs.img = ${DEPLOY_DIR_IMAGE}/rootfs.img\
"

# Both guest artifacts must be deployed before this disk can read them.
do_generate_diskfiles[depends] += "qnx-guest-image:do_deploy qnx-guest-rootfs:do_deploy"

# meta-qnx-hyp fixes the data partition at 512M, which suited a guestless disk.
# Adding the ~366 MB rootfs.img (and, later, the graphics stack) makes a fixed
# size a maintenance burden, so size it from what actually goes on it. "auto"
# sums the guest IFS, the qvmconf and rootfs.img and adds slack; pin it larger
# in local.conf if the guest needs runtime write headroom on the data partition.
QNX_DISK_DATA_SIZE = "auto"
