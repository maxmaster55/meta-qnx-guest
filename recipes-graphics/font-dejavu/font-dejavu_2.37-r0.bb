SUMMARY = "DejaVu fonts, for anything on this guest that draws text"
DESCRIPTION = "The DejaVu family, from QNX's own OSS repository. Qt on this \
guest is built without fontconfig -- libQt6Gui links freetype and nothing else \
-- so its font database is the basic one: a single flat directory of font \
files, and no font server, no cache and no aliasing to fall back on. This \
recipe is that directory."
HOMEPAGE = "https://dejavu-fonts.github.io/"
LICENSE = "BitstreamVera"

inherit qnx-apk

# 8.0.3/extra, not the class default 8.0.4/qnx-extra. The 8.0.4 channels carry
# 108 packages and no fonts at all; 8.0.3/extra carries 2023, fonts among them.
# Mixing channels across point releases is safe for exactly this kind of package
# and no other: a .ttf is data, with no libc, no ABI and nothing to link
# against. Find what else is there with:
#
#     bitbake -c search_oss qnx-sdp        (QNX_OSS_SEARCH = "font")
QNX_OSS_CHANNEL = "8.0.3/extra"
SRC_URI[sha256sum] = "c4f28f4281dace9f45924687ca3e5562dfb9bbcfcd4b182895f11f55ed8a8156"

# The class default gates every OSS package behind the non-commercial QNX
# licence flag, because most of them are QDL. This one is not: DejaVu is under
# the Bitstream Vera licence, which is permissive, so there is nothing to gate.
LICENSE_FLAGS = ""

# The apk lays its fonts out as usr/share/fonts/dejavu/*.ttf, with fontconfig
# .conf fragments in etc/. Neither is where they are wanted:
#
#   the subdirectory -- Qt's basic font database lists exactly one directory and
#   does not descend into it, so fonts one level down are simply not found. They
#   are flattened here rather than pointed at, because the alternative is a
#   QT_QPA_FONTDIR per font package.
#
#   /usr/lib/fonts -- Qt's compiled-in default (LibrariesPath + "/fonts"), and
#   the path the failure names when it is empty. Landing there means a Qt
#   application needs no font configuration at all; the guest image sets
#   QT_QPA_FONTDIR to the same value anyway, so the agreement is visible.
#
# The etc/ fragments are dropped: they configure fontconfig, which no Qt on this
# guest is built against.
do_install() {
	install -d ${D}${QNX_STAGE_USRLIBDIR}/fonts
	found=0
	for f in ${S}/usr/share/fonts/dejavu/*.ttf; do
		[ -f "$f" ] || continue
		install -m 0644 "$f" ${D}${QNX_STAGE_USRLIBDIR}/fonts/
		found=1
	done
	if [ "$found" = "0" ]; then
		bbfatal "font-dejavu apk carried no .ttf under usr/share/fonts/dejavu -- has its layout changed?"
	fi
}

# Fonts are ~5.4MB and belong on the data disk, not in a RAM-resident IFS: this
# recipe is for QNX_ROOTFS_INSTALL. The automatic IFS-entry pass is turned off so
# that staying out of QNX_IFS_INSTALL is the only thing needed to keep them out.
QNX_IFS_AUTO_ENTRIES = "0"
