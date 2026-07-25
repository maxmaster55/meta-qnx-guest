SUMMARY = "Qt Quick instrument cluster for the QNX guest"
DESCRIPTION = "The qt_cluster QML application. Builds against stock meta-qt6's \
Qt, cross-compiled for QNX by qnx-toolchain, and its post-build step assembles \
a self-contained deploy tree (appCluster, run.sh, the Qt libraries, QML modules, \
plugins and fonts it actually uses) -- which is exactly the payload the guest's \
data filesystem wants."
LICENSE = "CLOSED"

inherit qnx-cmake qnx-src

# NO STANDALONE REPOSITORY YET (same situation as frame-router): lives in the
# hypervisor monorepo, built from the working tree, no sstate.
QNX_SRC_LOCAL = "${QNX_PROJECT_SRC}"
QNX_SRC_SUBDIR = "src/qt_cluster"

# cmake builds out of tree, so nothing is written into the checkout.
EXTERNALSRC_BUILD = "${WORKDIR}/build"

# Stock meta-qt6, not the old hand-rolled qt6-qnx. qtbase brings QtCore/Gui and
# the QNX platform plugin (libqqnx.so); qtdeclarative brings Qml/Quick, which is
# what `find_package(Qt6 COMPONENTS Core Gui Quick)` in the app asks for.
#
# The -native halves are the host tools, which must run on the build host.
# meta-qt6's own qt6-cmake.bbclass takes the qtbase-native dependency and points
# QT_HOST_PATH at it; this recipe inherits qnx-cmake instead (it needs the QNX
# toolchain file), so it does the same wiring by hand.
#
# qtdeclarative-native is needed on top of that, and the reason is not obvious:
# qtbase-native supplies Qt6CoreTools and Qt6GuiTools (moc, rcc, uic), but
# Qt6QmlDependencies.cmake requires Qt6QmlTools and Qt6QuickDependencies.cmake
# requires Qt6QuickTools -- qmlimportscanner, qmlcachegen, qmltyperegistrar --
# and those ship only with qtdeclarative-native. Without it the *target*
# find_package(Qt6 COMPONENTS Quick) is what fails, reporting that Qt6Quick's
# config file exists but the component was not found, which points nowhere near
# the host side.
DEPENDS = "qtbase qtdeclarative qtbase-native qtdeclarative-native"

# Where meta-qt6 stages Qt. Deliberately spelled out rather than derived: the
# app's CMakeLists computes its deploy paths as ${Qt6_DIR}/../../.. plus
# /qml and /plugins, which is right for a Qt installed under its own prefix
# (what qt6-qnx produced) and wrong for meta-qt6, whose qt6-paths.bbclass puts
# QML at ${libdir}/qml and plugins at ${libdir}/plugins. Those are all CACHE
# PATH variables, so overriding them here is the supported way to correct it --
# and getting it wrong does not fail the configure, it silently deploys no QML
# and leaves the app unable to start.
QT6_CMAKE_DIR = "${RECIPE_SYSROOT}${libdir}/cmake/Qt6"

OECMAKE_EXTRA_ARGS = "\
    -DQt6_DIR=${QT6_CMAKE_DIR} \
    -DQT6_LIB_DIR=${RECIPE_SYSROOT}${libdir} \
    -DQT6_QML_DIR=${RECIPE_SYSROOT}${libdir}/qml \
    -DQT6_PLUGIN_DIR=${RECIPE_SYSROOT}${libdir}/plugins \
    -DQT_HOST_PATH=${RECIPE_SYSROOT_NATIVE}${prefix_native}/ \
    -DQNX_LIB_DIR=${QNX_TARGET}/${QNX_PROCESSOR} \
    -DFONT_SOURCE_DIR=/usr/share/fonts \
"

# These embed RECIPE_SYSROOT / RECIPE_SYSROOT_NATIVE, absolute per-recipe paths,
# for the same reason qnx-sdp excludes its sysroot flags from signatures.
OECMAKE_EXTRA_ARGS[vardepsexclude] = "QT6_CMAKE_DIR"

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
	# The deploy tree is useless without the QML modules; an empty qml/ means
	# QT6_QML_DIR pointed somewhere wrong and the app would fail at startup on
	# the board rather than here.
	if [ ! -d ${B}/deploy/qml ] || [ -z "$(ls -A ${B}/deploy/qml 2>/dev/null)" ]; then
		bbfatal "${B}/deploy/qml is empty -- QT6_QML_DIR did not match where meta-qt6 staged the QML modules"
	fi
	install -d ${D}${QNX_STAGE_DIR}/qt-cluster
	cp -a ${B}/deploy/. ${D}${QNX_STAGE_DIR}/qt-cluster/
	chmod 0755 ${D}${QNX_STAGE_DIR}/qt-cluster/run.sh
}

QNX_IFS_AUTO_ENTRIES = "0"
