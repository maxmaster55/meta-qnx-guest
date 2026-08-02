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
#
# Two BSPs, and both are needed for the same reason: this guest is handed real
# hardware, not only virtual devices.
#
#   qnx-hyp-guest-bsp   shmem-guest and wdtkick, which drive the shmem and
#                       wdt-sp805 vdevs qvm offers.
#   qnx-rpi5-bsp        gpio-rp1 and spi-dwc. A board BSP in a guest reads
#                       oddly and is right: the .qvmconf passes the RP1's SPI
#                       and GPIO register windows straight through, so the guest
#                       drives the same silicon with the same binaries the host
#                       would use.
#
# Both set QNX_IFS_AUTO_ENTRIES = "0", so installing them contributes nothing on
# its own -- the template names the handful of binaries it wants. That is the
# point: a BSP ships far more than any one image should carry.
QNX_IFS_INSTALL = "qnx-base-runtime qnx-block qnx-io-sock qnx-pci \
                   qnx-net-tools qnx-diag-tools qnx-fs-tools qnx-login \
                   qnx-ssh qnx-usb qnx-screen qnx-gfx-demos qnx-guest-conf \
                   qnx-hyp-guest-bsp qnx-rpi5-bsp \
                   spi-loopback motor-controller packagegroup-qnx-hyp-common \
                   motor-ai-client motor-data-producer \
                   packagegroup-qnx-someip"

# motor-ai-server is deliberately NOT here. The pair is split across the two
# guests: the client runs on QNX because that is where the SPI motor data
# arrives, and the service runs on Linux (guest-2, bmo-image-ai) because that is
# where the AI runtime lives. Its vsomeip configuration says the same thing --
# it binds 10.0.2.2, which is guest-2's end of the guest_to_guest link.
#
# The recipe in this layer is kept rather than deleted: it still builds, and
# installing it here is the quickest way to run both halves inside one guest
# when bisecting a SOME/IP problem without the hypervisor in the picture.

# ---------------------------------------------------------------------------
# Boot configuration
# ---------------------------------------------------------------------------
# The load address, format and startup program are all left at meta-qnx's
# defaults: a guest is loaded by qvm at a high address as ELF with the generic
# startup, which is exactly what those defaults are for -- unlike the hypervisor
# host, which overrides every one of them.
#
# The search paths are not left at the defaults, because the defaults are
# narrower than what this guest installs:
#
#   /usr/libexec   sshd execs its per-connection helpers from here -- sshd-session
#                  and sftp-server, both from the qnx-ssh component. Off PATH,
#                  sshd accepts a connection and then cannot service it.
#   /lib/dll/pci   where pci-server's hardware modules live, from qnx-pci. A
#                  guest sees PCI through qvm's generic FDT host bridge, so it
#                  installs that component and needs its modules findable.
#   /proc/boot/lib carried because the reference guest carries it.
#
# Both lists match qnx_guests/images/guest-1 exactly, including the order: /sbin
# ahead of /bin, which is what makes `screen` resolve to the component's
# /sbin/screen rather than anything shadowing it.
QNX_IFS_PATH = "/proc/boot:/sbin:/bin:/usr/bin:/usr/sbin:/usr/libexec"
QNX_IFS_LD_LIBRARY_PATH = "/proc/boot:/lib:/usr/lib:/lib/dll:/lib/dll/pci:/proc/boot/lib"

# ---------------------------------------------------------------------------
# Qt, for every application rather than one launcher
# ---------------------------------------------------------------------------
# These four were qt-cluster's run.sh and nowhere else, which meant the next Qt
# application on this guest would rediscover each of them the same way: by
# failing. They are properties of the guest -- its window system, its display,
# its lack of a GPU, where its fonts are -- not of the cluster, so they belong
# to the image. run.sh still sets them, and still wins, so nothing about the
# cluster changes.
#
#   QT_QPA_PLATFORM             Qt's compiled-in default is the Linux one (xcb),
#                               so without this a Qt application on QNX dies at
#                               startup looking for an X11 plugin that is
#                               neither deployed nor meaningful here:
#
#                                 qt.qpa.plugin: Could not find the Qt platform
#                                 plugin "xcb"
#
#                               The QNX plugin is libqqnx.so, and it talks to
#                               Screen.
#
#   QT_QPA_FONTDIR              Where font-dejavu puts its .ttf files, on the
#                               data disk. This is already Qt's compiled-in
#                               default (LibrariesPath + "/fonts"), so it is
#                               belt and braces -- but it is the value the
#                               failure names, and stating it makes the image
#                               and the font recipe visibly agree:
#
#                                 QFontDatabase: Cannot find font directory
#                                 /usr/lib/fonts.
#
#                               with every glyph drawn as a box afterwards.
#
#   QT_QUICK_BACKEND=software   qtbase here is built no-opengl (see meta-qnx's
#                               qtbase bbappend), so Qt Quick's default RHI path
#                               has no backend to run on. A board with a working
#                               GPU stack drops this line.
#
#   QQNX_PHYSICAL_SCREEN_SIZE   millimetres. Screen reports 0x0 for a display
#                               with no EDID, which is the virtio case, and Qt
#                               then computes a nonsense DPI -- text comes out
#                               microscopic or enormous rather than absent, so
#                               it does not look like a configuration problem.
#
# Deliberately NOT here: QT_PLUGIN_PATH, QML2_IMPORT_PATH and additions to
# LD_LIBRARY_PATH. Each application on this guest ships its own Qt, so those are
# per-application prefixes; a global one would point every self-contained
# application at one directory and load a platform plugin built against a
# different Qt.
QNX_IFS_ENV += "QT_QPA_PLATFORM=qnx"
QNX_IFS_ENV += "QT_QPA_FONTDIR=/usr/lib/fonts"
QNX_IFS_ENV += "QT_QUICK_BACKEND=software"
QNX_IFS_ENV += "QQNX_PHYSICAL_SCREEN_SIZE=150,90"

# The vdev addresses this image and the host's copy of the .qvmconf both have to
# agree on -- the console, the data disk, the GPU and the scanout size. Required
# by the qnx-host-data bbappend as well, which is the point: one file, both
# consumers, no way for them to disagree.
require conf/qnx-guest-vdevs.inc

# The other end of the host's vp0 interface. The host's QNX_HOST_GUEST_IP is
# this guest's gateway, and its QNX_HOST_GUEST_NET has to contain this address.
QNX_GUEST_IP ?= "10.0.0.2"
QNX_GUEST_GATEWAY ?= "10.0.0.1"

# The direct link to the Linux guest, on the guest_to_guest virtio-net vdev --
# no host, no routing, the two guests on a wire. Until this was set the vdev
# existed and the interface came up nameless and unaddressed, so SOME/IP between
# the two halves of the motor-AI pair could not work at all.
#
# 10.0.2.1 is not a free choice. Both vsomeip configurations name it literally:
# the client binds "unicast": "10.0.2.1" and dials the server at "10.0.2.2".
# The other end is set by network-setup in meta-bmo (the Linux guest's
# qguest0), and the two have to stay on the same /24.
QNX_GUEST_PEER_IP ?= "10.0.2.1"

# The template is tracked by its own checksum, but the values substituted into
# it are not -- so without this, changing an address in local.conf would leave
# the IFS untouched and the running guest on the old one.
do_generate_buildfile[vardeps] += "QNX_GUEST_IP QNX_GUEST_GATEWAY QNX_GUEST_PEER_IP"

do_configure[noexec] = "1"
do_compile[noexec] = "1"
