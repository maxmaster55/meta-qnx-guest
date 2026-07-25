SUMMARY = "Qt Quick instrument cluster for the QNX guest"
DESCRIPTION = "The qt_cluster QML application. Builds against the Qt-for-QNX \
tree staged by qt6-qnx, and its post-build step assembles a self-contained \
deploy tree (appCluster, run.sh, the Qt libraries, QML modules, plugins and \
fonts it actually uses) -- which is exactly the payload the guest's data \
filesystem wants."
LICENSE = "CLOSED"

inherit qnx-cmake qnx-src

# NO STANDALONE REPOSITORY YET (same situation as frame-router): lives in the
# hypervisor monorepo, built from the working tree, no sstate.
QNX_SRC_LOCAL = "${QNX_PROJECT_SRC}"
QNX_SRC_SUBDIR = "src/qt_cluster"

# cmake builds out of tree, so nothing is written into the checkout.
EXTERNALSRC_BUILD = "${WORKDIR}/build"

DEPENDS = "qt6-qnx"

QT6 = "${RECIPE_SYSROOT}${QNX_STAGE_DIR}/qt"

# The project's own Makefile drives this configure with Qt's generated
# toolchain file; the recipe does the same, with two twists:
#
#  - everything is taken from the qt6-qnx sysroot rather than the monorepo's
#    output_dir, so the app builds against exactly what its DEPENDS staged
#  - qt.toolchain.cmake's baked-in chainload path points into the monorepo, so
#    the chainload is redirected to the copy qt6-qnx staged next to it
#
# Passing -DCMAKE_TOOLCHAIN_FILE here overrides the one qnx-cmake generates
# (last -D wins); Qt insists on its own toolchain file to locate host tools and
# set the mkspec, and the chainloaded file supplies the qcc/QNX side.
#
# FONT_SOURCE_DIR mirrors the Makefile's qnx target: fonts are harvested from
# the build host, and deploy_qt.cmake just warns if none are found.
OECMAKE_EXTRA_ARGS = "\
    -DCMAKE_TOOLCHAIN_FILE=${QT6}/lib/cmake/Qt6/qt.toolchain.cmake \
    -DQT_CHAINLOAD_TOOLCHAIN_FILE=${QT6}/lib/cmake/toolchain_qnx_aarch64le.cmake \
    -DQt6_DIR=${QT6}/lib/cmake/Qt6 \
    -DQT_HOST_PATH=${QT6}/host_qt \
    -DQNX_LIB_DIR=${QNX_TARGET}/${QNX_PROCESSOR} \
    -DFONT_SOURCE_DIR=/usr/share/fonts \
"

# ${QT6} contains RECIPE_SYSROOT, an absolute per-recipe path; keep it out of
# the configure signature for the same reason qnx-sdp excludes its sysroot
# flags.
OECMAKE_EXTRA_ARGS[vardepsexclude] = "QT6"

# The POST_BUILD deploy step (deploy_qt.cmake) runs as part of do_compile and
# leaves ${B}/deploy as a relocatable directory: run.sh sets LD_LIBRARY_PATH,
# QML2_IMPORT_PATH and the QNX platform-plugin path relative to itself.
#
# That directory is staged whole. Like the Qt runtime it belongs on the guest's
# data filesystem, not in a RAM-resident IFS -- run it from wherever it lands
# with ./run.sh -- so no IFS entries are derived from it.
do_install() {
	if [ ! -x ${B}/deploy/appCluster ]; then
		bbfatal "no ${B}/deploy/appCluster -- the post-build deploy step did not run"
	fi
	install -d ${D}${QNX_STAGE_DIR}/qt-cluster
	cp -a ${B}/deploy/. ${D}${QNX_STAGE_DIR}/qt-cluster/
	chmod 0755 ${D}${QNX_STAGE_DIR}/qt-cluster/run.sh
}

QNX_IFS_AUTO_ENTRIES = "0"
