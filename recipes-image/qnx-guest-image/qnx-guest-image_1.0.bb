SUMMARY = "QNX hypervisor guest image"
DESCRIPTION = "A guest that runs under qvm on the hypervisor host: virtio console, \
virtio networking, and applications. Modelled on guest-1 of the QNX hypervisor \
project."
LICENSE = "CLOSED"

SRC_URI = "file://qnx-guest.build.in"

inherit qnx-ifs

S = "${WORKDIR}"
B = "${WORKDIR}/build"

QNX_IFS_NAME = "qnx-guest"
QNX_IFS_TEMPLATE = "${S}/qnx-guest.build.in"

# Two of these are groups rather than single recipes (qnx-packagegroup.bbclass),
# expanded to their members when the image is generated:
#
#   packagegroup-qnx-hyp-common  rpi-gpio -- shared with the host image, which
#                                installs the same group. The two images can no
#                                longer disagree about what "shared" means.
#   packagegroup-qnx-someip      vsomeip, the CommonAPI runtimes and boost. The
#                                applications link against them, so an image
#                                with motor_ai_client but no libCommonAPI.so has
#                                a binary that cannot load. ~9.5MB, which an IFS
#                                can carry.
#
# The project keeps the SOME/IP runtime on the guest's rootfs.img instead, and
# that becomes the right answer once Qt is in the picture -- an IFS is
# RAM-resident, and the Qt deploy tree is far too large for one. Moving it is
# then one line: the same group name, in QNX_ROOTFS_INSTALL instead.
# The guest needs the same base runtime and stacks as the host; what differs is
# the driver, which the template still names (devs-vtnet_mmio, devb-virtio).
# The same SDP components the host carries. A guest under qvm is a full QNX
# system, not a cut-down one -- the reference guest image ships ssh, PAM, the
# PCI stack and the diagnostic tools exactly as the host does, and the only
# things that genuinely differ are the drivers (virtio rather than real
# hardware) and which applications run.
#
# qnx-guest-conf, NOT qnx-host-conf. They fetch the same repository, but the
# host component also carries the board's wifi configuration -- wpa_supplicant
# .conf and the network PSK with it -- and a component is all-or-nothing, so
# installing it here put the PSK inside a guest that has no radio. It also put
# the host's own graphics-host-rpi5.conf and host-graphics-start.sh in, which
# describe a display this guest cannot drive.
QNX_IFS_INSTALL = "qnx-base-runtime qnx-block qnx-io-sock qnx-pci \
                   qnx-net-tools qnx-diag-tools qnx-fs-tools qnx-login \
                   qnx-ssh qnx-usb qnx-screen qnx-gfx-demos qnx-guest-conf \
                   spi-loopback motor-controller packagegroup-qnx-hyp-common \
                   motor-ai-client motor-ai-server motor-data-producer \
                   packagegroup-qnx-someip"

# ---------------------------------------------------------------------------
# Boot configuration
# ---------------------------------------------------------------------------
# Deliberately empty. A guest is loaded by qvm at a high address as ELF with the
# generic startup, which is exactly what meta-qnx defaults to -- unlike the
# hypervisor host, which overrides every one of those.

# The vdev addresses this image and the host's copy of the .qvmconf both have to
# agree on -- the console, the data disk, the GPU and the scanout size. Required
# by the qnx-host-data bbappend as well, which is the point: one file, both
# consumers, no way for them to disagree.
require conf/qnx-guest-vdevs.inc

# The other end of the host's vp0 interface. The host's QNX_HOST_GUEST_IP is
# this guest's gateway, and its QNX_HOST_GUEST_NET has to contain this address.
QNX_GUEST_IP ?= "10.0.0.2"
QNX_GUEST_GATEWAY ?= "10.0.0.1"

do_configure[noexec] = "1"
do_compile[noexec] = "1"
