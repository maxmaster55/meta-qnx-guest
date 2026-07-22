SUMMARY = "QNX Screen virtio graphics driver stack"
DESCRIPTION = "The guest side of virtio-gpu: EGL/GLES, the virtio DRI driver, \
the WFD implementation and the gfxstream Vulkan ICD. A guest renders through \
these, and vdev-virtio-gpu on the host turns the command stream into real GPU \
work via virglrenderer."
HOMEPAGE = "https://qnx.com"

# From the package's own .PKGINFO. Non-commercial: worth knowing before this
# ships in anything.
LICENSE = "LicenseRef-QDL-Non-Commercial"
# The package carries no licence file. Its .PKGINFO is the only place the
# licence is stated, so that is what is checksummed -- it is a real file in the
# archive rather than something invented, and a change to it is exactly the
# change worth noticing.
LIC_FILES_CHKSUM = "file://.PKGINFO;md5=bddf9e02a65da5a0c396a312f972e8f5"
LICENSE_FLAGS = "qnx-non-commercial"

inherit qnx-sdp

# QNX publishes its open-source packages as .apk files. They are xz-compressed,
# but an apk is several concatenated streams -- signature, control, data -- and
# only `tar -xf` walks them; bitbake's "xz -dc | tar" pipeline fails on the first
# segment with "This does not look like a tar archive". So the fetcher is told
# not to unpack, and do_unpack finishes the job the way the project's own
# download.mk does.
QNX_OSS_REPO ?= "https://repo.oss.qnx.com"
QNX_OSS_CHANNEL ?= "8.0.4/qnx-extra"
QNX_OSS_ARCH ?= "aarch64"

SRC_URI = "${QNX_OSS_REPO}/${QNX_OSS_CHANNEL}/${QNX_OSS_ARCH}/${BPN}-${PV}.apk;unpack=0"
SRC_URI[sha256sum] = "97938cf4fed1ca463c5dd50f533a0e4deeba110361666bfd35619a498326b6cc"

S = "${WORKDIR}/${BPN}-${PV}"

# Its own task rather than an append to do_unpack: that one is a python task in
# bitbake, and a shell body cannot be attached to it.
do_extract_apk() {
	# The APK-TOOLS.checksum.SHA1 warnings tar prints are expected: they are
	# apk's own per-file checksums carried as pax extended headers, which tar
	# does not know and does not need.
	install -d ${S}
	tar -xf ${WORKDIR}/${BPN}-${PV}.apk -C ${S}
}
# Before do_patch, not merely before do_configure: do_populate_lic runs after
# do_patch and needs .PKGINFO to already be on disk.
addtask extract_apk after do_unpack before do_patch

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# NOT for an IFS. This unpacks to ~279MB -- virtio_dri.so alone is 67MB, which
# the project's own rootfs.build calls one of the two largest files in the whole
# guest. An IFS is RAM-resident, so these belong on the guest's QNX6 data
# partition, and this recipe is deliberately not in any image's QNX_IFS_INSTALL
# until that exists.
#
# Staged into usr/lib, matching where the project's guest rootfs puts them.
do_install() {
	install -d ${D}${QNX_STAGE_USRLIBDIR}
	cp -Pf ${S}/usr/lib/*.so ${D}${QNX_STAGE_USRLIBDIR}/
	cp -f  ${S}/usr/lib/*.json ${D}${QNX_STAGE_USRLIBDIR}/
}
