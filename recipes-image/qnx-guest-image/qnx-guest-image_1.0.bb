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
#   packagegroup-qnx-hyp-common  frame-router, rpi-gpio -- shared with the host
#                                image, which installs the same group. The two
#                                images can no longer disagree about what
#                                "shared" means.
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
QNX_IFS_INSTALL = "qnx-base-runtime qnx-block qnx-io-sock \
                   spi-loopback packagegroup-qnx-hyp-common \
                   motor-ai-client motor-ai-server \
                   packagegroup-qnx-someip"

# ---------------------------------------------------------------------------
# Boot configuration
# ---------------------------------------------------------------------------
# Deliberately empty. A guest is loaded by qvm at a high address as ELF with the
# generic startup, which is exactly what meta-qnx defaults to -- unlike the
# hypervisor host, which overrides every one of those.

# The virtio-console vdev the host provides. Must match "vdev virtio-console /
# loc 0x20000000 / intr gic:42" in this guest's .qvmconf.
QNX_GUEST_CONSOLE ?= "0x20000000,42"

# The other end of the host's vp0 interface. The host's QNX_HOST_GUEST_IP is
# this guest's gateway, and its QNX_HOST_GUEST_NET has to contain this address.
QNX_GUEST_IP ?= "10.0.0.2"
QNX_GUEST_GATEWAY ?= "10.0.0.1"

do_configure[noexec] = "1"
do_compile[noexec] = "1"
