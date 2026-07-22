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
QNX_IFS_INSTALL = "spi-loopback frame-router rpi-gpio"

# ---------------------------------------------------------------------------
# Boot configuration
# ---------------------------------------------------------------------------
# Deliberately empty. A guest is loaded by qvm at a high address as ELF with the
# generic startup, which is exactly what meta-qnx defaults to -- unlike the
# hypervisor host, which overrides every one of those.

# The virtio-console vdev the host provides. Must match "vdev virtio-console /
# loc 0x20000000 / intr gic:42" in this guest's .qvmconf.
QNX_GUEST_CONSOLE ?= "0x20000000,42"

do_configure[noexec] = "1"
do_compile[noexec] = "1"
