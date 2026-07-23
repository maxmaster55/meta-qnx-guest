SUMMARY = "QNX Screen virtio graphics driver stack"
DESCRIPTION = "The guest side of virtio-gpu: EGL/GLES, the virtio DRI driver, \
the WFD implementation and the gfxstream Vulkan ICD. A guest renders through \
these, and vdev-virtio-gpu on the host turns the command stream into real GPU \
work via virglrenderer."
HOMEPAGE = "https://qnx.com"
LICENSE = "LicenseRef-QDL-Non-Commercial"

inherit qnx-apk

# From repo.oss.qnx.com/8.0.4/qnx-extra (the class default channel). The one
# per-package pin: the .apk's checksum. bitbake prints it on the first fetch --
# omit this line, run "bitbake -c fetch qnx-screen-virtio", paste what it shows.
SRC_URI[sha256sum] = "97938cf4fed1ca463c5dd50f533a0e4deeba110361666bfd35619a498326b6cc"

# NOT for an IFS. This unpacks to ~279MB -- virtio_dri.so alone is 67MB, which
# the project's own rootfs.build calls one of the two largest files in the whole
# guest. An IFS is RAM-resident, so these belong on the guest's QNX6 data
# partition, and this recipe is deliberately not in any image's QNX_IFS_INSTALL
# until that exists.
#
# It is bound for that data partition, which is a real filesystem rather than an
# mkifs image, so the automatic IFS-entry pass is turned off: it would otherwise
# warn about the usr/share config files, which mkifs cannot place by bare name
# but a filesystem has no trouble with.
QNX_IFS_AUTO_ENTRIES = "0"
