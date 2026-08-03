SUMMARY = "Eclipse Mosquitto MQTT client library, cross-built for QNX"
DESCRIPTION = "libmosquitto only -- the broker, the command-line clients, the \
plugins and the C++ wrapper are all turned off. What wants this is \
motor-recorder, which publishes telemetry to a broker somewhere else; nothing \
in these images is a broker."
HOMEPAGE = "https://mosquitto.org"

# Dual-licensed: EPL-2.0 or EDL-1.0, both text files at the repository root.
LICENSE = "EPL-2.0 | EDL-1.0"
LIC_FILES_CHKSUM = "file://epl-v20;md5=2dd765ca47a05140be15ebafddbeadfe \
                    file://edl-v10;md5=9f6accb1afcb570f8be65039e2fcd49e"

# The tag, not the branch. The project's own cross_compile_qnx.sh does
# `git clone --depth 1 https://github.com/eclipse/mosquitto.git` into a
# directory it names mosquitto-2.0.20 -- but a clone with no branch argument
# gets the default branch, so what it actually built was master.
#
# That distinction is worth more than pedantry, because master needs a port and
# this tag does not. On master, libcommon/CMakeLists.txt hard-fails when the C
# library has no getrandom():
#
#     C library does not provide getrandom(); enable WITH_TLS instead
#
# which is why that script carries ~100 lines of shell and python to write a
# sys/random.h, add a getrandom_qnx.c reading /dev/urandom, and rewrite the
# CMake check. None of it is needed here. In 2.0.20 there is no getrandom check
# at all: lib/util_mosq.c reaches for getrandom() only under #ifdef
# HAVE_GETRANDOM, and falls through to random() when neither that nor WITH_TLS
# is defined.
#
# The cost of that fallback is real but small and confined: util__random_bytes
# seeds MQTT message IDs, not key material, and with WITH_TLS off there is no
# key material here to seed. It is what upstream ships for any platform without
# getrandom.
SRC_URI = "git://github.com/eclipse/mosquitto.git;protocol=https;branch=master;tag=v${PV}"

inherit qnx-cmake

S = "${WORKDIR}/git"

# Everything except the client library. WITH_TLS is off because the SDP has no
# OpenSSL that this links against and the recorder publishes on the guest's
# private link to the host; turning it on is a real change, not a flag flip --
# it also switches util__random_bytes onto RAND_bytes, which is the reason the
# getrandom question above exists at all.
#
# Static libraries as well as shared: the shared one is what goes in the image,
# but a static link is the quickest way to test a change to the recorder on a
# board without reinstalling the library beside it.
#
# DOCUMENTATION, not WITH_DOCS -- the man pages are the one thing here that does
# not follow the WITH_ prefix, and the project's own cross-compile script passes
# the name that does nothing. The failure is not subtle once it happens, but it
# happens after the library has already built, which makes it look like a
# library problem:
#
#     xsltproc: not found
#     ninja: build stopped: subcommand failed.
OECMAKE_EXTRA_ARGS = "\
    -DWITH_BROKER=OFF \
    -DWITH_CLIENTS=OFF \
    -DWITH_APPS=OFF \
    -DWITH_PLUGINS=OFF \
    -DWITH_LIB_CPP=OFF \
    -DWITH_TLS=OFF \
    -DWITH_TLS_PSK=OFF \
    -DWITH_WEBSOCKETS=OFF \
    -DWITH_SOCKS=OFF \
    -DWITH_SRV=OFF \
    -DDOCUMENTATION=OFF \
    -DWITH_STATIC_LIBRARIES=ON \
    -DWITH_SHARED_LIBRARIES=ON \
    -DCMAKE_INSTALL_LIBDIR=${QNX_PROCESSOR}/lib \
    -DCMAKE_INSTALL_INCLUDEDIR=usr/include \
"

# CMAKE_INSTALL_LIBDIR above is relative to OECMAKE_INSTALL_PREFIX, which
# qnx-cmake sets to ${QNX_STAGE_DIR} -- so the library lands in the
# ${QNX_PROCESSOR}/ subtree that mkifs -r searches and that a dependent recipe's
# -L points at, and the headers land where QNX_SYSROOT_CPPFLAGS looks. Left at
# the defaults they would go to <prefix>/lib and nothing would find them.

# Nothing in an image needs the pkg-config or CMake package files, and the
# ${QNX_PROCESSOR}/lib tree is harvested for IFS entries -- so they would
# otherwise turn into records for files no image wants.
do_install:append() {
	rm -rf ${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/lib/pkgconfig
	rm -rf ${D}${QNX_STAGE_DIR}/${QNX_PROCESSOR}/lib/cmake
}
