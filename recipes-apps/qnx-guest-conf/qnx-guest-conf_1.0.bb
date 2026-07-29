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
# staged into a private directory and placed explicitly below. The automatic
# pass would warn about every one of them.
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

# Absolute sources: these have no bare-name search path to be found on.
#
# @QNX_IFS_ROOT@ (not ${...}) is deliberate -- it is expanded by the *image*
# recipe when the fragment is merged, since the path depends on which image
# installs this and is unknowable here.
#
# Screen reads its configuration from /usr/share/screen, and
# graphics-virtio-start.sh names graphics-virtio-mmio.conf there by absolute
# path, so these destinations are not free choices.
QNX_IFS_EXTRA_ENTRIES = "\
/usr/share/screen/graphics-virtio-mmio.conf=@QNX_IFS_ROOT@/guest-conf/graphics-virtio-mmio.conf\n\
/usr/share/screen/graphics-virtual-display.conf=@QNX_IFS_ROOT@/guest-conf/graphics-virtual-display.conf\n\
/usr/share/screen/graphics-headless.conf=@QNX_IFS_ROOT@/guest-conf/graphics-headless.conf\n\
[perms=0755] /scripts/graphics-virtio-start.sh=@QNX_IFS_ROOT@/guest-conf/graphics-virtio-start.sh\
"
