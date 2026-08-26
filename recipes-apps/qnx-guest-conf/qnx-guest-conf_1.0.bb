SUMMARY = "Guest Screen display configuration and its startup script"
DESCRIPTION = "The guest side of the display: the Screen configurations for the \
paravirtual GPU, and the script that brings up drm-virtio and Screen on top of \
it. Configuration rather than code, so it is staged verbatim."
LICENSE = "CLOSED"

inherit qnx-sdp qnx-src

# The same repository qnx-host-conf fetches, because the two sides of the
# display are described together: the guest's video-mode has to equal the host's
# display-1 video-mode and the virtio-gpu's scanout size, and keeping the files
# in one place is what makes that reviewable.
#
# What is NOT shared is the recipe. qnx-host-conf also stages the board's wifi
# configuration -- including wpa_supplicant.conf, which holds the network PSK --
# and a guest image that installed that component got the PSK baked into it, on
# a guest with no radio and no use for it. A component is all-or-nothing by
# design, so the answer is a second component that carries only the guest's
# files rather than a flag on the first.
#
# QNX_SRC_REV defaults to ${AUTOREV}, which needs the network at *parse* time.
# Pin it for reproducible and offline builds:
#
#     QNX_SRC_REV = "<commit sha>"
QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/qnx-host-conf.git;protocol=ssh;branch=main"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# /usr/share/screen and /scripts are not on any mkifs search path, so these are
# staged into a private directory and placed explicitly by whatever carries
# them. The automatic pass would warn about every one of them.
QNX_IFS_AUTO_ENTRIES = "0"

QNX_GUEST_CONF_DIR = "${QNX_STAGE_DIR}/guest-conf"

do_install() {
	install -d ${D}${QNX_GUEST_CONF_DIR}

	# All three configurations, because which one is used is a runtime choice:
	# virtio-mmio drives the paravirtual GPU, virtual-display renders offscreen
	# for a guest with no scanout, headless is for running Screen clients with
	# no display at all.
	install -m 0644 ${S}/display/graphics-virtio-mmio.conf     ${D}${QNX_GUEST_CONF_DIR}/
	install -m 0644 ${S}/display/graphics-virtual-display.conf ${D}${QNX_GUEST_CONF_DIR}/
	install -m 0644 ${S}/display/graphics-headless.conf        ${D}${QNX_GUEST_CONF_DIR}/
	install -m 0755 ${S}/display/graphics-virtio-start.sh      ${D}${QNX_GUEST_CONF_DIR}/
}

# No QNX_IFS_EXTRA_ENTRIES. All four files are on rootfs.img, placed by
# qnx-guest-rootfs.build.in -- this recipe is in that image's
# QNX_ROOTFS_INSTALL, not in qnx-guest-image's QNX_IFS_INSTALL.
#
# Nothing here is needed before the disk is mounted. start-guest1.sh runs
# /scripts/graphics-virtio-start.sh, and that is several lines below
# /proc/boot/.rootfs-mount.sh in the boot script; drm-virtio, which the script
# execs, is on the same disk already (qnx-screen-virtio, ~279MB of it).
#
# Putting them there is what makes the display tunable on a running guest. Which
# of the three Screen configurations applies, and the retry logic in the startup
# script, are exactly the things someone changes with a panel in front of them
# -- and in a read-only IFS every one of those changes was an image rebuild and
# a reflash of the SD card.
#
# Screen reads its configuration from /usr/share/screen, and
# graphics-virtio-start.sh names graphics-virtio-mmio.conf there by absolute
# path, so those destinations are not free choices -- see the records in the
# rootfs template.
