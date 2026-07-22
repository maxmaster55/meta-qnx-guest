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

# frame-router and rpi-gpio come from meta-qnx-hyp: they are built once and go
# into both the host and its guests.
QNX_IFS_INSTALL = "spi-loopback frame-router rpi-gpio \
                   motor-ai-client motor-ai-server \
                   commonapi-someip commonapi-core vsomeip boost"

# The SOME/IP runtimes are listed explicitly because the applications link
# against them: an image with motor_ai_client but no libCommonAPI.so has a
# binary that cannot load. ~9.5MB, which an IFS can carry.
#
# The project keeps these on the guest's rootfs.img instead, and that becomes
# the right answer once Qt is in the picture -- an IFS is RAM-resident, and the
# Qt deploy tree is far too large for one.

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
