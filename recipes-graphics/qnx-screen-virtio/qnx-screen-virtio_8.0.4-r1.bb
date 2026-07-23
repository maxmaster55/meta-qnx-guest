SUMMARY = "QNX Screen virtio graphics driver stack"
DESCRIPTION = "The guest side of virtio-gpu: EGL/GLES, the virtio DRI driver, \
the WFD implementation and the gfxstream Vulkan ICD. A guest renders through \
these, and vdev-virtio-gpu on the host turns the command stream into real GPU \
work via virglrenderer."
HOMEPAGE = "https://qnx.com"
LICENSE = "LicenseRef-QDL-Non-Commercial"

inherit qnx-apk

# From repo.oss.qnx.com/8.0.4/qnx-extra (the class default channel). The two
# per-package facts: the .apk's checksum, and the .PKGINFO's, which the class
# turns into the licence-file reference.
SRC_URI[sha256sum] = "97938cf4fed1ca463c5dd50f533a0e4deeba110361666bfd35619a498326b6cc"
QNX_APK_PKGINFO_MD5 = "bddf9e02a65da5a0c396a312f972e8f5"

# NOT for an IFS. This unpacks to ~279MB -- virtio_dri.so alone is 67MB, which
# the project's own rootfs.build calls one of the two largest files in the whole
# guest. An IFS is RAM-resident, so these belong on the guest's QNX6 data
# partition, and this recipe is deliberately not in any image's QNX_IFS_INSTALL
# until that exists.
