SUMMARY = "CommonAPI/SOME/IP motor data service"
DESCRIPTION = "Serves motor telemetry over SOME/IP. Its CMakeLists runs the \
CommonAPI generators at configure time to turn the .fidl/.fdepl interface \
definitions into C++ bindings, then builds them alongside the service."
LICENSE = "CLOSED"

inherit qnx-cmake qnx-src

QNX_SRC_REPO = "git://git@github.com/PM-Maestro-ITI-GP-Org/motor_ai_server.git;protocol=ssh;branch=main"

# The runtimes it links against, and the generators it runs. The generators are
# native: they are x86_64 host tools that produce source, not target binaries.
DEPENDS = "commonapi-someip commonapi-core vsomeip boost commonapi-generators-native"

# The QNX byte-order and narrowing settings the whole SOME/IP stack needs; the
# generated CommonAPI code pulls in the same headers the runtimes do.
require recipes-someip/someip-qnx-flags.inc

# The service lives in server/, but its CMakeLists reaches ../interface for the
# .fidl definitions, so the repository root has to be the source directory.
OECMAKE_SOURCEPATH = "${S}/server"

# Upstream looks for its dependencies under one output directory: lib/ for the
# libraries and generators/{core,someip}/ for the code generators. Neither
# exists here -- the libraries are in the sysroot and the generators are native
# tools on PATH -- so a directory of that shape is assembled from both.
QNX_SOMEIP_SHIM = "${WORKDIR}/someip-shim"

export LIBS_DIR = "${QNX_SOMEIP_SHIM}"
export OUTPUT_DIR = "${QNX_SOMEIP_SHIM}"

do_configure:prepend() {
	install -d ${QNX_SOMEIP_SHIM}/generators
	ln -sfn ${RECIPE_SYSROOT}${QNX_STAGE_LIBDIR} ${QNX_SOMEIP_SHIM}/lib
	ln -sfn ${STAGING_DATADIR_NATIVE}/commonapi-generators/core   ${QNX_SOMEIP_SHIM}/generators/core
	ln -sfn ${STAGING_DATADIR_NATIVE}/commonapi-generators/someip ${QNX_SOMEIP_SHIM}/generators/someip
}

# These land in /Motor_AI_Server and /etc, neither of which mkifs searches by
# bare name, so the automatic pass is turned off and the records are written
# out. The destinations match the project's own guest build files.
QNX_IFS_AUTO_ENTRIES = "0"

QNX_MOTOR_AI_DIR = "${QNX_STAGE_DIR}/motor-ai-server"

do_install() {
	install -d ${D}${QNX_MOTOR_AI_DIR}
	install -m 0755 ${B}/MotorDataService ${D}${QNX_MOTOR_AI_DIR}/motor_ai_server
	install -m 0644 ${S}/server/vsomeip_multicast.json ${D}${QNX_MOTOR_AI_DIR}/vsomeip.json
	install -m 0644 ${S}/interface/commonapi4someip.ini ${D}${QNX_MOTOR_AI_DIR}/commonapi.ini

	# window_rows, data_dir, ai_pid_file and the three signals. The service
	# looks for it at /etc/motor-ai-server/server.conf with no environment
	# variable set, which is where the IFS record below puts it.
	install -m 0644 ${S}/server/server.conf ${D}${QNX_MOTOR_AI_DIR}/server.conf
}

# @QNX_IFS_ROOT@ is expanded by the image recipe, since the path depends on
# which image installs this.
#
# /etc/commonapi.ini is +dupignore because the client and the server both ship
# it. In the project they live in different guests so it never collided; here
# they can share an image, and mkifs treats a redefined entry as an error. The
# file is the same CommonAPI binding configuration either way.
QNX_IFS_EXTRA_ENTRIES = "\
/Motor_AI_Server/motor_ai_server=@QNX_IFS_ROOT@/motor-ai-server/motor_ai_server\n\
/Motor_AI_Server/vsomeip.json=@QNX_IFS_ROOT@/motor-ai-server/vsomeip.json\n\
[+dupignore] /etc/motor-ai-server/server.conf=@QNX_IFS_ROOT@/motor-ai-server/server.conf\n\
[+dupignore] /etc/commonapi.ini=@QNX_IFS_ROOT@/motor-ai-server/commonapi.ini\
"

# Started by hand, as in the project's own guest images.

# What this no longer gives you on its own.
#
# The service used to spawn an inference command per window and could answer a
# client by itself. It now writes the window to <data_dir>/input_data/data.csv
# and signals a long-running motor_ai_node for each of the three stages, and
# this layer does not build that node -- it is packaged by meta-bmo for the
# Linux guest, where the models are.
#
# So installing this recipe to put both halves of the SOME/IP pair in one QNX
# image still does what it was kept for: it exercises the transport, the
# generated bindings and the whole client path. It just answers every window
# with "unknown", after waiting out result_timeout_ms per stage. Set
#
#     result_timeout_ms = 100
#
# in server.conf when using it that way, so a missing node costs a tenth of a
# second per window instead of thirty seconds.
#
# For the node as well, upstream's ai_node/ builds with a plain
# `make ai_node` against any C++14 compiler and links nothing, so a QNX build
# of it is a qcc invocation rather than a port.
