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
# The guest's display configuration is qnx-guest-conf, NOT qnx-host-conf -- it
# is on rootfs.img now (see above), but the distinction still holds wherever it
# is installed. They fetch the same repository, but the host component also
# carries the board's wifi configuration -- wpa_supplicant.conf and the network
# PSK with it -- and a component is all-or-nothing, so installing it here put
# the PSK inside a guest that has no radio. It also put the host's own
# graphics-host-rpi5.conf and host-graphics-start.sh in, which describe a
# display this guest cannot drive.
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
                   qnx-ssh qnx-usb qnx-screen qnx-gfx-demos \
                   qnx-hyp-guest-bsp qnx-rpi5-bsp \
                   packagegroup-qnx-hyp-common \
                   mosquitto \
                   packagegroup-qnx-someip"

# ---------------------------------------------------------------------------
# THE APPLICATIONS ARE NOT IN THE LIST ABOVE
# ---------------------------------------------------------------------------
# motor-ai-client, motor-data-producer, motor-recorder, motor-diag-service,
# fault-tester and spi-loopback are qnx-guest-rootfs's QNX_ROOTFS_INSTALL now,
# so they ride on rootfs.img -- the writable virtio-blk disk this guest
# union-mounts at / -- rather than in this RAM-resident, read-only IFS.
#
# So is their CONFIGURATION, and so is qnx-guest-conf, which is why that is gone
# from the list above too: its three Screen configurations and
# graphics-virtio-start.sh are read by start-guest1.sh, which runs well below
# .rootfs-mount.sh. A binary that can be replaced with an scp is not much use if
# the file that decides how it starts still costs a reflash.
#
# The reason is the one that moved hms off the host's IFS: an application here
# costs a full image rebuild and a reflash of the card to change one binary,
# which is the price of fixing a driver paid for code that changes every day.
# On rootfs.img it is an scp into the running guest.
#
# The mount is a union, so nothing about the paths changes: /bin, /usr/bin and
# /etc on the disk merge with the IFS's, and start-guest1.sh still launches
# `motor_data_producer /etc/motor/config.json`, `motor_recorder -d /record` and
# /Motor_AI_Client/motor_ai_client by the names it always used.
#
# The line is boot order, and it is drawn at /proc/boot/.rootfs-mount.sh in the
# startup script. What stays here runs before that line or is needed to reach
# it: rpi-gpio (via packagegroup-qnx-hyp-common) and the two BSPs come up with
# SPI and GPIO several lines earlier, and the SDP components carry the boot
# itself. start-guest1.sh runs long after the mount, which is what makes moving
# everything it launches safe.
#
# The libraries stay too, and deliberately -- they are not what is being
# iterated on. mosquitto is ~100KB and motor_recorder links libmosquitto.so.1;
# an image with the binary and not the library gets a process that dies at
# startup with ELIBACC, naming nothing useful. packagegroup-qnx-someip is the
# same story at ~9.5MB for motor_ai_client and motor_diag_service. Both resolve
# identically whichever filesystem the binary loading them came from.

# motor-diag-service is what the AAOS head unit talks to: it publishes a 1 Hz
# fault classification and answers capture requests over SOME/IP, reading the
# producer's ring and the AI pipeline's verdicts. It needs the guest to be on
# the head unit's wire, which is what QNX_GUEST_LAN_IP above provides.
#
# fault-tester is a bench tool, not part of the pipeline: it injects a chosen
# verdict into /motor_fault_override so the head unit's fault card and severity
# ring can be exercised without a real defect. It is installed but never
# started -- it latches a fault until released, and one that appeared at every
# boot would eventually be mistaken for a real one. Run `fault_tester` from the
# console or over ssh.
#
# motor-recorder is the third consumer of the motor shared-memory ring, after
# motor-ai-client and shm_chunker: it writes rows to CSV and publishes them over
# MQTT. mosquitto comes with it for the same reason it comes with hms on the
# host -- the binary links libmosquitto.so.1, and an image with one and not the
# other gets a process that dies at startup with ELIBACC.
#
# The two are split across the two filesystems now, which is the split this
# image draws everywhere: the binary is on rootfs.img because it is iterated on,
# the library stays in the IFS because it is not. libmosquitto is ~100KB, which
# an IFS carries without complaint, and it is staged into the processor tree's
# lib/ -- which the rootfs template does not map, so leaving it here is also the
# only place it currently lands.
#
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
#   (QT_QUICK_BACKEND)          deliberately NOT set. It used to be "software",
#                               because qtbase was built no-opengl and Qt
#                               Quick's default RHI path had no backend to run
#                               on. qtbase is now built with OpenGL ES 2 (see
#                               this layer's qtbase bbappend) and the guest has
#                               a real driver behind it in qnx-screen-virtio,
#                               so the RHI path works and the default is right.
#
#                               Do not put it back to force a fallback. The
#                               software backend has no shader support, so every
#                               MultiEffect and layer.enabled item draws NOTHING
#                               and prints no warning -- and the cluster's
#                               bezel, ring glow, status bands and telltales are
#                               all built from them. The failure looks like a
#                               plainer UI, not like a fault.
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
QNX_IFS_ENV += "QQNX_PHYSICAL_SCREEN_SIZE=150,90"

# Which machine this shell is on -- see the same setting in qnx-host-image.
QNX_IFS_PROMPT = "(G1)# "

# The vdev addresses this image and the host's copy of the .qvmconf both have to
# agree on -- the console, the data disk, the GPU and the scanout size. Required
# by the qnx-host-data bbappend as well, which is the point: one file, both
# consumers, no way for them to disagree.
require conf/qnx-guest-vdevs.inc

# The other end of the host's vp0 interface. The host's QNX_HOST_GUEST_IP is
# this guest's gateway, and its QNX_HOST_GUEST_NET has to contain this address.
QNX_GUEST_IP ?= "10.0.0.2"
QNX_GUEST_GATEWAY ?= "10.0.0.1"

# The guest's address on the LAN the host bridges it onto -- the one the AAOS
# head unit reaches it at, and the one motor_diag_service offers over SOME/IP.
# Set in conf/qnx-guest-vdevs.inc, which this recipe requires above -- so a
# `?=` here is dead and the value there is the one that applies. There were two
# of these, saying .50 and .3, and only .3 ever reached an image.

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

# Name servers, written to this guest's /etc/resolv.conf. Without them the guest
# routes to the outside world through the host's NAT and resolves nothing, which
# looks like a routing failure and is not one.
#
# Public resolvers by default rather than the LAN gateway: the host's own uplink
# may be the wifi, on a subnet the LAN gateway's address is not on. Space
# separated, one `nameserver` line each.
QNX_GUEST_DNS ?= "8.8.8.8 8.8.4.4"

# What sshd runs at inside this guest, rather than procnto's default 10.
#
# The guest's own applications sit at 10: the Qt cluster rendering in software,
# the motor producer (pinned to CPU 0), the recorder, the AI client. An ssh key
# exchange is CPU, so at equal priority a handshake waits behind all of them --
# which is why ssh from the host is slow to give a prompt and why hms, paying a
# handshake per Monitor poll, sees the guest time out while it is healthy.
#
# 15: above the workload, below io-sock at 21, so raising it cannot starve the
# guest's own networking.
QNX_GUEST_SSHD_PRIORITY ?= "15"

QNX_GUEST_RESOLV = "${@chr(10).join('nameserver %s' % s for s in (d.getVar('QNX_GUEST_DNS') or '').split())}"

# The template is tracked by its own checksum, but the values substituted into
# it are not -- so without this, changing an address in local.conf would leave
# the IFS untouched and the running guest on the old one.
do_generate_buildfile[vardeps] += "QNX_GUEST_IP QNX_GUEST_GATEWAY QNX_GUEST_PEER_IP QNX_GUEST_LAN_IP \
                                   QNX_GUEST_DNS QNX_GUEST_RESOLV \
                                   QNX_GUEST_SSHD_PRIORITY"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# ---------------------------------------------------------------------------
# Let hms in
# ---------------------------------------------------------------------------
# hms runs on the host and manages this guest over ssh -- starting it, stopping
# it, reading its state. That is key-based, so its public key has to be
# authorised here or every one of those connections asks for a password nobody
# is there to type.
#
# The key is a literal in meta-qnx-hyp's conf fragment rather than something
# fetched from the hms recipe: a public key is not a secret, and stating it
# means this image authorises hms without depending on hms.
require conf/hms-ssh-key.inc
QNX_SSH_AUTHORIZED_KEYS += "${QNX_HMS_PUBKEY}"

# UsePAM and StrictModes are off for this guest -- see the long comments on
# QNX_SSH_USE_PAM/QNX_SSH_STRICT_MODES in qnx-ssh_1.0.bb for the failures this
# works around (sshd closing every connection the instant a credential was
# submitted, then -- with PAM off -- refusing a verified key outright because
# / and /var are group-writable, which this guest's cluster demo needs).
#
# The actual override lives in
# recipes-sdp/components/qnx-ssh_1.0.bbappend, NOT here: qnx-ssh is one
# recipe shared by every image, and its ${QNX_SSH_USE_PAM}/
# ${QNX_SSH_STRICT_MODES} expansions resolve against ITS OWN datastore, not
# this image recipe's. A plain assignment here (which this file carried for a
# while) lands in qnx-guest-image's datastore and never reaches qnx-ssh's --
# silently: no error, no warning, just a guest that keeps shipping UsePAM
# yes/StrictModes yes regardless of what this file says. Confirmed against a
# real build: a board flashed from an image built with only this assignment
# (no bbappend) still refused every login exactly as before. The .bbappend is
# the actual fix; this comment is kept here because the reasoning for turning
# both off is specific to this guest, even though the mechanism is not.

# The second key the reference authorises on this guest and on no other. See
# the fragment for what is known about it, which is not much -- it is carried to
# match, not because anything here needs it.
QNX_SSH_AUTHORIZED_KEYS += "${QNX_HOST_ROOT_PUBKEY}"
