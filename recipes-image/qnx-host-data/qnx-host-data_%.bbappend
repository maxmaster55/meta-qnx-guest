# Put this layer's guest onto the host's data partition, at the same paths the
# QNX hypervisor project uses:
#
#   /guests/guest-1/qnx-guest.ifs
#   /guests/guest-1/qnx-guest.qvmconf
#   /guests/guest-1/rootfs.img
#
# This is a bbappend rather than an edit to the host data recipe on purpose.
# meta-qnx-guest already depends on meta-qnx-hyp for the shared application
# recipes; having the host data reach back for a guest image would make those
# two layers depend on each other. Appending here keeps the arrow pointing one
# way, and means a build without this layer simply produces a data partition
# with no guests rather than failing.

FILESEXTRAPATHS:prepend := "${THISDIR}/../qnx-guest-image/files:"

SRC_URI += "file://qnx-guest.qvmconf"

QNX_GUEST_NAME ?= "guest-1"

# Everything the guest needs lands in /guests/<name>/, the directory qvm is
# launched from on the host:
#   qnx-guest.ifs       the guest image
#   qnx-guest.qvmconf   its vdev configuration
#   rootfs.img           the data disk the qvmconf attaches (Qt + large payloads)
QNX_ROOTFS_EXTRA += "\
/guests/${QNX_GUEST_NAME}/qnx-guest.ifs = ${DEPLOY_DIR_IMAGE}/qnx-guest.ifs\n\
/guests/${QNX_GUEST_NAME}/qnx-guest.qvmconf = ${WORKDIR}/qnx-guest.qvmconf\n\
/guests/${QNX_GUEST_NAME}/rootfs.img = ${DEPLOY_DIR_IMAGE}/rootfs.img\
"

# Both guest artifacts must be deployed before this rootfs can read them.
do_generate_rootfs_buildfile[depends] += "qnx-guest-image:do_deploy qnx-guest-rootfs:do_deploy"

# The base recipe sets a fixed 512M -- adding the ~366 MB rootfs.img makes a
# fixed size a maintenance burden, so size it from what actually goes on it.
QNX_ROOTFS_SIZE = "auto"
