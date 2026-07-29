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

# The same addresses the guest image builds against. Both sides require this one
# file, so the .qvmconf written here and the driver command lines baked into the
# guest cannot drift apart -- which is the failure this replaced: a .qvmconf that
# had lost its virtio-gpu while the guest went on looking for one.
require conf/qnx-guest-vdevs.inc

# The .qvmconf ships with at-sign placeholders and is expanded here, the same
# way the IFS and disk templates are (qnx_expand_template, in qnx-sdp.bbclass).
#
# It is expanded rather than copied because a copy is what let the two sides
# disagree. The expansion is fatal on an unset marker, so a typo stops the build
# instead of producing a guest that boots and quietly cannot see a device.
QNX_GUEST_QVMCONF_SRC = "${WORKDIR}/qnx-guest.qvmconf"
QNX_GUEST_QVMCONF = "${WORKDIR}/qnx-guest.qvmconf.expanded"

python qnx_guest_expand_qvmconf() {
    import os

    src = d.getVar('QNX_GUEST_QVMCONF_SRC')
    out = d.getVar('QNX_GUEST_QVMCONF')

    bb.utils.mkdirhier(os.path.dirname(out))
    with open(out, 'w') as f:
        f.write(qnx_expand_template(d, src))

    bb.note("expanded %s -> %s" % (src, out))
}

# Before the rootfs build file is written, since that names the expanded file.
do_generate_rootfs_buildfile[prefuncs] += "qnx_guest_expand_qvmconf"

# Varflags and file contents are invisible to task signatures on their own, so
# name the variables the expansion actually reads. Without this, changing the
# GPU address would not rebuild the data partition.
do_generate_rootfs_buildfile[vardeps] += "\
    QNX_GUEST_RAM QNX_GUEST_CONSOLE_LOC QNX_GUEST_CONSOLE_INTR \
    QNX_GUEST_ROOTFS_LOC QNX_GUEST_ROOTFS_INTR \
    QNX_GUEST_GPU_LOC QNX_GUEST_GPU_INTR \
    QNX_GUEST_SCANOUT_DISPLAY QNX_GUEST_SCANOUT_WIDTH QNX_GUEST_SCANOUT_HEIGHT"

# Everything the guest needs lands in /guests/<name>/, the directory qvm is
# launched from on the host:
#   qnx-guest.ifs       the guest image
#   qnx-guest.qvmconf   its vdev configuration
#   rootfs.img           the data disk the qvmconf attaches (Qt + large payloads)
QNX_ROOTFS_EXTRA += "\
/guests/${QNX_GUEST_NAME}/qnx-guest.ifs = ${DEPLOY_DIR_IMAGE}/qnx-guest.ifs\n\
/guests/${QNX_GUEST_NAME}/qnx-guest.qvmconf = ${QNX_GUEST_QVMCONF}\n\
/guests/${QNX_GUEST_NAME}/rootfs.img = ${DEPLOY_DIR_IMAGE}/rootfs.img\
"

# Both guest artifacts must be deployed before this rootfs can read them.
do_generate_rootfs_buildfile[depends] += "qnx-guest-image:do_deploy qnx-guest-rootfs:do_deploy"

# The base recipe sets a fixed 512M -- adding the ~366 MB rootfs.img makes a
# fixed size a maintenance burden, so size it from what actually goes on it.
QNX_ROOTFS_SIZE = "auto"
