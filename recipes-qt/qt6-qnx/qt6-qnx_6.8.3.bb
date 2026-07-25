SUMMARY = "Qt 6 for QNX aarch64le, built from upstream source"
DESCRIPTION = "Cross-compiles Qt 6 for QNX 8.0 the way the QNX hypervisor \
project's src/QT/qt6-qnx-libs scripts do -- a host Qt first for the tools \
(moc/rcc/qmlcachegen), then the target Qt with qcc against $QNX_TARGET -- but \
self-contained: it fetches the Qt source from upstream and carries its own QNX \
cmake toolchain file, with no dependency on the monorepo working tree. Stages a \
complete Qt SDK (target runtime + host_qt tools) at ${QNX_STAGE_DIR}/qt for \
qt-cluster to build against."
HOMEPAGE = "https://www.qt.io"
LICENSE = "LGPL-3.0-only | GPL-3.0-only"
LIC_FILES_CHKSUM = "\
    file://${COMMON_LICENSE_DIR}/LGPL-3.0-only;md5=bfccfe952269fff2b407dd11f2f3083b \
    file://${COMMON_LICENSE_DIR}/GPL-3.0-only;md5=c79ff39f19dfec6d293b95dea7b07891 \
"

# The single upstream "everywhere" tarball -- the whole Qt super-repo -- plus the
# vendored QNX toolchain file. This is what makes the recipe stand alone.
SRC_URI = "\
    https://download.qt.io/archive/qt/6.8/${PV}/single/qt-everywhere-src-${PV}.tar.xz \
    file://toolchain_qnx_aarch64le.cmake \
"
SRC_URI[sha256sum] = "cdd3a69967208276bb01af7ace7dba0ba53e679f886a4cbe624225c60fb73f2c"

inherit qnx-sdp

S = "${WORKDIR}/qt-everywhere-src-${PV}"
B = "${WORKDIR}/build"

# The module set the project selects (qtbase is always built). Every other qt*
# module found in the source is switched off, exactly as build.sh's skip flags do.
QT6_MODULES ?= "qtbase qtdeclarative qtimageformats qtmultimedia qtshadertools qtsvg"

QT6_TOOLCHAIN = "${WORKDIR}/toolchain_qnx_aarch64le.cmake"
QT6_OUTPUT = "${B}/output"
QT6_HOST = "${QT6_OUTPUT}/host_qt"

# host_qt holds x86-64 Linux tools, staged on purpose for QT_HOST_PATH; the Qt
# tree is hundreds of MB bound for the guest rootfs, not a RAM-resident IFS.
QNX_ELF_CHECK = "0"
QNX_IFS_AUTO_ENTRIES = "0"

# Qt is fetched and built from source; there is nothing to configure separately.
do_configure[noexec] = "1"

# Downloading the ~950MB tarball happens in do_fetch, but the build itself pulls
# nothing from the network.
do_compile[dirs] = "${B}"
do_compile() {
	# Skip flags: disable every qt* module present in the source that is not in
	# QT6_MODULES -- the same computation build.sh's build_skip_flags does.
	skip=""
	for d in ${S}/qt*/; do
		m=$(basename "$d")
		[ "$m" = "qtbase" ] && continue
		[ -f "$d/CMakeLists.txt" ] || continue
		case " ${QT6_MODULES} " in
			*" $m "*) : ;;
			*) skip="$skip -DBUILD_${m}=OFF" ;;
		esac
	done

	# When CMAKE_INSTALL_PREFIX is set Qt defers copying these cmake helpers to
	# install time, but qtdeclarative and friends need them in the build tree at
	# configure time. Pre-copying them is build.sh's pre_copy_helper_files.
	prime_helpers() {
		mkdir -p "$1/qtbase/lib/cmake/Qt6"
		cp ${S}/qtbase/cmake/QtTargetHelpers.cmake \
		   ${S}/qtbase/cmake/QtBuildHelpers.cmake "$1/qtbase/lib/cmake/Qt6/"
	}

	# --- Phase 1: host Qt (moc/rcc/qmlcachegen), built with the host gcc ---
	# Forced to gcc/g++ so it does not pick up qcc from qnx-sdp's environment:
	# these tools must run on the build host.
	prime_helpers ${B}/host
	cmake -S ${S} -B ${B}/host -G Ninja \
		-DCMAKE_C_COMPILER=gcc -DCMAKE_CXX_COMPILER=g++ \
		-DCMAKE_BUILD_TYPE=Release \
		-DCMAKE_INSTALL_PREFIX=${QT6_HOST} \
		-DQT_BUILD_EXAMPLES=OFF -DQT_BUILD_TESTS=OFF \
		-DINPUT_opengl=no -DINPUT_dbus=no \
		$skip
	cmake --build ${B}/host --parallel ${@oe.utils.cpu_count()}
	cmake --install ${B}/host

	# --- Phase 2: cross Qt for QNX, using the QNX toolchain file + host tools ---
	prime_helpers ${B}/qnx
	cmake -S ${S} -B ${B}/qnx -G Ninja \
		-DCMAKE_TOOLCHAIN_FILE=${QT6_TOOLCHAIN} \
		-DQT_HOST_PATH=${QT6_HOST} \
		-DCMAKE_STAGING_PREFIX=${QT6_OUTPUT} \
		-DCMAKE_INSTALL_PREFIX=/qt \
		-DCMAKE_BUILD_TYPE=Release \
		-DQT_BUILD_EXAMPLES=OFF -DQT_BUILD_TESTS=OFF \
		-DINPUT_opengl=no -DINPUT_dbus=no -DFEATURE_libresolv=OFF \
		$skip
	cmake --build ${B}/qnx --parallel ${@oe.utils.cpu_count()}
	cmake --install ${B}/qnx
}

# Stage the whole SDK under ${QNX_STAGE_DIR}/qt -- target runtime, host_qt tools
# and lib/cmake -- the same layout the previous recipe produced, so qt-cluster
# consumes it unchanged. The toolchain file is staged beside lib/cmake so a
# consumer can chain-load it from the sysroot.
do_install() {
	install -d ${D}${QNX_STAGE_DIR}/qt
	cp -a ${QT6_OUTPUT}/. ${D}${QNX_STAGE_DIR}/qt/
	install -m 0644 ${QT6_TOOLCHAIN} ${D}${QNX_STAGE_DIR}/qt/lib/cmake/
}
